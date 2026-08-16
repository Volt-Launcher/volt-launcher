<script setup lang="ts">
import { computed } from "vue";
import { Icon } from "@iconify/vue";
import { formatDownloads } from "@/composables/useProviders";
import { formatRelativeIso, handleImgError } from "@/composables/helpers";
import { t } from "@/i18n";
import type { ProjectSummary } from "@/composables/types";

const props = defineProps<{ project: ProjectSummary }>();
defineEmits<{ open: [project: ProjectSummary]; install: [project: ProjectSummary] }>();

const LOADER_COLOURS: Record<string, string> = {
  fabric: "#c9a26d",
  forge: "#5b6394",
  neoforge: "#f28f22",
  quilt: "#8b61b4",
};

const loaderChips = computed(() => props.project.loaders.slice(0, 3));
const categoryChips = computed(() => props.project.categories.slice(0, 3));

const fallbackIcon = "https://placehold.co/64x64/0a1535/2a3a6a?text=%20";
</script>

<template>
  <article
    class="group flex cursor-pointer flex-col gap-3 rounded-xl border border-white/10 bg-[var(--surface-panel)] p-4 transition-all duration-200 hover:border-[var(--accent-border)] hover:bg-[var(--surface-panel-hover)]"
    @click="$emit('open', project)"
  >
    <div class="flex items-start gap-3">
      <img
        :src="project.iconUrl || fallbackIcon"
        :data-fallback-src="fallbackIcon"
        :alt="project.title"
        class="size-12 shrink-0 rounded-lg bg-white/5 object-cover"
        loading="lazy"
        @error="handleImgError"
      />
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-[length:var(--text-md-plus)] font-bold text-white">{{ project.title }}</h3>
        <p v-if="project.author" class="truncate text-[length:var(--text-2xs)] text-white/40">
          {{ t("discover.by", { author: project.author }) }}
        </p>
      </div>
      <button
        type="button"
        class="shrink-0 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] p-1.5 text-[var(--primary)] opacity-0 transition-all duration-200 group-hover:opacity-100 hover:bg-[var(--accent-bg-hover)] focus-visible:opacity-100"
        :aria-label="t('install.toProfile')"
        @click.stop="$emit('install', project)"
      >
        <Icon icon="lucide:plus" class="size-[14px]" />
      </button>
    </div>

    <p class="line-clamp-2 text-[length:var(--text-sm)] leading-[1.5] text-white/55">
      {{ project.description }}
    </p>

    <div class="flex flex-wrap items-center gap-1.5">
      <span
        v-for="loader in loaderChips"
        :key="loader"
        class="rounded-[4px] px-1.5 py-0.5 text-[length:var(--text-2xs)] font-semibold capitalize"
        :style="{
          color: LOADER_COLOURS[loader] ?? '#9aa4c4',
          background: `${LOADER_COLOURS[loader] ?? '#9aa4c4'}1f`,
        }"
      >
        {{ loader }}
      </span>
      <span
        v-for="category in categoryChips"
        :key="category"
        class="rounded-[4px] bg-white/[0.06] px-1.5 py-0.5 text-[length:var(--text-2xs)] text-white/45 capitalize"
      >
        {{ category }}
      </span>
    </div>

    <footer class="mt-auto flex items-center gap-3 text-[length:var(--text-2xs)] text-white/35">
      <span class="inline-flex items-center gap-1">
        <Icon icon="lucide:download" class="size-[11px]" />
        {{ formatDownloads(project.downloads) }}
      </span>
      <span v-if="project.follows > 0" class="inline-flex items-center gap-1">
        <Icon icon="lucide:heart" class="size-[11px]" />
        {{ formatDownloads(project.follows) }}
      </span>
      <span v-if="project.updated" class="ml-auto truncate">
        {{ t("discover.updated", { when: formatRelativeIso(project.updated) }) }}
      </span>
    </footer>
  </article>
</template>
