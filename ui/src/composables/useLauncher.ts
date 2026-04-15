import { computed, ref, watch, type WatchStopHandle } from "vue";

// ── Types ───────────────────────────────────────────────────────────────────
export interface AuthData { uuid: string; username: string; }
export interface LauncherInstance { name: string; slug: string; versionId: string; versionType: string; createdAt: number; lastPlayedAt: number; javaMajorVersion: number; javaComponent: string; running: boolean; launchPhase?: string; pid?: number; startedAt?: number; javaExecutable?: string; runningJavaMajorVersion?: number; }
export interface AvailableVersion { id: string; type: string; releaseTime: string; }
export type PlatformId = "vanilla" | "fabric" | "forge" | "neoforge" | "quilt";
export type MainTab = "home" | "profiles" | "skins" | "discover" | "settings";
export interface JavaRuntime { version: number; path: string; }
export type LaunchPhase = "idle" | "installing" | "launching" | "running" | "failed";
export interface LauncherNotification { id: number; type: "error" | "info"; message: string; timestamp: number; }

// ── Reactive state ──────────────────────────────────────────────────────────
const authData = ref<AuthData | null>(null);
const instances = ref<LauncherInstance[]>([]);
const availableMinecraftVersions = ref<AvailableVersion[]>([]);
const loaderVersions = ref<AvailableVersion[]>([]);
const selectedInstanceName = ref("");
const newInstanceName = ref("");
const selectedPlatformId = ref<PlatformId>("vanilla");
const selectedMinecraftVersionId = ref("");
const selectedLoaderVersionId = ref("");
const includeSnapshots = ref(false);
const includeBetas = ref(false);
const includeAlphas = ref(false);
const isAuthenticating = ref(false);
const isLaunching = ref(false);
const launchPhase = ref<LaunchPhase>("idle");
const launchMessage = ref<string | null>(null);
const isCreatingInstance = ref(false);
const isLoadingInstances = ref(false);
const isLoadingVersions = ref(false);
const isLoadingLoaderVersions = ref(false);
const error = ref<string | null>(null);
const launcherMessage = ref<string | null>(null);

const platformOptions = ref<Array<{ value: PlatformId; label: string }>>([
    { value: "vanilla", label: "Vanilla" },
    { value: "fabric", label: "Fabric" },
    { value: "forge", label: "Forge" },
    { value: "neoforge", label: "NeoForge" },
    { value: "quilt", label: "Quilt" },
]);

// ── Notifications ───────────────────────────────────────────────────────────
const NOTIF_KEY = "launcher_notifications";
let notifIdCounter = 0;
const loadStoredNotifications = (): LauncherNotification[] => {
    try { const raw = sessionStorage.getItem(NOTIF_KEY); if (raw) { const parsed = JSON.parse(raw) as LauncherNotification[]; notifIdCounter = parsed.reduce((max, n) => Math.max(max, n.id), 0); return parsed; } } catch { /* ignore */ }
    return [];
};
const notifications = ref<LauncherNotification[]>(loadStoredNotifications());
const persistNotifications = () => { try { sessionStorage.setItem(NOTIF_KEY, JSON.stringify(notifications.value)); } catch { /* ignore */ } };
const pushNotification = (type: "error" | "info", message: string) => {
    notifications.value.unshift({ id: ++notifIdCounter, type, message, timestamp: Date.now() });
    persistNotifications();
};
const clearNotification = (id: number) => { notifications.value = notifications.value.filter(n => n.id !== id); persistNotifications(); };
const clearAllNotifications = () => { notifications.value = []; persistNotifications(); };
const unreadCount = computed(() => notifications.value.length);
const formatNotifTime = (ts: number) => {
    const d = Date.now() - ts, s = Math.floor(d / 1000), m = Math.floor(d / 60000), h = Math.floor(d / 3600000), dy = Math.floor(d / 86400000);
    if (s < 60) return "just now"; if (m < 60) return `${m}m ago`; if (h < 24) return `${h}h ago`; return `${dy}d ago`;
};

// ── UI state ────────────────────────────────────────────────────────────────
const activeTab = ref<MainTab>("home");
const showCreateModal = ref(false);
const profileFilter = ref("ALL");
const discoverTabActive = ref("MODPACKS");
const discoverPlatformActive = ref("MODRINTH");
const settingsNavItem = ref("General");
const accentColor = ref("#00b2ff");
const toggleStates = ref({ autoUpdate: true, discordPresence: true, betaUpdates: false, openLogs: true, hwAccel: true, hideLauncher: false });
const uiScale = ref<"compact" | "default" | "comfortable">("default");
const animationsEnabled = ref(true);
const showFps = ref(false);
const javaRuntimes = ref<JavaRuntime[]>([
    { version: 8, path: "" },
    { version: 17, path: "" },
    { version: 21, path: "" },
]);
const jvmArgs = ref("-XX:+UseG1GC -XX:+ParallelRefProcEnabled");
const minMemory = ref(2);
const maxMemory = ref(4);

// ── Internal ────────────────────────────────────────────────────────────────
let authState = "";
let authPollInterval: ReturnType<typeof window.setInterval> | null = null;
let instancePollInterval: ReturnType<typeof window.setInterval> | null = null;
let launchPollInterval: ReturnType<typeof window.setInterval> | null = null;
let versionsUnwatch: WatchStopHandle | null = null;
let platformUnwatch: WatchStopHandle | null = null;
let minecraftVersionUnwatch: WatchStopHandle | null = null;

// ── Computed ────────────────────────────────────────────────────────────────
const selectedInstance = computed(() => instances.value.find((i) => i.name === selectedInstanceName.value) ?? null);
const runningInstancesCount = computed(() => instances.value.filter((i) => i.running).length);
const requiresLoaderSelection = computed(() => selectedPlatformId.value !== "vanilla");
const availableVersions = computed(() =>
    requiresLoaderSelection.value ? loaderVersions.value : availableMinecraftVersions.value,
);
const selectedVersionId = computed(() =>
    requiresLoaderSelection.value ? selectedLoaderVersionId.value : selectedMinecraftVersionId.value,
);
const selectedVersion = computed(() => availableVersions.value.find((v) => v.id === selectedVersionId.value) ?? null);
const playerName = computed(() => authData.value?.username ?? "");
const playerSkinUrl = computed(() => `https://crafatar.com/renders/body/${encodeURIComponent(playerName.value || "MHF_Steve")}?overlay&scale=10`);
const playerSkinFallback = computed(() => `https://mc-heads.net/body/${encodeURIComponent(playerName.value || "MHF_Steve")}/300`);
const playerSkinTextureUrl = computed(() =>
    authData.value?.uuid
        ? `https://mineskin.eu/skin/${encodeURIComponent(authData.value.uuid)}`
        : "https://mineskin.eu/skin/MHF_Steve",
);
const playerAvatarUrl = computed(() => `https://crafatar.com/avatars/${encodeURIComponent(playerName.value || "MHF_Steve")}?size=24&overlay`);
const playerAvatarFallback = computed(() => `https://mc-heads.net/avatar/${encodeURIComponent(playerName.value || "MHF_Steve")}/24`);
const filteredInstances = computed(() => profileFilter.value === "ALL" ? instances.value : instances.value.filter((i) => i.versionType.toLowerCase().includes(profileFilter.value.toLowerCase())));

// ── Helpers ─────────────────────────────────────────────────────────────────
export const versionEmoji = (t: string) => ({ release: "📦", snapshot: "🔬", old_beta: "⚗️", old_alpha: "⚔️" }[t] ?? "🎮");
export const versionGradient = (t: string) => ({ release: "linear-gradient(135deg,#0d3a18,#184d22)", snapshot: "linear-gradient(135deg,#0a2040,#001535)", old_beta: "linear-gradient(135deg,#3a1a08,#5a2a10)", old_alpha: "linear-gradient(135deg,#4d0f0f,#7a1a1a)" }[t] ?? "linear-gradient(135deg,#0a1535,#122050)");
export const formatRelativeDate = (ts: number) => { if (!ts) return "Nie gespielt"; const d = Date.now() - ts, m = Math.floor(d / 6e4), h = Math.floor(d / 36e5), dy = Math.floor(d / 864e5), w = Math.floor(dy / 7), mo = Math.floor(dy / 30); if (m < 1) return "gerade eben"; if (m < 60) return `vor ${m}min`; if (h < 24) return `vor ${h}h`; if (dy < 7) return `vor ${dy}T`; if (w < 5) return `vor ${w}W`; return `vor ${mo}M`; };
export const formatVersionType = (t: string) => ({ release: "Release", snapshot: "Snapshot", old_beta: "Beta", old_alpha: "Alpha" }[t] ?? t);
export const formatReleaseTime = (rt: string) => { if (!rt) return "Unbekannt"; const d = new Date(rt); return Number.isNaN(d.getTime()) ? rt : d.toLocaleDateString("de-DE"); };
export const handleImgError = (event: Event) => { const img = event.target as HTMLImageElement; const fb = img.dataset.fallbackSrc; if (fb && img.src !== fb) img.src = fb; };

// ── API helper ──────────────────────────────────────────────────────────────
const apiFetch = async <T>(url: string, init?: RequestInit): Promise<T> => {
    const r = await fetch(url, init);
    return r.json() as Promise<T>;
};

// ── Auth ────────────────────────────────────────────────────────────────────
const stopAuth = () => {
    if (authPollInterval !== null) { window.clearInterval(authPollInterval); authPollInterval = null; }
    authState = "";
};

const pollAuthStatus = async () => {
    if (!authState) return;
    try {
        const d = await apiFetch<{ success: boolean; status: string; uuid?: string; username?: string; error?: string }>(
            `/api/auth/status?state=${encodeURIComponent(authState)}`
        );
        if (d.status === "pending") return;
        if (d.status === "success" && d.uuid && d.username) {
            authData.value = { uuid: d.uuid, username: d.username };
            error.value = null;
        } else {
            error.value = d.error ?? "Authentifizierung fehlgeschlagen";
        }
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Authentifizierungsstatus konnte nicht geprüft werden";
    }
    isAuthenticating.value = false;
    stopAuth();
};

const handleLogin = async () => {
    try {
        isAuthenticating.value = true;
        error.value = null;
        const d = await apiFetch<{ success: boolean; state?: string; url?: string; error?: string }>("/api/auth/login");
        if (!d.success || !d.state) {
            error.value = d.error ?? "Authentifizierung fehlgeschlagen";
            isAuthenticating.value = false;
            return;
        }
        authState = d.state;
        authPollInterval = window.setInterval(() => { void pollAuthStatus(); }, 1500);
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Unbekannter Fehler";
        isAuthenticating.value = false;
        stopAuth();
    }
};

const handleLogout = async () => {
    try { await fetch("/api/auth/logout", { method: "POST" }); } catch { /* ignore */ }
    authData.value = null;
    error.value = null;
    launcherMessage.value = null;
};

// ── Data loading ─────────────────────────────────────────────────────────────
const loadSession = async () => {
    try {
        const d = await apiFetch<{ success: boolean; authenticated: boolean; uuid?: string; username?: string; error?: string }>("/api/session");
        if (d.authenticated && d.uuid && d.username) { authData.value = { uuid: d.uuid, username: d.username }; return; }
        authData.value = null;
        if (!d.success) error.value = d.error ?? "Session konnte nicht wiederhergestellt werden";
    } catch (e) {
        authData.value = null;
        error.value = e instanceof Error ? e.message : "Session konnte nicht wiederhergestellt werden";
    }
};

const loadInstances = async () => {
    try {
        isLoadingInstances.value = true;
        const d = await apiFetch<{ success: boolean; instances?: LauncherInstance[]; error?: string }>("/api/instances");
        if (!d.success) { error.value = d.error ?? "Profile konnten nicht geladen werden"; return; }
        instances.value = d.instances ?? [];
        if (!selectedInstanceName.value || !instances.value.some((i) => i.name === selectedInstanceName.value))
            selectedInstanceName.value = instances.value[0]?.name ?? "";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profile konnten nicht geladen werden";
    } finally { isLoadingInstances.value = false; }
};

const loadVersions = async () => {
    try {
        isLoadingVersions.value = true;
        const q = new URLSearchParams({ includeSnapshots: String(includeSnapshots.value), includeBetas: String(includeBetas.value), includeAlphas: String(includeAlphas.value) });
        const d = await apiFetch<{ success: boolean; versions?: AvailableVersion[]; error?: string }>(`/api/instances/versions?${q}`);
        if (!d.success) { error.value = d.error ?? "Versionen konnten nicht geladen werden"; return; }
        availableMinecraftVersions.value = d.versions ?? [];
        if (!selectedMinecraftVersionId.value || !availableMinecraftVersions.value.some((v) => v.id === selectedMinecraftVersionId.value))
            selectedMinecraftVersionId.value = availableMinecraftVersions.value[0]?.id ?? "";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Versionen konnten nicht geladen werden";
    } finally { isLoadingVersions.value = false; }
};

const loadLoaderVersions = async () => {
    if (!requiresLoaderSelection.value) {
        loaderVersions.value = [];
        selectedLoaderVersionId.value = "";
        return;
    }
    if (!selectedMinecraftVersionId.value) {
        loaderVersions.value = [];
        selectedLoaderVersionId.value = "";
        return;
    }
    try {
        isLoadingLoaderVersions.value = true;
        const q = new URLSearchParams({
            platformId: selectedPlatformId.value,
            minecraftVersionId: selectedMinecraftVersionId.value,
        });
        const d = await apiFetch<{ success: boolean; versions?: AvailableVersion[]; error?: string }>(`/api/instances/loader-versions?${q}`);
        if (!d.success) { error.value = d.error ?? "Loader-Versionen konnten nicht geladen werden"; return; }
        loaderVersions.value = d.versions ?? [];
        if (!selectedLoaderVersionId.value || !loaderVersions.value.some((v) => v.id === selectedLoaderVersionId.value)) {
            selectedLoaderVersionId.value = loaderVersions.value[0]?.id ?? "";
        }
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Loader-Versionen konnten nicht geladen werden";
    } finally {
        isLoadingLoaderVersions.value = false;
    }
};

// ── Instance actions ──────────────────────────────────────────────────────────
const handleCreateInstance = async () => {
    if (!newInstanceName.value.trim()) { error.value = "Bitte einen Profilnamen eingeben"; return; }
    if (!selectedMinecraftVersionId.value) { error.value = "Bitte eine Minecraft-Version wählen"; return; }
    if (requiresLoaderSelection.value && !selectedLoaderVersionId.value) { error.value = "Bitte eine Loader-Version wählen"; return; }
    try {
        isCreatingInstance.value = true;
        error.value = null;
        const d = await apiFetch<{ success: boolean; instance?: LauncherInstance; error?: string }>("/api/instances", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: newInstanceName.value.trim(), versionId: selectedVersionId.value }),
        });
        if (!d.success || !d.instance) { error.value = d.error ?? "Profil konnte nicht erstellt werden"; return; }
        launcherMessage.value = `Profil "${d.instance.name}" erfolgreich erstellt.`;
        newInstanceName.value = "";
        await loadInstances();
        selectedInstanceName.value = d.instance.name;
        showCreateModal.value = false;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profil konnte nicht erstellt werden";
    } finally { isCreatingInstance.value = false; }
};

const stopLaunchPolling = () => {
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
    if (!authData.value || !selectedInstance.value) return;
    const instanceName = selectedInstance.value.name;
    try {
        isLaunching.value = true;
        launchPhase.value = "installing";
        launchMessage.value = "Starte Installation...";
        error.value = null;

        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/instances/${encodeURIComponent(instanceName)}/launch`,
            { method: "POST" },
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
            { method: "POST" },
        );
        if (!d.success) { error.value = d.error ?? "Profil konnte nicht gestoppt werden"; return; }
        launcherMessage.value = `${d.instanceName ?? selectedInstance.value.name} erfolgreich gestoppt.`;
        launchPhase.value = "idle";
        await loadInstances();
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profil konnte nicht gestoppt werden";
    } finally { isLaunching.value = false; }
};

const handleWindowAction = async (action: "minimize" | "maximize" | "close") => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(`/api/window/${action}`, { method: "POST" });
        if (!d.success) error.value = d.error ?? `Fensteraktion "${action}" fehlgeschlagen`;
    } catch (e) {
        error.value = e instanceof Error ? e.message : `Fensteraktion "${action}" fehlgeschlagen`;
    }
};

const handleWindowMinimize = async () => { await handleWindowAction("minimize"); };
const handleWindowMaximize = async () => { await handleWindowAction("maximize"); };
const handleWindowClose = async () => { await handleWindowAction("close"); };

const setAccentColor = (c: string) => { accentColor.value = c; };

// ── Appearance helpers ────────────────────────────────────────────────────────
const uiScaleFactors: Record<string, number> = { compact: 0.88, default: 1, comfortable: 1.14 };
const baseTextSizes: Record<string, number> = {
    "--text-2xs": 12, "--text-2xs-plus": 12.5, "--text-xs": 13, "--text-sm": 13.5,
    "--text-base": 14, "--text-base-plus": 14.5, "--text-md": 15, "--text-md-plus": 15.5,
    "--text-lg": 16, "--text-xl": 18,
};
const applyUiScale = (scale: string) => {
    const f = uiScaleFactors[scale] ?? 1;
    const el = document.documentElement;
    for (const [token, base] of Object.entries(baseTextSizes)) {
        el.style.setProperty(token, `${+(base * f).toFixed(1)}px`);
    }
};
const applyAnimations = (enabled: boolean) => {
    document.documentElement.toggleAttribute("data-no-animations", !enabled);
};

watch(uiScale, (v) => applyUiScale(v), { immediate: true });
watch(animationsEnabled, (v) => applyAnimations(v), { immediate: true });
watch(error, (v) => { if (v) pushNotification("error", v); });
watch(launcherMessage, (v) => { if (v) pushNotification("info", v); });

// ── Lifecycle ─────────────────────────────────────────────────────────────────
function init() {
    void loadSession();
    void loadInstances();
    void loadVersions();
    instancePollInterval = window.setInterval(() => { void loadInstances(); }, 3000);
    versionsUnwatch = watch([includeSnapshots, includeBetas, includeAlphas], () => { void loadVersions(); });
    platformUnwatch?.();
    minecraftVersionUnwatch?.();
    platformUnwatch = watch(selectedPlatformId, () => {
        if (!requiresLoaderSelection.value) {
            loaderVersions.value = [];
            selectedLoaderVersionId.value = "";
            return;
        }
        void loadLoaderVersions();
    });
    minecraftVersionUnwatch = watch(selectedMinecraftVersionId, () => {
        if (!requiresLoaderSelection.value) return;
        void loadLoaderVersions();
    });
}

function cleanup() {
    stopAuth();
    stopLaunchPolling();
    if (instancePollInterval !== null) { window.clearInterval(instancePollInterval); instancePollInterval = null; }
    versionsUnwatch?.();
    versionsUnwatch = null;
    platformUnwatch?.();
    platformUnwatch = null;
    minecraftVersionUnwatch?.();
    minecraftVersionUnwatch = null;
}

// ── Public API ────────────────────────────────────────────────────────────────
export function useLauncher() {
    return {
        authData, instances, availableVersions, availableMinecraftVersions, loaderVersions,
        selectedInstanceName, newInstanceName, selectedVersionId,
        selectedPlatformId, selectedMinecraftVersionId, selectedLoaderVersionId,
        includeSnapshots, includeBetas, includeAlphas,
        isAuthenticating, isLaunching, launchPhase, launchMessage,
        isCreatingInstance, isLoadingInstances, isLoadingVersions, isLoadingLoaderVersions,
        error, launcherMessage, notifications, unreadCount, formatNotifTime, clearNotification, clearAllNotifications,
        activeTab, showCreateModal, profileFilter, discoverTabActive, discoverPlatformActive, settingsNavItem, accentColor, toggleStates,
        uiScale, animationsEnabled, showFps,
        javaRuntimes, jvmArgs, minMemory, maxMemory,
        selectedInstance, runningInstancesCount, selectedVersion, requiresLoaderSelection, platformOptions,
        playerName, playerSkinUrl, playerSkinFallback, playerSkinTextureUrl, playerAvatarUrl, playerAvatarFallback, filteredInstances,
        versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime, handleImgError,
        handleLogin, handleLogout, handleCreateInstance, handleLaunch, handleStop,
        handleWindowMinimize, handleWindowMaximize, handleWindowClose,
        loadInstances, loadVersions, loadLoaderVersions, setAccentColor,
        init, cleanup,
    };
}