import { watch } from "vue";
import { pushNotification } from "./useNotifications";
import { error, launcherMessage } from "./state";
import { useAuth, stopAuth } from "./useAuth";
import { useInstances, setupInstanceWatchers, teardownInstanceWatchers } from "./useInstances";
import { useLaunch, stopLaunchPolling } from "./useLaunch";
import { useNotifications } from "./useNotifications";
import { useSettings } from "./useSettings";
import { versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime, formatLoaderId, handleImgError } from "./helpers";

// Re-export types and helpers so existing consumers don't need to change their imports.
export type { AccountEntry, AuthData, LauncherInstance, InstanceSettings, ContentType, ContentEntry, AvailableVersion, PlatformId, MainTab, JavaRuntime, LaunchPhase, LauncherNotification } from "./types";
export type { PendingInstance } from "./useInstances";
export { versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime, formatLoaderId, handleImgError };

// ── Lifecycle ─────────────────────────────────────────────────────────────────

let instancePollInterval: ReturnType<typeof window.setInterval> | null = null;
let notifUnwatches: (() => void)[] = [];

function init() {
    const { loadSession } = useAuth();
    const { loadInstances, loadVersions } = useInstances();

    void loadSession();
    void loadInstances();
    void loadVersions();

    instancePollInterval = window.setInterval(() => { void loadInstances(); }, 3000);

    setupInstanceWatchers();

    notifUnwatches = [
        watch(error, (v) => { if (v) pushNotification("error", v); }),
        watch(launcherMessage, (v) => { if (v) pushNotification("info", v); }),
    ];
}

function cleanup() {
    stopAuth();
    stopLaunchPolling();
    if (instancePollInterval !== null) { window.clearInterval(instancePollInterval); instancePollInterval = null; }
    teardownInstanceWatchers();
    for (const stop of notifUnwatches) stop();
    notifUnwatches = [];
}

// ── Public API ────────────────────────────────────────────────────────────────

export function useLauncher() {
    return {
        ...useAuth(),
        ...useInstances(),
        ...useLaunch(),
        ...useNotifications(),
        ...useSettings(),
        error,
        launcherMessage,
        versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime, formatLoaderId, handleImgError,
        init,
        cleanup,
    };
}
