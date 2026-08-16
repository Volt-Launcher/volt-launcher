import { watch } from "vue";
import { error, launcherMessage } from "./state";
import { pushNotification, useNotifications } from "./useNotifications";
import { stopAuth, useAuth } from "./useAuth";
import { setupInstanceWatchers, teardownInstanceWatchers, useInstances } from "./useInstances";
import { stopLaunchPolling, useLaunch } from "./useLaunch";
import { useSettings } from "./useSettings";
import { useProviders } from "./useProviders";
import { useJava } from "./useJava";
import { useI18n } from "@/i18n";
import {
  formatLoaderId,
  formatRelativeDate,
  formatRelativeIso,
  formatReleaseTime,
  formatVersionType,
  handleImgError,
  loaderOf,
  minecraftVersionOf,
  versionEmoji,
  versionGradient,
} from "./helpers";

// Re-exported so components can import everything from one place.
export type * from "./types";
export type { PendingInstance } from "./useInstances";
export {
  versionEmoji,
  versionGradient,
  formatRelativeDate,
  formatRelativeIso,
  formatVersionType,
  formatReleaseTime,
  formatLoaderId,
  minecraftVersionOf,
  loaderOf,
  handleImgError,
};

/** How often the profile list is refreshed to pick up launch/exit state changes. */
const INSTANCE_POLL_MS = 3000;

let instancePollInterval: ReturnType<typeof window.setInterval> | null = null;
let notificationUnwatchers: Array<() => void> = [];

async function init() {
  const { loadSession } = useAuth();
  const { loadInstances, loadVersions, loadPlatforms } = useInstances();
  const { loadSettings } = useSettings();
  const { loadProviders } = useProviders();

  // Settings first: they carry the language and accent colour the rest of the UI renders with.
  await loadSettings();

  void loadSession();
  void loadPlatforms();
  void loadInstances();
  void loadVersions();
  void loadProviders();

  instancePollInterval = window.setInterval(() => void loadInstances(), INSTANCE_POLL_MS);
  setupInstanceWatchers();

  notificationUnwatchers = [
    watch(error, (value) => {
      if (value) pushNotification("error", value);
    }),
    watch(launcherMessage, (value) => {
      if (value) pushNotification("info", value);
    }),
  ];
}

function cleanup() {
  stopAuth();
  stopLaunchPolling();
  useJava().stopAllPolling();

  if (instancePollInterval !== null) {
    window.clearInterval(instancePollInterval);
    instancePollInterval = null;
  }
  teardownInstanceWatchers();

  for (const stop of notificationUnwatchers) stop();
  notificationUnwatchers = [];
}

export function useLauncher() {
  return {
    ...useAuth(),
    ...useInstances(),
    ...useLaunch(),
    ...useNotifications(),
    ...useSettings(),
    ...useProviders(),
    ...useI18n(),
    java: useJava(),

    error,
    launcherMessage,

    versionEmoji,
    versionGradient,
    formatRelativeDate,
    formatRelativeIso,
    formatVersionType,
    formatReleaseTime,
    formatLoaderId,
    minecraftVersionOf,
    loaderOf,
    handleImgError,

    init,
    cleanup,
  };
}
