<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ref } from "vue";
import { useLauncher } from "@/composables/useLauncher";
import ProjectDetail from "@/components/ProjectDetail.vue";
import {
  useModrinth,
  useModrinthTags,
  formatDownloads,
  formatRelativeDate,
  environmentLabel,
  splitCategoriesAndLoaders,
  type ModrinthSortIndex,
} from "@/composables/useModrinth";

const { discoverTabActive } = useLauncher();

const searchQuery = ref("");
const sortBy = ref<ModrinthSortIndex>("relevance");
const sortOptions = [
  { value: "relevance" as const, label: "Relevance" },
  { value: "downloads" as const, label: "Downloads" },
  { value: "follows" as const, label: "Follows" },
  { value: "newest" as const, label: "Newest" },
  { value: "updated" as const, label: "Updated" },
];

// Filter state
const selectedCategories = ref<string[]>([]);
const selectedLoaders = ref<string[]>([]);
const selectedEnvironments = ref<string[]>([]);

// ── Tags from Modrinth API ───────────────────────────────────────────────────

const { categories: apiCategories, loaders: apiLoaders } = useModrinthTags(discoverTabActive);

const environments = [
  { label: "Client", icon: "lucide:monitor" },
  { label: "Server", icon: "lucide:server" },
];

const toggleFilter = (arr: string[], value: string) => {
  const idx = arr.indexOf(value);
  if (idx >= 0) arr.splice(idx, 1);
  else arr.push(value);
};

// Expanded state for sidebar sections
const expandedSections = ref({ categories: true, loaders: true, environment: true });

// ── Project detail view ──────────────────────────────────────────────────────

const selectedProjectSlug = ref<string | null>(null);

function openProject(slug: string) {
  selectedProjectSlug.value = slug;
}

function closeProject() {
  selectedProjectSlug.value = null;
}

// ── Real Modrinth API ────────────────────────────────────────────────────────

const {
  hits,
  totalHits,
  isLoading,
  error,
  loadMore,
} = useModrinth({
  tab: discoverTabActive,
  query: searchQuery,
  sortBy,
  selectedCategories,
  selectedLoaders,
  selectedEnvironments,
  limit: 20,
});

function onScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200) {
    loadMore();
  }
}

function openProjectPage(hit: { project_type: string; slug: string }) {
  window.open(`https://modrinth.com/${hit.project_type}/${hit.slug}`, "_blank");
}

/** Convert Modrinth's integer color to a CSS background gradient */
function colorToBg(color: number | null): string {
  if (color == null) return "background: linear-gradient(135deg, #1a1a2e, #16213e)";
  const hex = `#${color.toString(16).padStart(6, "0")}`;
  return `background: linear-gradient(135deg, ${hex}, ${hex}88)`;
}
</script>

<template>
  <!-- Project detail view -->
  <ProjectDetail v-if="selectedProjectSlug" :slug="selectedProjectSlug" @back="closeProject" />

  <!-- Search / browse view -->
  <template v-else>
    <!-- Search bar -->
    <div class="flex shrink-0 items-center gap-2 border-b border-white/[0.06] px-4 py-3 md:px-6">
      <div class="flex flex-1 items-center gap-2 rounded-lg border border-white/10 bg-[var(--surface-panel)] px-3 py-[7px]">
        <Icon icon="lucide:search" class="size-[14px] shrink-0 text-white/30" />
        <input v-model="searchQuery" type="text" placeholder="Search projects…"
          class="w-full bg-transparent text-[length:var(--text-md)] text-white outline-none placeholder:text-white/40" />
        <button v-if="searchQuery" type="button" class="shrink-0 text-white/30 transition-colors hover:text-white/60"
          @click="searchQuery = ''">
          <Icon icon="lucide:x" class="size-[13px]" />
        </button>
      </div>
    </div>

    <!-- Main content: sidebar + results -->
    <div class="flex flex-1 overflow-hidden">
      <!-- Filter sidebar -->
      <aside
        class="hidden w-[200px] shrink-0 flex-col gap-1 overflow-y-auto border-r border-white/[0.06] p-4 md:flex">
        <!-- Categories -->
        <button type="button"
          class="flex w-full items-center justify-between rounded-md px-1.5 py-1.5 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 transition-colors hover:text-white/60"
          @click="expandedSections.categories = !expandedSections.categories">
          CATEGORIES
          <Icon :icon="expandedSections.categories ? 'lucide:chevron-up' : 'lucide:chevron-down'"
            class="size-[11px]" />
        </button>
        <div v-show="expandedSections.categories" class="mb-2 flex flex-col gap-[2px]">
          <button v-for="cat in apiCategories" :key="cat.name" type="button"
            class="flex items-center gap-2 rounded-md px-2 py-[5px] text-left text-[length:var(--text-sm)] capitalize transition-all duration-150"
            :class="selectedCategories.includes(cat.name)
              ? 'bg-[var(--accent-bg-strong)] font-semibold text-[var(--primary)]'
              : 'text-white/55 hover:bg-white/[0.04] hover:text-white/80'"
            @click="toggleFilter(selectedCategories, cat.name)">
            <span class="flex size-[12px] shrink-0 items-center [&>svg]:size-full" v-html="cat.icon" />
            {{ cat.name.replace(/-/g, ' ') }}
          </button>
        </div>

        <!-- Loaders -->
        <template v-if="apiLoaders.length > 0">
          <button type="button"
            class="flex w-full items-center justify-between rounded-md px-1.5 py-1.5 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 transition-colors hover:text-white/60"
            @click="expandedSections.loaders = !expandedSections.loaders">
            LOADER
            <Icon :icon="expandedSections.loaders ? 'lucide:chevron-up' : 'lucide:chevron-down'"
              class="size-[11px]" />
          </button>
          <div v-show="expandedSections.loaders" class="mb-2 flex flex-col gap-[2px]">
            <button v-for="l in apiLoaders" :key="l.name" type="button"
              class="flex items-center gap-2 rounded-md px-2 py-[5px] text-left text-[length:var(--text-sm)] capitalize transition-all duration-150"
              :class="selectedLoaders.includes(l.name)
                ? 'bg-[var(--accent-bg-strong)] font-semibold text-[var(--primary)]'
                : 'text-white/55 hover:bg-white/[0.04] hover:text-white/80'"
              @click="toggleFilter(selectedLoaders, l.name)">
              <span class="flex size-[12px] shrink-0 items-center [&>svg]:size-full" v-html="l.icon" />
              {{ l.name }}
            </button>
          </div>
        </template>

        <!-- Environment -->
        <button type="button"
          class="flex w-full items-center justify-between rounded-md px-1.5 py-1.5 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 transition-colors hover:text-white/60"
          @click="expandedSections.environment = !expandedSections.environment">
          ENVIRONMENT
          <Icon :icon="expandedSections.environment ? 'lucide:chevron-up' : 'lucide:chevron-down'"
            class="size-[11px]" />
        </button>
        <div v-show="expandedSections.environment" class="mb-2 flex flex-col gap-[2px]">
          <button v-for="e in environments" :key="e.label" type="button"
            class="flex items-center gap-2 rounded-md px-2 py-[5px] text-left text-[length:var(--text-sm)] transition-all duration-150"
            :class="selectedEnvironments.includes(e.label)
              ? 'bg-[var(--accent-bg-strong)] font-semibold text-[var(--primary)]'
              : 'text-white/55 hover:bg-white/[0.04] hover:text-white/80'"
            @click="toggleFilter(selectedEnvironments, e.label)">
            <Icon :icon="e.icon" class="size-[12px] shrink-0" />
            {{ e.label }}
          </button>
        </div>

        <!-- Clear filters -->
        <button v-if="selectedCategories.length || selectedLoaders.length || selectedEnvironments.length"
          type="button"
          class="mt-1 rounded-md px-2 py-1.5 text-[length:var(--text-2xs)] font-semibold text-[var(--primary)] transition-colors hover:bg-[var(--accent-bg)]"
          @click="selectedCategories = []; selectedLoaders = []; selectedEnvironments = []">
          Clear all filters
        </button>
      </aside>

      <!-- Results area -->
      <div class="flex flex-1 flex-col overflow-hidden">
        <!-- Sort bar -->
        <div class="flex shrink-0 items-center justify-between border-b border-white/[0.06] px-4 py-2.5 md:px-5">
          <span class="text-[length:var(--text-sm)] text-white/40">
            <span class="font-semibold text-white/70">{{ totalHits.toLocaleString() }}</span> results
          </span>
          <div class="flex items-center gap-1.5">
            <span class="text-[length:var(--text-2xs)] text-white/35">Sort by</span>
            <div class="flex gap-[3px]">
              <button v-for="s in sortOptions" :key="s.value" type="button"
                class="rounded-[5px] px-2 py-[3px] text-[length:var(--text-2xs)] font-semibold transition-all duration-150"
                :class="sortBy === s.value
                  ? 'bg-[var(--accent-bg-strong)] text-[var(--primary)]'
                  : 'text-white/40 hover:bg-white/[0.04] hover:text-white/60'" @click="sortBy = s.value">
                {{ s.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- Project list -->
        <div class="flex-1 overflow-y-auto" @scroll="onScroll">
          <!-- Loading state -->
          <div v-if="isLoading && hits.length === 0" class="flex flex-col items-center justify-center gap-3 py-20">
            <Icon icon="lucide:loader-2" class="size-8 animate-spin text-white/20" />
            <div class="text-[length:var(--text-sm)] text-white/30">Loading projects…</div>
          </div>

          <!-- Error state -->
          <div v-else-if="error && hits.length === 0" class="flex flex-col items-center justify-center gap-3 py-20">
            <Icon icon="lucide:alert-triangle" class="size-10 text-[var(--danger)]/40" />
            <div class="text-[length:var(--text-md)] font-semibold text-white/30">Failed to load</div>
            <div class="text-[length:var(--text-sm)] text-white/20">{{ error }}</div>
          </div>

          <!-- Empty state -->
          <div v-else-if="hits.length === 0 && !isLoading"
            class="flex flex-col items-center justify-center gap-3 py-20">
            <Icon icon="lucide:search-x" class="size-10 text-white/15" />
            <div class="text-[length:var(--text-md)] font-semibold text-white/30">No projects found</div>
            <div class="text-[length:var(--text-sm)] text-white/20">Try adjusting your search or filters</div>
          </div>

          <template v-else>
            <div v-for="project in hits" :key="project.project_id"
              class="group flex cursor-pointer gap-3.5 border-b border-white/[0.04] px-4 py-3.5 transition-colors duration-150 hover:bg-white/[0.02] md:px-5"
              @click="openProject(project.slug)">
              <!-- Project icon -->
              <div
                class="flex size-[52px] shrink-0 items-center justify-center overflow-hidden rounded-[10px] shadow-[0_2px_8px_rgba(0,0,0,0.25)]"
                :style="colorToBg(project.color)">
                <img v-if="project.icon_url" :src="project.icon_url" :alt="project.title"
                  class="size-full object-cover" loading="lazy" />
                <Icon v-else icon="lucide:package" class="size-6 text-white/30" />
              </div>

              <!-- Project info -->
              <div class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex items-baseline gap-2">
                  <span
                    class="truncate text-[length:var(--text-md)] font-bold text-white transition-colors group-hover:text-[var(--primary)]">{{
                      project.title }}</span>
                  <span class="shrink-0 text-[length:var(--text-2xs)] text-white/35">by <span
                      class="font-medium text-white/50">{{ project.author }}</span></span>
                </div>
                <p class="line-clamp-1 text-[length:var(--text-base)] leading-[1.5] text-white/50">{{
                  project.description }}</p>
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1 pt-0.5">
                  <!-- Stats -->
                  <span class="inline-flex items-center gap-1 text-[length:var(--text-2xs)] text-white/35">
                    <Icon icon="lucide:download" class="size-[10px]" />{{ formatDownloads(project.downloads) }}
                  </span>
                  <span class="inline-flex items-center gap-1 text-[length:var(--text-2xs)] text-white/35">
                    <Icon icon="lucide:heart" class="size-[10px]" />{{ formatDownloads(project.follows) }}
                  </span>
                  <span class="inline-flex items-center gap-1 text-[length:var(--text-2xs)] text-white/35">
                    <Icon icon="lucide:clock" class="size-[10px]" />{{ formatRelativeDate(project.date_modified) }}
                  </span>

                  <!-- Separator -->
                  <span class="hidden text-white/10 sm:inline">&middot;</span>

                  <!-- Environment tag -->
                  <span
                    class="hidden rounded-[4px] bg-white/[0.05] px-1.5 py-[1px] text-[length:var(--text-2xs)] font-medium text-white/40 sm:inline-block">{{
                      environmentLabel(project) }}</span>

                  <!-- Category tags -->
                  <span v-for="c in splitCategoriesAndLoaders(project.display_categories).cats.slice(0, 3)" :key="c"
                    class="hidden rounded-[4px] bg-[var(--accent-bg-soft)] px-1.5 py-[1px] text-[length:var(--text-2xs)] font-medium text-[var(--primary)]/60 sm:inline-block">{{
                      c }}</span>
                  <span v-if="splitCategoriesAndLoaders(project.display_categories).cats.length > 3"
                    class="hidden text-[length:var(--text-2xs)] text-white/25 sm:inline">+{{
                      splitCategoriesAndLoaders(project.display_categories).cats.length - 3 }}</span>

                  <!-- Loader tags -->
                  <span v-for="l in splitCategoriesAndLoaders(project.display_categories).loaders.slice(0, 2)"
                    :key="l"
                    class="hidden rounded-[4px] border border-white/[0.06] px-1.5 py-[1px] text-[length:var(--text-2xs)] font-medium text-white/35 sm:inline-block">{{
                      l }}</span>
                  <span v-if="splitCategoriesAndLoaders(project.display_categories).loaders.length > 2"
                    class="hidden text-[length:var(--text-2xs)] text-white/25 sm:inline">+{{
                      splitCategoriesAndLoaders(project.display_categories).loaders.length - 2 }}</span>
                </div>
              </div>

              <!-- Actions -->
              <div class="flex shrink-0 items-center gap-1.5">
                <button type="button"
                  class="rounded-[7px] border border-white/[0.08] bg-white/[0.03] px-3 py-[6px] text-[length:var(--text-2xs)] font-bold tracking-[0.08em] text-white/50 opacity-0 transition-all duration-200 hover:bg-white/[0.07] hover:text-white/70 group-hover:opacity-100"
                  @click.stop="openProjectPage(project)">
                  <Icon icon="lucide:external-link" class="size-[12px]" />
                </button>
                <button type="button"
                  class="rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg)] px-3 py-[6px] text-[length:var(--text-2xs)] font-bold tracking-[0.08em] text-[var(--primary)] opacity-0 transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)] group-hover:opacity-100">
                  Install
                </button>
              </div>
            </div>

            <!-- Load more spinner -->
            <div v-if="isLoading && hits.length > 0" class="flex justify-center py-4">
              <Icon icon="lucide:loader-2" class="size-5 animate-spin text-white/20" />
            </div>
          </template>
        </div>
      </div>
    </div>
  </template>
</template>
