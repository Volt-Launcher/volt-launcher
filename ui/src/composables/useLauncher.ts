import { computed, ref, type WatchStopHandle } from "vue";
import { watch } from "vue";

// ── Types ──────────────────────────────────────────────────────────────────
export interface AuthData { uuid: string; username: string; }
export interface SessionResponse { success: boolean; authenticated: boolean; uuid?: string; username?: string; error?: string; }
export interface LauncherInstance { name: string; slug: string; versionId: string; versionType: string; createdAt: number; lastPlayedAt: number; javaMajorVersion: number; javaComponent: string; running: boolean; pid?: number; startedAt?: number; javaExecutable?: string; runningJavaMajorVersion?: number; }
export interface AvailableVersion { id: string; type: string; releaseTime: string; }
export type MainTab = "home" | "profiles" | "skins" | "discover" | "settings";

// ── Singleton reactive state ───────────────────────────────────────────────
const authData = ref<AuthData | null>(null);
const instances = ref<LauncherInstance[]>([]);
const availableVersions = ref<AvailableVersion[]>([]);
const selectedInstanceName = ref<string>("");
const newInstanceName = ref<string>("");
const selectedVersionId = ref<string>("");
const includeSnapshots = ref(false);
const includeBetas = ref(false);
const includeAlphas = ref(false);
const isAuthenticating = ref(false);
const isLaunching = ref(false);
const isCreatingInstance = ref(false);
const isLoadingInstances = ref(false);
const isLoadingVersions = ref(false);
const error = ref<string | null>(null);
const launcherMessage = ref<string | null>(null);
const authUrl = ref<string>("");
const authState = ref<string>("");
const authWindowWasClosed = ref(false);

// ── UI state ───────────────────────────────────────────────────────────────
const activeTab = ref<MainTab>("home");
const showCreateModal = ref(false);
const profileFilter = ref("ALL");
const discoverTabActive = ref("MODPACKS");
const settingsNavItem = ref("Allgemein");
const accentColor = ref("#00b2ff");
const toggleStates = ref({ autoUpdate: true, discordPresence: true, betaUpdates: false, openLogs: true, hwAccel: true, hideLauncher: false });

// ── Internal (non-reactive) ────────────────────────────────────────────────
let authPopup: Window | null = null;
let authStatusInterval: ReturnType<typeof window.setInterval> | null = null;
let instanceRefreshInterval: ReturnType<typeof window.setInterval> | null = null;
let versionsWatchStop: WatchStopHandle | null = null;
let authStartedAt = 0;

// ── Computed ───────────────────────────────────────────────────────────────
const selectedInstance = computed<LauncherInstance | null>(() => instances.value.find((i) => i.name === selectedInstanceName.value) ?? null);
const runningInstancesCount = computed(() => instances.value.filter((i) => i.running).length);
const selectedVersion = computed<AvailableVersion | null>(() => availableVersions.value.find((v) => v.id === selectedVersionId.value) ?? null);
const playerName = computed(() => authData.value?.username ?? "");
const playerSkinUrl = computed(() => `https://crafatar.com/renders/body/${encodeURIComponent(playerName.value || "MHF_Steve")}?overlay&scale=10`);
const playerSkinFallback = computed(() => `https://mc-heads.net/body/${encodeURIComponent(playerName.value || "MHF_Steve")}/300`);
const playerAvatarUrl = computed(() => `https://crafatar.com/avatars/${encodeURIComponent(playerName.value || "MHF_Steve")}?size=24&overlay`);
const playerAvatarFallback = computed(() => `https://mc-heads.net/avatar/${encodeURIComponent(playerName.value || "MHF_Steve")}/24`);
const filteredInstances = computed(() => profileFilter.value === "ALL" ? instances.value : instances.value.filter((i) => i.versionType.toLowerCase().includes(profileFilter.value.toLowerCase())));

// ── Helpers ────────────────────────────────────────────────────────────────
export const versionEmoji = (t: string) => ({ release: "📦", snapshot: "🔬", old_beta: "⚗️", old_alpha: "⚔️" }[t] ?? "🎮");
export const versionGradient = (t: string) => ({ release: "linear-gradient(135deg,#0d3a18,#184d22)", snapshot: "linear-gradient(135deg,#0a2040,#001535)", old_beta: "linear-gradient(135deg,#3a1a08,#5a2a10)", old_alpha: "linear-gradient(135deg,#4d0f0f,#7a1a1a)" }[t] ?? "linear-gradient(135deg,#0a1535,#122050)");
export const formatRelativeDate = (ts: number) => { if (!ts) return "Nie gespielt"; const d = Date.now() - ts, m = Math.floor(d / 6e4), h = Math.floor(d / 36e5), dy = Math.floor(d / 864e5), w = Math.floor(dy / 7), mo = Math.floor(dy / 30); if (m < 1) return "gerade eben"; if (m < 60) return `vor ${m}min`; if (h < 24) return `vor ${h}h`; if (dy < 7) return `vor ${dy}T`; if (w < 5) return `vor ${w}W`; return `vor ${mo}M`; };
export const formatVersionType = (t: string) => ({ release: "Release", snapshot: "Snapshot", old_beta: "Beta", old_alpha: "Alpha" }[t] ?? t);
export const formatReleaseTime = (rt: string) => { if (!rt) return "Unbekannt"; const d = new Date(rt); return Number.isNaN(d.getTime()) ? rt : d.toLocaleDateString("de-DE"); };

// ── Auth internals ─────────────────────────────────────────────────────────
const stopAuthPolling = () => { if (authStatusInterval !== null) { window.clearInterval(authStatusInterval); authStatusInterval = null; } };
const resetAuthFlow = (cp = false) => { stopAuthPolling(); if (cp && authPopup && !authPopup.closed) authPopup.close(); authPopup = null; authStartedAt = 0; authState.value = ""; authWindowWasClosed.value = false; };
const completeAuthentication = (d: AuthData) => { authData.value = d; isAuthenticating.value = false; error.value = null; resetAuthFlow(true); };
const failAuthentication = (msg: string, cp = false) => { authData.value = null; isAuthenticating.value = false; error.value = msg; resetAuthFlow(cp); };

// ── API calls ──────────────────────────────────────────────────────────────
const loadSession = async () => {
  try {
    const r = await fetch("/api/session"); const d = (await r.json()) as SessionResponse;
    if (!d.success) { authData.value = null; error.value = d.error ?? "Session konnte nicht wiederhergestellt werden"; return; }
    if (d.authenticated && d.uuid && d.username) { authData.value = { uuid: d.uuid, username: d.username }; return; }
    authData.value = null;
  } catch (e) { authData.value = null; error.value = e instanceof Error ? e.message : "Session konnte nicht wiederhergestellt werden"; }
};

const loadInstances = async () => {
  try {
    isLoadingInstances.value = true;
    const r = await fetch("/api/instances"); const d = (await r.json()) as { success: boolean; instances?: LauncherInstance[]; error?: string };
    if (!d.success) { error.value = d.error ?? "Profile konnten nicht geladen werden"; return; }
    instances.value = d.instances ?? [];
    if (selectedInstanceName.value && instances.value.some((i) => i.name === selectedInstanceName.value)) return;
    selectedInstanceName.value = instances.value[0]?.name ?? "";
  } catch (e) { error.value = e instanceof Error ? e.message : "Profile konnten nicht geladen werden"; }
  finally { isLoadingInstances.value = false; }
};

const loadVersions = async () => {
  try {
    isLoadingVersions.value = true;
    const q = new URLSearchParams({ includeSnapshots: String(includeSnapshots.value), includeBetas: String(includeBetas.value), includeAlphas: String(includeAlphas.value) });
    const r = await fetch(`/api/instances/versions?${q}`); const d = (await r.json()) as { success: boolean; versions?: AvailableVersion[]; error?: string };
    if (!d.success) { error.value = d.error ?? "Versionen konnten nicht geladen werden"; return; }
    availableVersions.value = d.versions ?? [];
    if (selectedVersionId.value && availableVersions.value.some((v) => v.id === selectedVersionId.value)) return;
    selectedVersionId.value = availableVersions.value[0]?.id ?? "";
  } catch (e) { error.value = e instanceof Error ? e.message : "Versionen konnten nicht geladen werden"; }
  finally { isLoadingVersions.value = false; }
};

const pollAuthStatus = async () => {
  if (!authState.value) return;
  try {
    const r = await fetch(`/api/auth/status?state=${encodeURIComponent(authState.value)}`);
    const d = (await r.json()) as { success: boolean; status: string; uuid?: string; username?: string; error?: string };
    if (d.status === "pending") { if (authPopup?.closed) { authWindowWasClosed.value = true; if (Date.now() - authStartedAt > 10_000) failAuthentication("Das Login-Fenster wurde geschlossen."); } return; }
    if (d.status === "success" && d.uuid && d.username) { completeAuthentication({ uuid: d.uuid, username: d.username }); return; }
    failAuthentication(d.error ?? "Authentifizierung fehlgeschlagen", true);
  } catch (e) { failAuthentication(e instanceof Error ? e.message : "Authentifizierungsstatus konnte nicht geprüft werden", true); }
};

const startAuthPolling = () => { stopAuthPolling(); authStartedAt = Date.now(); authStatusInterval = window.setInterval(() => { void pollAuthStatus(); }, 1000); void pollAuthStatus(); };

const handleLogin = async () => {
  try {
    isAuthenticating.value = true; error.value = null; launcherMessage.value = null; authWindowWasClosed.value = false;
    const r = await fetch("/api/auth/login"); const d = (await r.json()) as { success: boolean; url?: string; state?: string; error?: string };
    if (!d.success || !d.url || !d.state) { error.value = d.error ?? "Authentifizierung fehlgeschlagen"; isAuthenticating.value = false; return; }
    authUrl.value = d.url; authState.value = d.state;
    authPopup = window.open(d.url, "Microsoft Login", "popup=yes,width=520,height=760");
    if (!authPopup) { failAuthentication("Das Login-Fenster konnte nicht geöffnet werden. Bitte Popups erlauben."); return; }
    authPopup.focus(); startAuthPolling();
  } catch (e) { failAuthentication(e instanceof Error ? e.message : "Unbekannter Fehler", true); }
};

const handleLogout = async () => {
  try { await fetch("/api/auth/logout", { method: "POST" }); } catch { /* ignore */ }
  authData.value = null; error.value = null; launcherMessage.value = null;
};

const handleCreateInstance = async () => {
  if (!newInstanceName.value.trim()) { error.value = "Bitte einen Profilnamen eingeben"; return; }
  if (!selectedVersionId.value) { error.value = "Bitte eine Minecraft-Version wählen"; return; }
  try {
    isCreatingInstance.value = true; error.value = null; launcherMessage.value = null;
    const r = await fetch("/api/instances", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: newInstanceName.value.trim(), versionId: selectedVersionId.value }) });
    const d = (await r.json()) as { success: boolean; instance?: LauncherInstance; error?: string };
    if (!d.success || !d.instance) { error.value = d.error ?? "Profil konnte nicht erstellt werden"; return; }
    launcherMessage.value = `Profil "${d.instance.name}" erfolgreich erstellt.`; newInstanceName.value = "";
    await loadInstances(); selectedInstanceName.value = d.instance.name; showCreateModal.value = false;
  } catch (e) { error.value = e instanceof Error ? e.message : "Profil konnte nicht erstellt werden"; }
  finally { isCreatingInstance.value = false; }
};

const handleLaunch = async () => {
  if (!authData.value || !selectedInstance.value) return;
  try {
    isLaunching.value = true; error.value = null; launcherMessage.value = null;
    const r = await fetch(`/api/instances/${encodeURIComponent(selectedInstance.value.name)}/launch`, { method: "POST" });
    const d = (await r.json()) as { success: boolean; instanceName?: string; version?: string; pid?: number; javaMajorVersion?: number; error?: string };
    if (!d.success) { error.value = d.error ?? "Minecraft konnte nicht gestartet werden"; return; }
    launcherMessage.value = `${d.instanceName ?? selectedInstance.value.name} gestartet${d.version ? ` mit ${d.version}` : ""}${d.javaMajorVersion ? ` auf Java ${d.javaMajorVersion}` : ""}${d.pid ? ` (PID ${d.pid})` : ""}.`;
    await loadInstances();
  } catch (e) { error.value = e instanceof Error ? e.message : "Minecraft konnte nicht gestartet werden"; }
  finally { isLaunching.value = false; }
};

const handleStop = async () => {
  if (!selectedInstance.value) return;
  try {
    isLaunching.value = true; error.value = null; launcherMessage.value = null;
    const r = await fetch(`/api/instances/${encodeURIComponent(selectedInstance.value.name)}/stop`, { method: "POST" });
    const d = (await r.json()) as { success: boolean; instanceName?: string; error?: string };
    if (!d.success) { error.value = d.error ?? "Profil konnte nicht gestoppt werden"; return; }
    launcherMessage.value = `${d.instanceName ?? selectedInstance.value.name} erfolgreich gestoppt.`;
    await loadInstances();
  } catch (e) { error.value = e instanceof Error ? e.message : "Profil konnte nicht gestoppt werden"; }
  finally { isLaunching.value = false; }
};

const handleImgError = (event: Event) => { const img = event.target as HTMLImageElement; const fb = img.dataset.fallbackSrc; if (fb && img.src !== fb) img.src = fb; };

const setAccentColor = (c: string) => { accentColor.value = c; };

// ── Lifecycle helpers ──────────────────────────────────────────────────────
function init() {
  void loadSession();
  void loadInstances();
  void loadVersions();
  instanceRefreshInterval = window.setInterval(() => { void loadInstances(); }, 3000);
  versionsWatchStop = watch([includeSnapshots, includeBetas, includeAlphas], () => { void loadVersions(); });
}

function cleanup() {
  resetAuthFlow(true);
  if (instanceRefreshInterval !== null) { window.clearInterval(instanceRefreshInterval); instanceRefreshInterval = null; }
  if (versionsWatchStop) { versionsWatchStop(); versionsWatchStop = null; }
}

// ── Public API ─────────────────────────────────────────────────────────────
export function useLauncher() {
  return {
    // Data state
    authData, instances, availableVersions,
    selectedInstanceName, newInstanceName, selectedVersionId,
    includeSnapshots, includeBetas, includeAlphas,
    isAuthenticating, isLaunching, isCreatingInstance, isLoadingInstances, isLoadingVersions,
    error, launcherMessage, authUrl, authState, authWindowWasClosed,
    // UI state
    activeTab, showCreateModal, profileFilter, discoverTabActive, settingsNavItem, accentColor, toggleStates,
    // Computed
    selectedInstance, runningInstancesCount, selectedVersion,
    playerName, playerSkinUrl, playerSkinFallback, playerAvatarUrl, playerAvatarFallback, filteredInstances,
    // Helpers
    versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime,
    // Actions
    handleLogin, handleLogout, handleCreateInstance, handleLaunch, handleStop,
    loadInstances, loadVersions, setAccentColor, handleImgError,
    // Lifecycle
    init, cleanup,
  };
}

