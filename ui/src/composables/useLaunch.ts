import { ref } from "vue";
import { apiGet, apiSend, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type { LaunchPhase } from "./types";
import { useInstances } from "./useInstances";

const { selectedInstance, loadInstances } = useInstances();

const isLaunching = ref(false);
const launchPhase = ref<LaunchPhase>("idle");
const launchMessage = ref<string | null>(null);

let launchPollInterval: ReturnType<typeof window.setInterval> | null = null;

export const stopLaunchPolling = () => {
  if (launchPollInterval !== null) {
    window.clearInterval(launchPollInterval);
    launchPollInterval = null;
  }
};

interface LaunchStatus {
  success: boolean;
  phase: LaunchPhase;
  message: string | null;
  instanceName?: string;
  version?: string;
  pid?: number;
  javaMajorVersion?: number;
}

const pollLaunchStatus = async (instanceName: string) => {
  try {
    const status = await apiGet<LaunchStatus>(
      `/api/instances/${encodeURIComponent(instanceName)}/launch-status`,
    );

    launchPhase.value = status.phase;
    launchMessage.value = status.message;

    if (status.phase === "running") {
      isLaunching.value = false;
      launcherMessage.value = t("launch.started", { name: status.instanceName ?? instanceName });
      stopLaunchPolling();
      await loadInstances();
    } else if (status.phase === "failed") {
      isLaunching.value = false;
      error.value = status.message ?? t("launch.failed");
      stopLaunchPolling();
    }
  } catch (e) {
    error.value = errorMessage(e, t("launch.failed"));
    isLaunching.value = false;
    stopLaunchPolling();
  }
};

/** Accepts an explicit profile name, or nothing when wired directly to a click handler. */
const resolveInstanceName = (candidate?: unknown): string | undefined =>
  typeof candidate === "string" && candidate ? candidate : selectedInstance.value?.name;

const handleLaunch = async (instanceName?: unknown) => {
  const name = resolveInstanceName(instanceName);
  if (!name) return;

  try {
    isLaunching.value = true;
    launchPhase.value = "installing";
    launchMessage.value = t("launch.preparing");
    error.value = null;

    await apiSend("POST", `/api/instances/${encodeURIComponent(name)}/launch`);
    stopLaunchPolling();
    launchPollInterval = window.setInterval(() => void pollLaunchStatus(name), 1500);
  } catch (e) {
    error.value = errorMessage(e, t("launch.failed"));
    isLaunching.value = false;
    launchPhase.value = "idle";
    stopLaunchPolling();
  }
};

const handleStop = async (instanceName?: unknown) => {
  const name = resolveInstanceName(instanceName);
  if (!name) return;

  try {
    isLaunching.value = true;
    error.value = null;
    await apiSend("POST", `/api/instances/${encodeURIComponent(name)}/stop`);
    launcherMessage.value = t("launch.stopped", { name });
    launchPhase.value = "idle";
    stopLaunchPolling();
    await loadInstances();
  } catch (e) {
    error.value = errorMessage(e, t("launch.stopFailed"));
  } finally {
    isLaunching.value = false;
  }
};

// ── Window chrome ─────────────────────────────────────────────────────────────

// The renderer runs with nodeIntegration enabled, so `require` is available at runtime but not
// in the type system.
interface RendererIpc {
  send(channel: string): void;
}

const ipcRenderer = (
  window as unknown as { require?: (module: string) => { ipcRenderer?: RendererIpc } }
).require?.("electron")?.ipcRenderer;

const sendWindowCommand = async (channel: string, endpoint: string) => {
  if (ipcRenderer) {
    ipcRenderer.send(channel);
    return;
  }
  // Running outside Electron (e.g. the Vite dev server in a browser): fall back to the API.
  try {
    await apiSend("POST", endpoint);
  } catch {
    // Nothing to control — harmless in a plain browser.
  }
};

const handleWindowMinimize = () => void sendWindowCommand("window-minimize", "/api/window/minimize");
const handleWindowMaximize = () => void sendWindowCommand("window-maximize", "/api/window/maximize");
const handleWindowClose = () => void sendWindowCommand("window-close", "/api/window/close");

export function useLaunch() {
  return {
    isLaunching,
    launchPhase,
    launchMessage,
    handleLaunch,
    handleStop,
    stopLaunchPolling,
    handleWindowMinimize,
    handleWindowMaximize,
    handleWindowClose,
  };
}
