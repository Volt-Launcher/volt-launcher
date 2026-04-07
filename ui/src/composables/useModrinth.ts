import { ref, computed, watch, type Ref } from "vue";

// ── Types matching Modrinth API v2 /search response ──────────────────────────

export interface ModrinthHit {
  project_id: string;
  project_type: string;
  slug: string;
  author: string;
  title: string;
  description: string;
  categories: string[];
  display_categories: string[];
  versions: string[];
  downloads: number;
  follows: number;
  icon_url: string;
  date_created: string;
  date_modified: string;
  latest_version: string;
  license: string;
  client_side: "required" | "optional" | "unsupported";
  server_side: "required" | "optional" | "unsupported";
  gallery: string[];
  featured_gallery: string | null;
  color: number | null;
}

export interface ModrinthSearchResponse {
  hits: ModrinthHit[];
  offset: number;
  limit: number;
  total_hits: number;
}

// ── Full project detail (GET /v2/project/{id|slug}) ─────────────────────────

export interface ModrinthGalleryImage {
  url: string;
  featured: boolean;
  title: string | null;
  description: string | null;
  created: string;
  ordering: number;
}

export interface ModrinthProject {
  id: string;
  slug: string;
  project_type: string;
  team: string;
  title: string;
  description: string;
  body: string;
  categories: string[];
  additional_categories: string[];
  client_side: "required" | "optional" | "unsupported" | "unknown";
  server_side: "required" | "optional" | "unsupported" | "unknown";
  downloads: number;
  followers: number;
  icon_url: string | null;
  color: number | null;
  published: string;
  updated: string;
  approved: string | null;
  license: { id: string; name: string; url: string | null };
  versions: string[];
  game_versions: string[];
  loaders: string[];
  gallery: ModrinthGalleryImage[];
  issues_url: string | null;
  source_url: string | null;
  wiki_url: string | null;
  discord_url: string | null;
  donation_urls: { id: string; platform: string; url: string }[];
}

export type ModrinthSortIndex =
  | "relevance"
  | "downloads"
  | "follows"
  | "newest"
  | "updated";

export type ModrinthProjectType =
  | "mod"
  | "modpack"
  | "resourcepack"
  | "shader"
  | "datapack";

// ── Tab-name → project_type mapping ─────────────────────────────────────────

const TAB_TO_PROJECT_TYPE: Record<string, ModrinthProjectType> = {
  MODPACKS: "modpack",
  MODS: "mod",
  "RESOURCE PACKS": "resourcepack",
  SHADERS: "shader",
  "DATA PACKS": "datapack",
};

// ── Helpers ──────────────────────────────────────────────────────────────────

const API_BASE = "https://api.modrinth.com/v2";

// ── Tag types from /v2/tag/category and /v2/tag/loader ──────────────────────

export interface ModrinthCategory {
  icon: string;
  name: string;
  project_type: string;
  header: string;
}

export interface ModrinthLoader {
  icon: string;
  name: string;
  supported_project_types: string[];
}

// ── Singleton tag cache ──────────────────────────────────────────────────────

let tagsFetched = false;
const allCategories = ref<ModrinthCategory[]>([]);
const allLoaders = ref<ModrinthLoader[]>([]);
const tagsLoading = ref(false);
const tagsError = ref<string | null>(null);

/** Set of all loader names (lowercase) – populated from API */
const loaderNameSet = ref(new Set<string>());

async function ensureTagsFetched() {
  if (tagsFetched) return;
  tagsFetched = true;
  tagsLoading.value = true;
  tagsError.value = null;

  try {
    const [catRes, loaderRes] = await Promise.all([
      fetch(`${API_BASE}/tag/category`),
      fetch(`${API_BASE}/tag/loader`),
    ]);

    if (!catRes.ok) throw new Error(`Categories: ${catRes.status}`);
    if (!loaderRes.ok) throw new Error(`Loaders: ${loaderRes.status}`);

    allCategories.value = await catRes.json();
    allLoaders.value = await loaderRes.json();

    loaderNameSet.value = new Set(
      allLoaders.value.map((l) => l.name.toLowerCase()),
    );
  } catch (e: unknown) {
    tagsError.value = e instanceof Error ? e.message : "Failed to load tags";
    tagsFetched = false; // allow retry
  } finally {
    tagsLoading.value = false;
  }
}

// ── useModrinthTags composable ──────────────────────────────────────────────

export function useModrinthTags(tab: Ref<string>) {
  // Kick off fetch on first call
  ensureTagsFetched();

  const projectType = computed(
    () => TAB_TO_PROJECT_TYPE[tab.value] ?? "mod",
  );

  /** Categories for the current tab (header === "categories" only) */
  const categories = computed(() =>
    allCategories.value.filter(
      (c) => c.project_type === projectType.value && c.header === "categories",
    ),
  );

  /** Loaders relevant to the current tab's project type (only launcher-supported) */
  const SUPPORTED_LOADERS = new Set(["forge", "neoforge", "fabric", "quilt"]);
  const loaders = computed(() =>
    allLoaders.value.filter(
      (l) =>
        SUPPORTED_LOADERS.has(l.name) &&
        l.supported_project_types.includes(projectType.value),
    ),
  );

  return {
    categories,
    loaders,
    tagsLoading,
    tagsError,
  };
}

export function formatDownloads(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

export function formatRelativeDate(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  if (days < 30) return `${Math.floor(days / 7)}w ago`;
  if (days < 365) return `${Math.floor(days / 30)}mo ago`;
  return `${Math.floor(days / 365)}y ago`;
}

export function environmentLabel(hit: ModrinthHit): string {
  const c = hit.client_side;
  const s = hit.server_side;
  if (c === "required" && s === "required") return "Client and server";
  if (c === "required" && s === "optional") return "Client";
  if (c === "optional" && s === "required") return "Server";
  if (c === "required" && s === "unsupported") return "Client";
  if (c === "unsupported" && s === "required") return "Server";
  if (c === "optional" && s === "optional") return "Client or server";
  return "Unknown";
}

export function splitCategoriesAndLoaders(categories: string[]): {
  cats: string[];
  loaders: string[];
} {
  const cats: string[] = [];
  const loaders: string[] = [];
  const set = loaderNameSet.value;
  for (const c of categories) {
    if (set.size > 0 ? set.has(c.toLowerCase()) : FALLBACK_LOADERS.has(c.toLowerCase()))
      loaders.push(c);
    else cats.push(c);
  }
  return { cats, loaders };
}

/** Fallback until the API tags are loaded */
const FALLBACK_LOADERS = new Set([
  "fabric", "forge", "neoforge", "quilt", "liteloader", "rift", "modloader",
  "bukkit", "spigot", "paper", "purpur", "sponge", "bungeecord", "velocity",
  "waterfall", "folia", "iris", "optifine", "canvas", "vanilla", "datapack",
]);

// ── Build facets ─────────────────────────────────────────────────────────────

function buildFacets(
  projectType: ModrinthProjectType,
  categories: string[],
  loaders: string[],
  environments: string[],
): string {
  // facets = [["project_type:mod"], ["categories:fabric", "categories:forge"], ...]
  // Each inner array = OR, outer arrays = AND
  const facets: string[][] = [];

  facets.push([`project_type:${projectType}`]);

  if (categories.length) {
    // OR within categories
    facets.push(categories.map((c) => `categories:${c.toLowerCase()}`));
  }

  if (loaders.length) {
    facets.push(loaders.map((l) => `categories:${l.toLowerCase()}`));
  }

  if (environments.length) {
    for (const env of environments) {
      if (env.toLowerCase() === "client") {
        facets.push(["client_side:required", "client_side:optional"]);
      } else if (env.toLowerCase() === "server") {
        facets.push(["server_side:required", "server_side:optional"]);
      }
    }
  }

  return JSON.stringify(facets);
}

// ── Composable ───────────────────────────────────────────────────────────────

export interface UseModrinthOptions {
  /** Reactive discover tab id (e.g. "MODS", "MODPACKS") */
  tab: Ref<string>;
  query: Ref<string>;
  sortBy: Ref<ModrinthSortIndex>;
  selectedCategories: Ref<string[]>;
  selectedLoaders: Ref<string[]>;
  selectedEnvironments: Ref<string[]>;
  limit?: number;
}

export function useModrinth(opts: UseModrinthOptions) {
  const hits = ref<ModrinthHit[]>([]);
  const totalHits = ref(0);
  const offset = ref(0);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  const limit = opts.limit ?? 20;

  let abortController: AbortController | null = null;

  async function fetchProjects(resetOffset = true) {
    // Cancel any in-flight request
    if (abortController) abortController.abort();
    abortController = new AbortController();

    if (resetOffset) offset.value = 0;

    const projectType =
      TAB_TO_PROJECT_TYPE[opts.tab.value] ?? "mod";

    const params = new URLSearchParams();
    if (opts.query.value.trim()) params.set("query", opts.query.value.trim());
    params.set("facets", buildFacets(
      projectType,
      opts.selectedCategories.value,
      opts.selectedLoaders.value,
      opts.selectedEnvironments.value,
    ));
    params.set("index", opts.sortBy.value);
    params.set("offset", String(offset.value));
    params.set("limit", String(limit));

    isLoading.value = true;
    error.value = null;

    try {
      const res = await fetch(`${API_BASE}/search?${params}`, {
        signal: abortController.signal,
        headers: {
          "User-Agent": "Volt-Launcher/volt-launcher",
        },
      });

      if (!res.ok) {
        throw new Error(`Modrinth API returned ${res.status}`);
      }

      const data: ModrinthSearchResponse = await res.json();

      if (resetOffset) {
        hits.value = data.hits;
      } else {
        hits.value = [...hits.value, ...data.hits];
      }
      totalHits.value = data.total_hits;
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      isLoading.value = false;
    }
  }

  function loadMore() {
    if (isLoading.value) return;
    if (hits.value.length >= totalHits.value) return;
    offset.value = hits.value.length;
    fetchProjects(false);
  }

  // Re-fetch whenever inputs change (debounced for query, immediate for others)
  let debounceTimer: ReturnType<typeof setTimeout> | undefined;

  watch(
    [opts.tab, opts.sortBy, opts.selectedCategories, opts.selectedLoaders, opts.selectedEnvironments],
    () => fetchProjects(),
    { deep: true },
  );

  watch(opts.query, () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => fetchProjects(), 300);
  });

  // Initial fetch
  fetchProjects();

  return {
    hits,
    totalHits,
    offset,
    isLoading,
    error,
    fetchProjects,
    loadMore,
    formatDownloads,
    formatRelativeDate,
    environmentLabel,
    splitCategoriesAndLoaders,
  };
}

// ── Single project detail composable ─────────────────────────────────────────

export function useModrinthProject() {
  const project = ref<ModrinthProject | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  let abortController: AbortController | null = null;

  async function fetchProject(slugOrId: string) {
    if (abortController) abortController.abort();
    abortController = new AbortController();

    project.value = null;
    isLoading.value = true;
    error.value = null;

    try {
      const res = await fetch(`${API_BASE}/project/${encodeURIComponent(slugOrId)}`, {
        signal: abortController.signal,
        headers: {
          "User-Agent": "Volt-Launcher/volt-launcher",
        },
      });

      if (!res.ok) throw new Error(`Modrinth API returned ${res.status}`);
      project.value = await res.json();
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      error.value = e instanceof Error ? e.message : "Unknown error";
    } finally {
      isLoading.value = false;
    }
  }

  function clear() {
    if (abortController) abortController.abort();
    project.value = null;
    error.value = null;
  }

  return { project, isLoading, error, fetchProject, clear };
}
