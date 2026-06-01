import { ref } from "vue";
import { apiFetch } from "./api";
import { error, launcherMessage } from "./state";
import type { LaunchPhase } from "./types";
import { useInstances } from "./useInstances";

const { selectedInstance, loadInstances } = useInstances();

const isLaunching = ref(false);
const launchPhase = ref<LaunchPhase>("idle");
const launchMessage = ref<string | null>(null);

let launchPollInterval: ReturnType<typeof window.setInterval> | null = null;

export const stopLaunchPolling = () => {
    if (launchPollInterval !== null) { window.clearInterval(launchPollInterval); launchPollInterval = null; }
};

const pollLaunchStatus = async (instanceName: string) => {
    try {
        const d = await apiFetch<{
            success: boolean; phase: LaunchPhase; message?: string;
            instanceName?: string; version?: string; pid?: number;
            javaMajorVersion?: number; error?: string;
        }>(`/api/instances/${encodeURIComponent(instanceName)}/launch-status`);

        launchPhase.value = d.phase;
        launchMessage.value = d.message ?? null;

        if (d.phase === "running") {
            isLaunching.value = false;
            launcherMessage.value = [
                d.instanceName ?? instanceName,
                d.version && `mit ${d.version}`,
                d.javaMajorVersion && `Java ${d.javaMajorVersion}`,
                d.pid && `PID ${d.pid}`,
            ].filter(Boolean).join(" ") + " gestartet.";
            stopLaunchPolling();
            await loadInstances();
        } else if (d.phase === "failed") {
            isLaunching.value = false;
            error.value = d.message ?? "Launch fehlgeschlagen";
            stopLaunchPolling();
        }
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Launch-Status konnte nicht abgefragt werden";
        isLaunching.value = false;
        stopLaunchPolling();
    }
};

const handleLaunch = async () => {
    if (!selectedInstance.value) return;
    const instanceName = selectedInstance.value.name;
    try {
        isLaunching.value = true;
        launchPhase.value = "installing";
        launchMessage.value = "Starte Installation...";
        error.value = null;

        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/instances/${encodeURIComponent(instanceName)}/launch`,
            { method: "POST" }
        );
        if (!d.success) {
            error.value = d.error ?? "Minecraft konnte nicht gestartet werden";
            isLaunching.value = false;
            launchPhase.value = "idle";
            return;
        }

        launchPollInterval = window.setInterval(() => { void pollLaunchStatus(instanceName); }, 1500);
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Minecraft konnte nicht gestartet werden";
        isLaunching.value = false;
        launchPhase.value = "idle";
        stopLaunchPolling();
    }
};

const handleStop = async () => {
    if (!selectedInstance.value) return;
    try {
        isLaunching.value = true;
        error.value = null;
        const d = await apiFetch<{ success: boolean; instanceName?: string; error?: string }>(
            `/api/instances/${encodeURIComponent(selectedInstance.value.name)}/stop`,
            { method: "POST" }
        );
        if (!d.success) { error.value = d.error ?? "Profil konnte nicht gestoppt werden"; return; }
        launcherMessage.value = `${d.instanceName ?? selectedInstance.value.name} erfolgreich gestoppt.`;
        launchPhase.value = "idle";
        await loadInstances();
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profil konnte nicht gestoppt werden";
    } finally {
        isLaunching.value = false;
    }
};

const handleWindowAction = async (action: "minimize" | "maximize" | "close") => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(`/api/window/${action}`, { method: "POST" });
        if (!d.success) error.value = d.error ?? `Fensteraktion "${action}" fehlgeschlagen`;
    } catch (e) {
        error.value = e instanceof Error ? e.message : `Fensteraktion "${action}" fehlgeschlagen`;
    }
};

const handleWindowMinimize = () => handleWindowAction("minimize");
const handleWindowMaximize = () => handleWindowAction("maximize");
const handleWindowClose = () => handleWindowAction("close");

export function useLaunch() {
    return {
        isLaunching, launchPhase, launchMessage,
        handleLaunch, handleStop, stopLaunchPolling,
        handleWindowMinimize, handleWindowMaximize, handleWindowClose,
    };
}
