import { ref } from "vue";
import { apiGet, apiSend, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type { JavaInstallJob, JavaRuntime, JavaRuntimeEntry } from "./types";

/** Java runtime discovery plus the built-in Temurin downloader used by Settings. */

const detected = ref<JavaRuntime[]>([]);
const managed = ref<JavaRuntime[]>([]);
const installable = ref<number[]>([]);
const configured = ref<JavaRuntimeEntry[]>([]);
const isLoading = ref(false);

/** Install progress keyed by Java major version. */
const installJobs = ref<Record<number, JavaInstallJob>>({});

const pollTimers = new Map<number, ReturnType<typeof setInterval>>();

const loadRuntimes = async () => {
  try {
    isLoading.value = true;
    const response = await apiGet<{
      success: boolean;
      detected: JavaRuntime[];
      managed: JavaRuntime[];
      installable: number[];
      configured: JavaRuntimeEntry[];
    }>("/api/java/runtimes");

    detected.value = response.detected;
    managed.value = response.managed;
    installable.value = response.installable;
    configured.value = response.configured;
  } catch (e) {
    error.value = errorMessage(e, "Could not read the Java runtimes");
  } finally {
    isLoading.value = false;
  }
};

/** True when a Java major version is already available without downloading it. */
const isAvailable = (majorVersion: number): boolean =>
  detected.value.some((runtime) => runtime.majorVersion === majorVersion) ||
  managed.value.some((runtime) => runtime.majorVersion === majorVersion);

const stopPolling = (majorVersion: number) => {
  const timer = pollTimers.get(majorVersion);
  if (timer !== undefined) {
    clearInterval(timer);
    pollTimers.delete(majorVersion);
  }
};

/**
 * Downloads a Temurin JDK. The request returns as soon as the download starts; progress is then
 * polled, because a JDK runs to a couple of hundred megabytes.
 */
const installRuntime = async (majorVersion: number) => {
  try {
    const response = await apiSend<{ success: boolean; job: JavaInstallJob }>(
      "POST",
      `/api/java/runtimes/${majorVersion}/install`,
    );
    installJobs.value = { ...installJobs.value, [majorVersion]: response.job };
    pollInstall(majorVersion);
  } catch (e) {
    error.value = errorMessage(e, t("settings.javaInstallFailed"));
  }
};

const pollInstall = (majorVersion: number) => {
  stopPolling(majorVersion);
  const timer = setInterval(async () => {
    try {
      const response = await apiGet<{ success: boolean; job: JavaInstallJob }>(
        `/api/java/runtimes/${majorVersion}/install`,
      );
      installJobs.value = { ...installJobs.value, [majorVersion]: response.job };

      if (response.job.phase === "done") {
        stopPolling(majorVersion);
        launcherMessage.value = response.job.message ?? t("settings.javaInstalled");
        await loadRuntimes();
      } else if (response.job.phase === "failed") {
        stopPolling(majorVersion);
        error.value = response.job.message ?? t("settings.javaInstallFailed");
      }
    } catch {
      // Keep polling: a dropped request mid-download is not a failure.
    }
  }, 2000);
  pollTimers.set(majorVersion, timer);
};

const stopAllPolling = () => {
  for (const majorVersion of [...pollTimers.keys()]) stopPolling(majorVersion);
};

export function useJava() {
  return {
    detected,
    managed,
    installable,
    configured,
    isLoading,
    installJobs,
    loadRuntimes,
    installRuntime,
    isAvailable,
    stopAllPolling,
  };
}
