import { computed, ref, watch, type WatchStopHandle } from "vue";
import { apiGet, apiSend, buildQuery, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type {
  AvailableVersion,
  ContentEntry,
  ContentType,
  InstanceSettings,
  LauncherInstance,
  Platform,
} from "./types";

/** A profile whose creation is still running, shown as a placeholder card. */
export interface PendingInstance {
  id: string;
  name: string;
  versionId: string;
  platformId: string;
  failed: boolean;
  errorMessage?: string;
}

// ── State ─────────────────────────────────────────────────────────────────────

const instances = ref<LauncherInstance[]>([]);
const platforms = ref<Platform[]>([]);
const availableMinecraftVersions = ref<AvailableVersion[]>([]);
const loaderVersions = ref<AvailableVersion[]>([]);

const selectedInstanceName = ref("");
const newInstanceName = ref("");
const selectedPlatformId = ref("vanilla");
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

// ── Computed ──────────────────────────────────────────────────────────────────

const selectedInstance = computed(
  () => instances.value.find((instance) => instance.name === selectedInstanceName.value) ?? null,
);
const runningInstancesCount = computed(() => instances.value.filter((instance) => instance.running).length);
const requiresLoaderSelection = computed(() => selectedPlatformId.value !== "vanilla");
const availableVersions = computed(() =>
  requiresLoaderSelection.value ? loaderVersions.value : availableMinecraftVersions.value,
);
const selectedVersionId = computed(() =>
  requiresLoaderSelection.value ? selectedLoaderVersionId.value : selectedMinecraftVersionId.value,
);
const selectedVersion = computed(
  () => availableVersions.value.find((version) => version.id === selectedVersionId.value) ?? null,
);

const filteredInstances = computed(() => {
  const pendingNames = new Set(
    pendingInstances.value.filter((pending) => !pending.failed).map((pending) => pending.name.toLowerCase()),
  );
  const visible = instances.value.filter((instance) => !pendingNames.has(instance.name.toLowerCase()));
  if (profileFilter.value === "ALL") return visible;
  return visible.filter((instance) =>
    instance.versionType.toLowerCase().includes(profileFilter.value.toLowerCase()),
  );
});

// ── Data loading ──────────────────────────────────────────────────────────────

const loadPlatforms = async () => {
  try {
    const response = await apiGet<{ success: boolean; platforms: Platform[] }>("/api/platforms");
    platforms.value = response.platforms;
  } catch (e) {
    error.value = errorMessage(e, "Could not load the mod loaders");
  }
};

const loadInstances = async () => {
  try {
    isLoadingInstances.value = true;
    const response = await apiGet<{ success: boolean; instances: LauncherInstance[] }>("/api/instances");
    instances.value = response.instances;

    const stillExists = instances.value.some((instance) => instance.name === selectedInstanceName.value);
    if (!selectedInstanceName.value || !stillExists) {
      selectedInstanceName.value = instances.value[0]?.name ?? "";
    }
  } catch (e) {
    error.value = errorMessage(e, "Could not load your profiles");
  } finally {
    isLoadingInstances.value = false;
  }
};

const loadVersions = async () => {
  try {
    isLoadingVersions.value = true;
    const response = await apiGet<{ success: boolean; versions: AvailableVersion[] }>(
      `/api/instances/versions${buildQuery({
        includeSnapshots: includeSnapshots.value,
        includeBetas: includeBetas.value,
        includeAlphas: includeAlphas.value,
      })}`,
    );
    availableMinecraftVersions.value = response.versions;

    const stillListed = availableMinecraftVersions.value.some(
      (version) => version.id === selectedMinecraftVersionId.value,
    );
    if (!selectedMinecraftVersionId.value || !stillListed) {
      selectedMinecraftVersionId.value = availableMinecraftVersions.value[0]?.id ?? "";
    }
  } catch (e) {
    error.value = errorMessage(e, "Could not load the Minecraft versions");
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
    const response = await apiGet<{ success: boolean; versions: AvailableVersion[] }>(
      `/api/instances/loader-versions${buildQuery({
        platformId: selectedPlatformId.value,
        minecraftVersionId: selectedMinecraftVersionId.value,
      })}`,
    );
    loaderVersions.value = response.versions;

    const stillListed = loaderVersions.value.some((version) => version.id === selectedLoaderVersionId.value);
    if (!selectedLoaderVersionId.value || !stillListed) {
      selectedLoaderVersionId.value = loaderVersions.value[0]?.id ?? "";
    }
  } catch (e) {
    // A loader with no build for this Minecraft version is a normal outcome, not a launcher fault.
    loaderVersions.value = [];
    selectedLoaderVersionId.value = "";
    error.value = errorMessage(e, "Could not load the loader versions");
  } finally {
    isLoadingLoaderVersions.value = false;
  }
};

// ── Profile actions ───────────────────────────────────────────────────────────

const markPendingFailed = (pendingId: string, message: string) => {
  const pending = pendingInstances.value.find((entry) => entry.id === pendingId);
  if (pending) {
    pending.failed = true;
    pending.errorMessage = message;
  }
};

const dismissPending = (pendingId: string) => {
  pendingInstances.value = pendingInstances.value.filter((pending) => pending.id !== pendingId);
};

const handleCreateInstance = async () => {
  if (!newInstanceName.value.trim()) {
    error.value = t("profiles.nameRequired");
    return;
  }
  if (!selectedMinecraftVersionId.value) {
    error.value = t("profiles.versionRequired");
    return;
  }
  if (requiresLoaderSelection.value && !selectedLoaderVersionId.value) {
    error.value = t("profiles.loaderRequired");
    return;
  }

  const name = newInstanceName.value.trim();
  const versionId = selectedVersionId.value;
  const platformId = selectedPlatformId.value;

  // Close the dialog straight away — installing takes a while and the card shows progress.
  newInstanceName.value = "";
  showCreateModal.value = false;
  error.value = null;

  const pendingId = `${name}-${Date.now()}`;
  pendingInstances.value.push({ id: pendingId, name, versionId, platformId, failed: false });

  try {
    isCreatingInstance.value = true;
    const response = await apiSend<{ success: boolean; instance: LauncherInstance }>("POST", "/api/instances", {
      name,
      versionId,
    });
    launcherMessage.value = t("profiles.created_toast", { name: response.instance.name });
    dismissPending(pendingId);
    await loadInstances();
    selectedInstanceName.value = response.instance.name;
  } catch (e) {
    markPendingFailed(pendingId, errorMessage(e, t("profiles.createFailed")));
  } finally {
    isCreatingInstance.value = false;
  }
};

const handleDeleteInstance = async (name: string) => {
  try {
    await apiSend("DELETE", `/api/instances/${encodeURIComponent(name)}`);
    launcherMessage.value = t("profiles.deleted_toast", { name });
    if (selectedInstanceName.value === name) selectedInstanceName.value = "";
    showDeleteModal.value = false;
    deleteTargetInstance.value = null;
    await loadInstances();
  } catch (e) {
    error.value = errorMessage(e, t("profiles.deleteFailed"));
  }
};

const handleRenameInstance = async (name: string, newName: string) => {
  if (!newName.trim()) {
    error.value = t("profiles.nameRequired");
    return;
  }
  try {
    const response = await apiSend<{ success: boolean; instance: LauncherInstance }>(
      "PATCH",
      `/api/instances/${encodeURIComponent(name)}`,
      { name: newName.trim() },
    );
    launcherMessage.value = t("profiles.renamed_toast", { name: response.instance.name });
    if (selectedInstanceName.value === name) selectedInstanceName.value = response.instance.name;
    showEditModal.value = false;
    editTargetInstance.value = null;
    editNewName.value = "";
    await loadInstances();
  } catch (e) {
    error.value = errorMessage(e, t("profiles.renameFailed"));
  }
};

const handleUpdateInstanceSettings = async (
  name: string,
  settings: InstanceSettings,
): Promise<boolean> => {
  try {
    const response = await apiSend<{ success: boolean; instance: LauncherInstance }>(
      "PATCH",
      `/api/instances/${encodeURIComponent(name)}/settings`,
      settings,
    );
    const index = instances.value.findIndex((instance) => instance.name === name);
    if (index !== -1) instances.value[index] = response.instance;
    launcherMessage.value = t("common.saved");
    return true;
  } catch (e) {
    error.value = errorMessage(e, t("settings.saveFailed"));
    return false;
  }
};

const handleOpenInstanceFolder = async (name: string) => {
  try {
    await apiSend("POST", `/api/instances/${encodeURIComponent(name)}/open-folder`);
  } catch (e) {
    error.value = errorMessage(e, "Could not open the folder");
  }
};

// ── Instance content ──────────────────────────────────────────────────────────

const loadInstanceContent = async (name: string, type: ContentType): Promise<ContentEntry[]> => {
  try {
    const response = await apiGet<{ success: boolean; items: ContentEntry[] }>(
      `/api/instances/${encodeURIComponent(name)}/content/${type}`,
    );
    return response.items;
  } catch (e) {
    error.value = errorMessage(e, t("content.loadFailed"));
    return [];
  }
};

const addInstanceContent = async (name: string, type: ContentType, paths: string[]): Promise<boolean> => {
  try {
    const response = await apiSend<{ success: boolean; added: ContentEntry[]; failures: string[] }>(
      "POST",
      `/api/instances/${encodeURIComponent(name)}/content/${type}`,
      { paths },
    );
    if (response.failures.length > 0) error.value = response.failures.join("; ");
    return response.added.length > 0;
  } catch (e) {
    error.value = errorMessage(e, t("content.addFailed"));
    return false;
  }
};

const removeInstanceContent = async (name: string, type: ContentType, fileName: string): Promise<boolean> => {
  try {
    await apiSend("DELETE", `/api/instances/${encodeURIComponent(name)}/content/${type}/${encodeURIComponent(fileName)}`);
    return true;
  } catch (e) {
    error.value = errorMessage(e, t("content.removeFailed"));
    return false;
  }
};

const toggleInstanceContent = async (
  name: string,
  type: ContentType,
  fileName: string,
): Promise<ContentEntry | null> => {
  try {
    const response = await apiSend<{ success: boolean; item: ContentEntry }>(
      "POST",
      `/api/instances/${encodeURIComponent(name)}/content/${type}/${encodeURIComponent(fileName)}/toggle`,
    );
    return response.item;
  } catch (e) {
    error.value = errorMessage(e, t("content.toggleFailed"));
    return null;
  }
};

// ── Watchers ──────────────────────────────────────────────────────────────────

let versionsUnwatch: WatchStopHandle | null = null;
let platformUnwatch: WatchStopHandle | null = null;
let minecraftVersionUnwatch: WatchStopHandle | null = null;

export function setupInstanceWatchers() {
  teardownInstanceWatchers();

  versionsUnwatch = watch([includeSnapshots, includeBetas, includeAlphas], () => void loadVersions());

  platformUnwatch = watch(selectedPlatformId, () => {
    if (!requiresLoaderSelection.value) {
      loaderVersions.value = [];
      selectedLoaderVersionId.value = "";
      return;
    }
    void loadLoaderVersions();
  });

  minecraftVersionUnwatch = watch(selectedMinecraftVersionId, () => {
    if (requiresLoaderSelection.value) void loadLoaderVersions();
  });
}

export function teardownInstanceWatchers() {
  versionsUnwatch?.();
  platformUnwatch?.();
  minecraftVersionUnwatch?.();
  versionsUnwatch = null;
  platformUnwatch = null;
  minecraftVersionUnwatch = null;
}

export function useInstances() {
  return {
    instances,
    platforms,
    availableMinecraftVersions,
    loaderVersions,
    availableVersions,
    selectedInstanceName,
    newInstanceName,
    selectedVersionId,
    selectedPlatformId,
    selectedMinecraftVersionId,
    selectedLoaderVersionId,
    includeSnapshots,
    includeBetas,
    includeAlphas,
    isCreatingInstance,
    isLoadingInstances,
    isLoadingVersions,
    isLoadingLoaderVersions,
    profileFilter,
    pendingInstances,
    showCreateModal,
    showEditModal,
    showDeleteModal,
    editTargetInstance,
    editNewName,
    deleteTargetInstance,
    selectedInstance,
    runningInstancesCount,
    requiresLoaderSelection,
    selectedVersion,
    filteredInstances,

    loadPlatforms,
    loadInstances,
    loadVersions,
    loadLoaderVersions,
    dismissPending,

    handleCreateInstance,
    handleDeleteInstance,
    handleRenameInstance,
    handleOpenInstanceFolder,
    handleUpdateInstanceSettings,

    loadInstanceContent,
    addInstanceContent,
    removeInstanceContent,
    toggleInstanceContent,
  };
}
