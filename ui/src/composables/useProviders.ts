import { computed, ref, watch } from "vue";
import { apiGet, apiSend, buildQuery, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type {
  ContentKind,
  ProjectDetail,
  ProjectSummary,
  ProjectVersion,
  ProviderId,
  ProviderInfo,
  SearchResult,
  SortIndex,
} from "./types";

/**
 * Discovery and installation, routed through the launcher backend.
 *
 * The UI never talks to Modrinth or CurseForge directly: the backend normalises both onto one
 * model, which is also what lets the CurseForge bridge hold the API key.
 */

// ── Providers ─────────────────────────────────────────────────────────────────

const providers = ref<ProviderInfo[]>([]);
const isLoadingProviders = ref(false);

const loadProviders = async () => {
  try {
    isLoadingProviders.value = true;
    const response = await apiGet<{ success: boolean; providers: ProviderInfo[] }>("/api/providers");
    providers.value = response.providers;
  } catch (e) {
    error.value = errorMessage(e, "Could not load the content providers");
  } finally {
    isLoadingProviders.value = false;
  }
};

const providerById = (id: string) => providers.value.find((provider) => provider.id === id) ?? null;

// ── Search ────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 20;

const query = ref("");
const kind = ref<ContentKind>("modpack");
const provider = ref<ProviderId>("modrinth");
const sort = ref<SortIndex>("relevance");
const gameVersionFilter = ref<string>("");
const loaderFilter = ref<string>("");
const categoryFilters = ref<string[]>([]);

const hits = ref<ProjectSummary[]>([]);
const totalHits = ref(0);
const isSearching = ref(false);
const searchError = ref<string | null>(null);

const hasMore = computed(() => hits.value.length < totalHits.value);
const activeProvider = computed(() => providerById(provider.value));

let searchToken = 0;
let debounceTimer: ReturnType<typeof setTimeout> | undefined;

const runSearch = async (append = false) => {
  const token = ++searchToken;
  isSearching.value = true;
  searchError.value = null;

  const path =
    `/api/providers/${encodeURIComponent(provider.value)}/search` +
    buildQuery({
      query: query.value.trim(),
      kind: kind.value,
      sort: sort.value,
      gameVersion: gameVersionFilter.value,
      loader: loaderFilter.value,
      categories: categoryFilters.value.join(","),
      offset: append ? hits.value.length : 0,
      limit: PAGE_SIZE,
    });

  try {
    const response = await apiGet<SearchResult & { success: boolean }>(path);
    // A slower earlier request must not overwrite a newer result set.
    if (token !== searchToken) return;
    hits.value = append ? [...hits.value, ...response.hits] : response.hits;
    totalHits.value = response.total;
  } catch (e) {
    if (token !== searchToken) return;
    searchError.value = errorMessage(e, "Search failed");
    if (!append) {
      hits.value = [];
      totalHits.value = 0;
    }
  } finally {
    if (token === searchToken) isSearching.value = false;
  }
};

const search = () => runSearch(false);

const loadMore = () => {
  if (isSearching.value || !hasMore.value) return;
  void runSearch(true);
};

const clearFilters = () => {
  gameVersionFilter.value = "";
  loaderFilter.value = "";
  categoryFilters.value = [];
};

const toggleCategory = (category: string) => {
  const index = categoryFilters.value.indexOf(category);
  if (index === -1) categoryFilters.value.push(category);
  else categoryFilters.value.splice(index, 1);
};

// Filter changes re-query immediately; typing is debounced.
watch([provider, kind, sort, gameVersionFilter, loaderFilter, categoryFilters], () => void runSearch(false), {
  deep: true,
});

watch(query, () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => void runSearch(false), 300);
});

// ── Project detail ────────────────────────────────────────────────────────────

const project = ref<ProjectDetail | null>(null);
const projectVersions = ref<ProjectVersion[]>([]);
const isLoadingProject = ref(false);
const projectError = ref<string | null>(null);

const openProject = async (summary: ProjectSummary) => {
  isLoadingProject.value = true;
  projectError.value = null;
  project.value = null;
  projectVersions.value = [];

  try {
    const detail = await apiGet<{ success: boolean; project: ProjectDetail }>(
      `/api/providers/${encodeURIComponent(summary.provider)}/projects/${encodeURIComponent(summary.projectId)}`,
    );
    project.value = detail.project;
    await loadProjectVersions(summary.provider, summary.projectId);
  } catch (e) {
    projectError.value = errorMessage(e, "Could not load this project");
  } finally {
    isLoadingProject.value = false;
  }
};

const loadProjectVersions = async (
  providerId: ProviderId,
  projectId: string,
  gameVersion?: string,
  loader?: string,
) => {
  try {
    const response = await apiGet<{ success: boolean; versions: ProjectVersion[] }>(
      `/api/providers/${encodeURIComponent(providerId)}/projects/${encodeURIComponent(projectId)}/versions` +
        buildQuery({ gameVersion, loader }),
    );
    projectVersions.value = response.versions;
    return response.versions;
  } catch (e) {
    projectError.value = errorMessage(e, "Could not load the version list");
    return [];
  }
};

const closeProject = () => {
  project.value = null;
  projectVersions.value = [];
  projectError.value = null;
};

// ── Installing into a profile ─────────────────────────────────────────────────

const isInstalling = ref(false);

export interface InstallOutcome {
  installed: number;
  skipped: string[];
}

const installIntoProfile = async (
  instanceName: string,
  providerId: ProviderId,
  versionId: string,
  contentKind: ContentKind,
  withDependencies = true,
): Promise<InstallOutcome | null> => {
  try {
    isInstalling.value = true;
    const response = await apiSend<{
      success: boolean;
      installed: Array<{ fileName: string; dependency: boolean }>;
      skipped: string[];
    }>("POST", `/api/instances/${encodeURIComponent(instanceName)}/install`, {
      provider: providerId,
      versionId,
      kind: contentKind,
      withDependencies,
    });
    return { installed: response.installed.length, skipped: response.skipped };
  } catch (e) {
    error.value = errorMessage(e, t("install.failed"));
    return null;
  } finally {
    isInstalling.value = false;
  }
};

// ── Installing a modpack as a new profile ─────────────────────────────────────

export type ModpackPhase = "fetching" | "installing" | "downloading_content" | "done" | "failed";

export interface ModpackProgress {
  phase: ModpackPhase;
  message: string | null;
  instanceName: string | null;
}

const modpackProgress = ref<ModpackProgress | null>(null);

/**
 * Starts a modpack installation and resolves once it finishes. Progress is polled because the
 * install can run for several minutes — far longer than a single HTTP request should be held open.
 */
const installModpack = async (
  providerId: ProviderId,
  versionId: string,
  name = "",
): Promise<string | null> => {
  let jobId: string;
  try {
    const response = await apiSend<{ success: boolean; jobId: string }>("POST", "/api/modpacks/install", {
      provider: providerId,
      versionId,
      name,
    });
    jobId = response.jobId;
  } catch (e) {
    error.value = errorMessage(e, t("install.packFailed"));
    return null;
  }

  modpackProgress.value = { phase: "fetching", message: t("install.packInstalling"), instanceName: null };

  return new Promise<string | null>((resolve) => {
    const poll = window.setInterval(async () => {
      try {
        const status = await apiGet<{
          success: boolean;
          phase: ModpackPhase;
          message: string | null;
          instance?: { name: string };
        }>(`/api/modpacks/install/${encodeURIComponent(jobId)}`);

        modpackProgress.value = {
          phase: status.phase,
          message: status.message,
          instanceName: status.instance?.name ?? null,
        };

        if (status.phase === "done") {
          window.clearInterval(poll);
          const instanceName = status.instance?.name ?? name;
          launcherMessage.value = t("install.packSuccess", { name: instanceName });
          modpackProgress.value = null;
          void apiSend("DELETE", `/api/modpacks/install/${encodeURIComponent(jobId)}`).catch(() => {});
          resolve(instanceName);
        } else if (status.phase === "failed") {
          window.clearInterval(poll);
          error.value = status.message ?? t("install.packFailed");
          modpackProgress.value = null;
          void apiSend("DELETE", `/api/modpacks/install/${encodeURIComponent(jobId)}`).catch(() => {});
          resolve(null);
        }
      } catch {
        // Transient polling failures are expected while large downloads saturate the connection.
      }
    }, 1500);
  });
};

// ── Formatting helpers ────────────────────────────────────────────────────────

export const formatDownloads = (count: number): string => {
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1)}M`;
  if (count >= 1_000) return `${(count / 1_000).toFixed(1)}K`;
  return String(count);
};

export const formatFileSize = (bytes: number): string => {
  if (bytes >= 1_048_576) return `${(bytes / 1_048_576).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
};

export function useProviders() {
  return {
    providers,
    isLoadingProviders,
    loadProviders,
    providerById,

    query,
    kind,
    provider,
    sort,
    gameVersionFilter,
    loaderFilter,
    categoryFilters,
    hits,
    totalHits,
    isSearching,
    searchError,
    hasMore,
    activeProvider,
    search,
    loadMore,
    clearFilters,
    toggleCategory,

    project,
    projectVersions,
    isLoadingProject,
    projectError,
    openProject,
    loadProjectVersions,
    closeProject,

    isInstalling,
    installIntoProfile,
    installModpack,
    modpackProgress,

    formatDownloads,
    formatFileSize,
  };
}
