import { computed, ref, watch, type WatchStopHandle } from "vue";
import { apiFetch } from "./api";
import { error, launcherMessage } from "./state";
import type { AvailableVersion, ContentEntry, ContentType, InstanceSettings, LauncherInstance, PlatformId } from "./types";

export interface PendingInstance {
    id: string;
    name: string;
    versionId: string;
    platformId: PlatformId;
    failed: boolean;
    errorMessage?: string;
}

// ── State ─────────────────────────────────────────────────────────────────────

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
const isCreatingInstance = ref(false);
const isLoadingInstances = ref(false);
const isLoadingVersions = ref(false);
const isLoadingLoaderVersions = ref(false);
const profileFilter = ref("ALL");
const pendingInstances = ref<PendingInstance[]>([]);

const showCreateModal = ref(false);
const showEditModal = ref(false);
const showDeleteModal = ref(false);
const editTargetInstance = ref<LauncherInstance | null>(null);
const editNewName = ref("");
const deleteTargetInstance = ref<LauncherInstance | null>(null);

const platformOptions = ref<Array<{ value: PlatformId; label: string }>>([
    { value: "vanilla", label: "Vanilla" },
    { value: "fabric", label: "Fabric" },
    { value: "forge", label: "Forge" },
    { value: "neoforge", label: "NeoForge" },
    { value: "quilt", label: "Quilt" },
]);

// ── Computed ──────────────────────────────────────────────────────────────────

const selectedInstance = computed(() =>
    instances.value.find(i => i.name === selectedInstanceName.value) ?? null
);
const runningInstancesCount = computed(() => instances.value.filter(i => i.running).length);
const requiresLoaderSelection = computed(() => selectedPlatformId.value !== "vanilla");
const availableVersions = computed(() =>
    requiresLoaderSelection.value ? loaderVersions.value : availableMinecraftVersions.value
);
const selectedVersionId = computed(() =>
    requiresLoaderSelection.value ? selectedLoaderVersionId.value : selectedMinecraftVersionId.value
);
const selectedVersion = computed(() =>
    availableVersions.value.find(v => v.id === selectedVersionId.value) ?? null
);
const filteredInstances = computed(() => {
    const pendingNames = new Set(pendingInstances.value.filter(p => !p.failed).map(p => p.name.toLowerCase()));
    const visible = instances.value.filter(i => !pendingNames.has(i.name.toLowerCase()));
    return profileFilter.value === "ALL"
        ? visible
        : visible.filter(i => i.versionType.toLowerCase().includes(profileFilter.value.toLowerCase()));
});

// ── Watch handles (managed by init/cleanup in useLauncher) ────────────────────

export let versionsUnwatch: WatchStopHandle | null = null;
export let platformUnwatch: WatchStopHandle | null = null;
export let minecraftVersionUnwatch: WatchStopHandle | null = null;

// ── Data loading ──────────────────────────────────────────────────────────────

const loadInstances = async () => {
    try {
        isLoadingInstances.value = true;
        const d = await apiFetch<{ success: boolean; instances?: LauncherInstance[]; error?: string }>("/api/instances");
        if (!d.success) { error.value = d.error ?? "Profile konnten nicht geladen werden"; return; }
        instances.value = d.instances ?? [];
        if (!selectedInstanceName.value || !instances.value.some(i => i.name === selectedInstanceName.value))
            selectedInstanceName.value = instances.value[0]?.name ?? "";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profile konnten nicht geladen werden";
    } finally {
        isLoadingInstances.value = false;
    }
};

const loadVersions = async () => {
    try {
        isLoadingVersions.value = true;
        const q = new URLSearchParams({
            includeSnapshots: String(includeSnapshots.value),
            includeBetas: String(includeBetas.value),
            includeAlphas: String(includeAlphas.value),
        });
        const d = await apiFetch<{ success: boolean; versions?: AvailableVersion[]; error?: string }>(
            `/api/instances/versions?${q}`
        );
        if (!d.success) { error.value = d.error ?? "Versionen konnten nicht geladen werden"; return; }
        availableMinecraftVersions.value = d.versions ?? [];
        if (!selectedMinecraftVersionId.value || !availableMinecraftVersions.value.some(v => v.id === selectedMinecraftVersionId.value))
            selectedMinecraftVersionId.value = availableMinecraftVersions.value[0]?.id ?? "";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Versionen konnten nicht geladen werden";
    } finally {
        isLoadingVersions.value = false;
    }
};

const loadLoaderVersions = async () => {
    if (!requiresLoaderSelection.value || !selectedMinecraftVersionId.value) {
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
        const d = await apiFetch<{ success: boolean; versions?: AvailableVersion[]; error?: string }>(
            `/api/instances/loader-versions?${q}`
        );
        if (!d.success) { error.value = d.error ?? "Loader-Versionen konnten nicht geladen werden"; return; }
        loaderVersions.value = d.versions ?? [];
        if (!selectedLoaderVersionId.value || !loaderVersions.value.some(v => v.id === selectedLoaderVersionId.value))
            selectedLoaderVersionId.value = loaderVersions.value[0]?.id ?? "";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Loader-Versionen konnten nicht geladen werden";
    } finally {
        isLoadingLoaderVersions.value = false;
    }
};

// ── CRUD actions ──────────────────────────────────────────────────────────────

const handleCreateInstance = async () => {
    if (!newInstanceName.value.trim()) { error.value = "Bitte einen Profilnamen eingeben"; return; }
    if (!selectedMinecraftVersionId.value) { error.value = "Bitte eine Minecraft-Version wählen"; return; }
    if (requiresLoaderSelection.value && !selectedLoaderVersionId.value) {
        error.value = "Bitte eine Loader-Version wählen";
        return;
    }

    const name = newInstanceName.value.trim();
    const versionId = selectedVersionId.value;
    const platformId = selectedPlatformId.value;

    // Close the modal and reset the form immediately.
    newInstanceName.value = "";
    showCreateModal.value = false;
    error.value = null;

    const pendingId = `${name}-${Date.now()}`;
    pendingInstances.value.push({ id: pendingId, name, versionId, platformId, failed: false });

    try {
        isCreatingInstance.value = true;
        const d = await apiFetch<{ success: boolean; instance?: LauncherInstance; error?: string }>("/api/instances", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, versionId }),
        });
        if (!d.success || !d.instance) {
            const idx = pendingInstances.value.findIndex(p => p.id === pendingId);
            if (idx !== -1) pendingInstances.value[idx] = { ...pendingInstances.value[idx], failed: true, errorMessage: d.error ?? "Erstellung fehlgeschlagen" };
            return;
        }
        launcherMessage.value = `Profil "${d.instance.name}" erfolgreich erstellt.`;
        pendingInstances.value = pendingInstances.value.filter(p => p.id !== pendingId);
        await loadInstances();
        selectedInstanceName.value = d.instance.name;
    } catch (e) {
        const idx = pendingInstances.value.findIndex(p => p.id === pendingId);
        if (idx !== -1) pendingInstances.value[idx] = { ...pendingInstances.value[idx], failed: true, errorMessage: e instanceof Error ? e.message : "Erstellung fehlgeschlagen" };
    } finally {
        isCreatingInstance.value = false;
    }
};

const handleDeleteInstance = async (name: string) => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}`,
            { method: "DELETE" }
        );
        if (!d.success) { error.value = d.error ?? "Profil konnte nicht gelöscht werden"; return; }
        launcherMessage.value = `Profil "${name}" wurde gelöscht.`;
        if (selectedInstanceName.value === name) selectedInstanceName.value = "";
        showDeleteModal.value = false;
        deleteTargetInstance.value = null;
        await loadInstances();
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profil konnte nicht gelöscht werden";
    }
};

const handleRenameInstance = async (name: string, newName: string) => {
    if (!newName.trim()) { error.value = "Bitte einen Profilnamen eingeben"; return; }
    try {
        const d = await apiFetch<{ success: boolean; instance?: LauncherInstance; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}`,
            { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name: newName.trim() }) }
        );
        if (!d.success || !d.instance) { error.value = d.error ?? "Profil konnte nicht umbenannt werden"; return; }
        launcherMessage.value = `Profil umbenannt zu "${d.instance.name}".`;
        if (selectedInstanceName.value === name) selectedInstanceName.value = d.instance.name;
        showEditModal.value = false;
        editTargetInstance.value = null;
        editNewName.value = "";
        await loadInstances();
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Profil konnte nicht umbenannt werden";
    }
};

const handleInstallModrinthPack = async (modrinthVersionId: string, name: string) => {
    const pendingId = `modrinth-${modrinthVersionId}-${Date.now()}`;
    const displayName = name.trim() || "Modpack";
    pendingInstances.value.push({ id: pendingId, name: displayName, versionId: modrinthVersionId, platformId: "fabric", failed: false });

    let jobId: string | null = null;
    try {
        const d = await apiFetch<{ success: boolean; jobId?: string; error?: string }>("/api/instances/from-modrinth", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: name.trim(), modrinthVersionId }),
        });
        if (!d.success || !d.jobId) {
            const idx = pendingInstances.value.findIndex(p => p.id === pendingId);
            if (idx !== -1) pendingInstances.value[idx] = { ...pendingInstances.value[idx], failed: true, errorMessage: d.error ?? "Installation fehlgeschlagen" };
            return null;
        }
        jobId = d.jobId;
    } catch (e) {
        const idx = pendingInstances.value.findIndex(p => p.id === pendingId);
        if (idx !== -1) pendingInstances.value[idx] = { ...pendingInstances.value[idx], failed: true, errorMessage: e instanceof Error ? e.message : "Installation fehlgeschlagen" };
        return null;
    }

    return new Promise<LauncherInstance | null>((resolve) => {
        const poll = window.setInterval(async () => {
            try {
                const s = await apiFetch<{
                    success: boolean; phase?: string; message?: string;
                    instance?: LauncherInstance; error?: string;
                }>(`/api/instances/from-modrinth/status?jobId=${encodeURIComponent(jobId!)}`);

                if (!s.success || s.phase === "failed") {
                    window.clearInterval(poll);
                    const idx = pendingInstances.value.findIndex(p => p.id === pendingId);
                    if (idx !== -1) pendingInstances.value[idx] = { ...pendingInstances.value[idx], failed: true, errorMessage: s.error ?? s.message ?? "Installation fehlgeschlagen" };
                    resolve(null);
                    return;
                }
                if (s.phase === "done" && s.instance) {
                    window.clearInterval(poll);
                    launcherMessage.value = `Modpack "${s.instance.name}" erfolgreich installiert.`;
                    pendingInstances.value = pendingInstances.value.filter(p => p.id !== pendingId);
                    await loadInstances();
                    selectedInstanceName.value = s.instance.name;
                    resolve(s.instance);
                }
            } catch {
                // Keep polling on transient errors
            }
        }, 2000);
    });
};

const handleUpdateInstanceSettings = async (name: string, settings: InstanceSettings): Promise<boolean> => {
    try {
        const d = await apiFetch<{ success: boolean; instance?: LauncherInstance; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/settings`,
            { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(settings) }
        );
        if (!d.success || !d.instance) { error.value = d.error ?? "Einstellungen konnten nicht gespeichert werden"; return false; }
        const idx = instances.value.findIndex(i => i.name === name);
        if (idx !== -1) instances.value[idx] = d.instance;
        launcherMessage.value = `Einstellungen für "${name}" gespeichert.`;
        return true;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Einstellungen konnten nicht gespeichert werden";
        return false;
    }
};

const loadInstanceContent = async (name: string, type: ContentType): Promise<ContentEntry[]> => {
    try {
        const d = await apiFetch<{ success: boolean; items?: ContentEntry[]; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/content/${type}`
        );
        if (!d.success) { error.value = d.error ?? "Inhalte konnten nicht geladen werden"; return []; }
        return d.items ?? [];
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Inhalte konnten nicht geladen werden";
        return [];
    }
};

const addInstanceContent = async (name: string, type: ContentType, paths: string[]): Promise<boolean> => {
    try {
        const d = await apiFetch<{ success: boolean; added?: ContentEntry[]; failures?: string[]; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/content/${type}`,
            { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ paths }) }
        );
        if (!d.success) { error.value = d.error ?? "Datei konnte nicht hinzugefügt werden"; return false; }
        if (d.failures && d.failures.length > 0) error.value = d.failures.join("; ");
        return (d.added?.length ?? 0) > 0;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Datei konnte nicht hinzugefügt werden";
        return false;
    }
};

const removeInstanceContent = async (name: string, type: ContentType, fileName: string): Promise<boolean> => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/content/${type}/${encodeURIComponent(fileName)}`,
            { method: "DELETE" }
        );
        if (!d.success) { error.value = d.error ?? "Datei konnte nicht gelöscht werden"; return false; }
        return true;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Datei konnte nicht gelöscht werden";
        return false;
    }
};

const toggleInstanceContent = async (name: string, type: ContentType, fileName: string): Promise<ContentEntry | null> => {
    try {
        const d = await apiFetch<{ success: boolean; item?: ContentEntry; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/content/${type}/${encodeURIComponent(fileName)}/toggle`,
            { method: "POST" }
        );
        if (!d.success || !d.item) { error.value = d.error ?? "Status konnte nicht geändert werden"; return null; }
        return d.item;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Status konnte nicht geändert werden";
        return null;
    }
};

const handleOpenInstanceFolder = async (name: string) => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/instances/${encodeURIComponent(name)}/open-folder`,
            { method: "POST" }
        );
        if (!d.success) error.value = d.error ?? "Ordner konnte nicht geöffnet werden";
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Ordner konnte nicht geöffnet werden";
    }
};

// ── Watch setup (called from useLauncher init) ────────────────────────────────

export function setupInstanceWatchers() {
    versionsUnwatch?.();
    platformUnwatch?.();
    minecraftVersionUnwatch?.();

    versionsUnwatch = watch([includeSnapshots, includeBetas, includeAlphas], () => { void loadVersions(); });

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

export function teardownInstanceWatchers() {
    versionsUnwatch?.(); versionsUnwatch = null;
    platformUnwatch?.(); platformUnwatch = null;
    minecraftVersionUnwatch?.(); minecraftVersionUnwatch = null;
}

export function useInstances() {
    return {
        instances, availableMinecraftVersions, loaderVersions, availableVersions,
        selectedInstanceName, newInstanceName, selectedVersionId,
        selectedPlatformId, selectedMinecraftVersionId, selectedLoaderVersionId,
        includeSnapshots, includeBetas, includeAlphas,
        isCreatingInstance, isLoadingInstances, isLoadingVersions, isLoadingLoaderVersions,
        profileFilter, platformOptions, pendingInstances,
        showCreateModal, showEditModal, showDeleteModal,
        editTargetInstance, editNewName, deleteTargetInstance,
        selectedInstance, runningInstancesCount, requiresLoaderSelection,
        selectedVersion, filteredInstances,
        loadInstances, loadVersions, loadLoaderVersions,
        handleCreateInstance, handleDeleteInstance, handleRenameInstance, handleOpenInstanceFolder, handleInstallModrinthPack,
        handleUpdateInstanceSettings, loadInstanceContent, addInstanceContent, removeInstanceContent, toggleInstanceContent,
    };
}
