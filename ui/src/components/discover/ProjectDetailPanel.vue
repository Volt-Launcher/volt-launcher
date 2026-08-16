<script setup lang="ts">
import { computed } from "vue";
import { Icon } from "@iconify/vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { useLauncher } from "@/composables/useLauncher";
import { formatDownloads } from "@/composables/useProviders";
import { formatRelativeIso } from "@/composables/helpers";
import type { ProjectSummary } from "@/composables/types";

const emit = defineEmits<{ close: []; install: [project: ProjectSummary] }>();

const { t, project, isLoadingProject, projectError } = useLauncher();

const LINK_ICONS: Record<string, string> = {
  website: "lucide:globe",
  issues: "lucide:circle-alert",
  source: "lucide:code",
  wiki: "lucide:book-open",
  discord: "simple-icons:discord",
};

/**
 * Modrinth returns Markdown, CurseForge returns HTML. Both are third-party content rendered
 * inside a privileged Electron window, so everything is sanitised before it reaches the DOM.
 */
const renderedBody = computed(() => {
  const body = project.value?.body ?? "";
  if (!body.trim()) return "";

  const looksLikeHtml = /<\/?[a-z][\s\S]*>/i.test(body) && project.value?.provider === "curseforge";
  const html = looksLikeHtml ? body : (marked.parse(body, { async: false }) as string);

  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "p", "br", "strong", "em", "del", "code", "pre", "blockquote",
      "h1", "h2", "h3", "h4", "h5", "h6",
      "ul", "ol", "li", "a", "img", "hr",
      "table", "thead", "tbody", "tr", "th", "td", "span", "div",
    ],
    ALLOWED_ATTR: ["href", "src", "alt", "title", "target", "rel"],
    // Block javascript:/data: URLs outright.
    ALLOWED_URI_REGEXP: /^(?:https?:|mailto:|#)/i,
  });
});
</script>

<template>
  <div
    v-if="project || isLoadingProject || projectError"
    class="fixed inset-0 z-[70] flex justify-end bg-black/60 backdrop-blur-sm"
    @click.self="emit('close')"
  >
    <aside class="flex h-full w-full max-w-[720px] flex-col border-l border-white/10 bg-[var(--surface-modal)]">
      <div v-if="isLoadingProject" class="flex flex-1 items-center justify-center gap-2 text-white/50">
        <Icon icon="lucide:loader-2" class="size-5 animate-spin" />{{ t("common.loading") }}
      </div>

      <div v-else-if="projectError" class="flex flex-1 flex-col items-center justify-center gap-3 p-6">
        <Icon icon="lucide:circle-alert" class="size-8 text-[var(--danger-text)]" />
        <p class="text-center text-[length:var(--text-md)] text-white/60">{{ projectError }}</p>
        <button
          type="button"
          class="rounded-[7px] border border-white/10 bg-white/5 px-4 py-2 text-[length:var(--text-sm)] font-semibold text-white/60 hover:bg-white/10"
          @click="emit('close')"
        >
          {{ t("common.close") }}
        </button>
      </div>

      <template v-else-if="project">
        <header class="flex items-start gap-4 border-b border-white/[0.07] p-5">
          <img
            v-if="project.iconUrl"
            :src="project.iconUrl"
            :alt="project.title"
            class="size-16 shrink-0 rounded-xl bg-white/5 object-cover"
          />
          <div class="min-w-0 flex-1">
            <h2 class="text-[length:var(--text-xl)] font-bold text-white">{{ project.title }}</h2>
            <p v-if="project.author" class="text-[length:var(--text-sm)] text-white/45">
              {{ t("discover.by", { author: project.author }) }}
            </p>
            <div class="mt-2 flex flex-wrap items-center gap-3 text-[length:var(--text-2xs)] text-white/40">
              <span class="inline-flex items-center gap-1">
                <Icon icon="lucide:download" class="size-[11px]" />{{ formatDownloads(project.downloads) }}
              </span>
              <span v-if="project.updated" class="inline-flex items-center gap-1">
                <Icon icon="lucide:clock" class="size-[11px]" />
                {{ t("discover.updated", { when: formatRelativeIso(project.updated) }) }}
              </span>
              <span v-if="project.license" class="inline-flex items-center gap-1">
                <Icon icon="lucide:scale" class="size-[11px]" />{{ project.license }}
              </span>
            </div>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-2 text-[length:var(--text-sm)] font-semibold text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)]"
              @click="emit('install', project)"
            >
              <Icon icon="lucide:download" class="size-[13px]" />
              {{ project.kind === "modpack" ? t("common.install") : t("install.toProfile") }}
            </button>
            <button
              type="button"
              class="rounded-md p-1.5 text-white/40 transition-colors hover:bg-white/5 hover:text-white"
              :aria-label="t('common.close')"
              @click="emit('close')"
            >
              <Icon icon="lucide:x" class="size-[16px]" />
            </button>
          </div>
        </header>

        <div class="flex flex-1 flex-col gap-5 overflow-y-auto p-5">
          <!-- Metadata chips -->
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="loader in project.loaders"
              :key="loader"
              class="rounded-[5px] bg-[var(--accent-bg-soft)] px-2 py-0.5 text-[length:var(--text-2xs)] font-semibold text-[var(--primary)]/80 capitalize"
            >
              {{ loader }}
            </span>
            <span
              v-for="category in project.categories"
              :key="category"
              class="rounded-[5px] bg-white/[0.06] px-2 py-0.5 text-[length:var(--text-2xs)] text-white/50 capitalize"
            >
              {{ category }}
            </span>
          </div>

          <!-- Links -->
          <div v-if="Object.keys(project.links).length" class="flex flex-wrap gap-2">
            <a
              v-for="(url, key) in project.links"
              :key="key"
              :href="url"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-1.5 rounded-[6px] border border-white/10 bg-white/[0.03] px-2.5 py-1 text-[length:var(--text-2xs)] font-semibold text-white/50 capitalize transition-colors hover:bg-white/[0.08] hover:text-white/80"
            >
              <Icon :icon="LINK_ICONS[key] ?? 'lucide:link'" class="size-[11px]" />{{ key }}
            </a>
          </div>

          <!-- Gallery -->
          <div v-if="project.gallery.length" class="flex gap-3 overflow-x-auto pb-2">
            <img
              v-for="image in project.gallery.slice(0, 8)"
              :key="image"
              :src="image"
              alt=""
              loading="lazy"
              class="h-[150px] shrink-0 rounded-lg border border-white/10 object-cover"
            />
          </div>

          <!-- Description -->
          <article v-if="renderedBody" class="project-body" v-html="renderedBody" />
          <p v-else class="text-[length:var(--text-base)] text-white/50">{{ project.description }}</p>
        </div>
      </template>
    </aside>
  </div>
</template>

<style scoped>
/*
 * Provider descriptions arrive as arbitrary (sanitised) markup, so the readable typography has to
 * be applied from here rather than with utility classes on elements we do not control.
 */
.project-body {
  font-size: var(--text-base);
  line-height: 1.65;
  color: rgb(255 255 255 / 0.62);
  overflow-wrap: anywhere;
}
.project-body :deep(h1),
.project-body :deep(h2),
.project-body :deep(h3),
.project-body :deep(h4) {
  margin: 1.4em 0 0.5em;
  font-weight: 700;
  color: rgb(255 255 255 / 0.92);
  line-height: 1.3;
}
.project-body :deep(h1) { font-size: var(--text-lg); }
.project-body :deep(h2) { font-size: var(--text-md-plus); }
.project-body :deep(h3) { font-size: var(--text-md); }
.project-body :deep(p) { margin: 0.75em 0; }
.project-body :deep(a) { color: var(--primary); text-decoration: underline; }
.project-body :deep(ul),
.project-body :deep(ol) { margin: 0.75em 0; padding-left: 1.4em; }
.project-body :deep(ul) { list-style: disc; }
.project-body :deep(ol) { list-style: decimal; }
.project-body :deep(li) { margin: 0.3em 0; }
.project-body :deep(img) { max-width: 100%; height: auto; border-radius: 8px; }
.project-body :deep(code) {
  background: rgb(255 255 255 / 0.07);
  border-radius: 4px;
  padding: 0.1em 0.35em;
  font-size: 0.9em;
}
.project-body :deep(pre) {
  background: rgb(0 0 0 / 0.35);
  border: 1px solid rgb(255 255 255 / 0.08);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  margin: 0.9em 0;
}
.project-body :deep(pre code) { background: none; padding: 0; }
.project-body :deep(blockquote) {
  border-left: 3px solid var(--primary);
  padding-left: 12px;
  margin: 0.9em 0;
  color: rgb(255 255 255 / 0.5);
}
.project-body :deep(table) {
  display: block;
  width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: 0.9em 0;
}
.project-body :deep(th),
.project-body :deep(td) {
  border: 1px solid rgb(255 255 255 / 0.1);
  padding: 6px 10px;
  text-align: left;
}
.project-body :deep(hr) {
  border: none;
  border-top: 1px solid rgb(255 255 255 / 0.1);
  margin: 1.4em 0;
}
</style>
