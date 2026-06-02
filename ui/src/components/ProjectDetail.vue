<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { computed, ref, watch } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import {
  useModrinthProject,
  formatDownloads,
  formatRelativeDate,
  type ModrinthProject,
  type ModrinthVersion,
} from "@/composables/useModrinth";
import { useInstances } from "@/composables/useInstances";

const API_BASE = "https://api.modrinth.com/v2";

const props = defineProps<{
  slug: string;
}>();

const emit = defineEmits<{
  back: [];
}>();

const { project, isLoading, error, fetchProject } = useModrinthProject();
const { handleInstallModrinthPack } = useInstances();

// Install modal state
const showInstallModal = ref(false);
const installName = ref("");
const selectedVersionId = ref("");
const isInstalling = ref(false);
const installError = ref<string | null>(null);

// Versions for the install modal (top-level refs to avoid nested reactivity issues)
const packVersions = ref<ModrinthVersion[]>([]);
const packVersionsLoading = ref(false);
const packVersionsError = ref<string | null>(null);

async function loadPackVersions(projectId: string) {
  packVersions.value = [];
  packVersionsLoading.value = true;
  packVersionsError.value = null;
  try {
    const res = await fetch(`${API_BASE}/project/${encodeURIComponent(projectId)}/version`, {
      headers: { "User-Agent": "Volt-Launcher/volt-launcher" },
    });
    if (!res.ok) throw new Error(`Modrinth API returned ${res.status}`);
    packVersions.value = await res.json();
  } catch (e: unknown) {
    packVersionsError.value = e instanceof Error ? e.message : "Failed to load versions";
  } finally {
    packVersionsLoading.value = false;
  }
}

function openInstallModal() {
  installName.value = project.value?.title ?? "";
  selectedVersionId.value = "";
  installError.value = null;
  isInstalling.value = false;
  showInstallModal.value = true;
  if (project.value) {
    loadPackVersions(project.value.id);
  }
}

async function confirmInstall() {
  if (!selectedVersionId.value) { installError.value = "Please select a version"; return; }
  installError.value = null;
  isInstalling.value = true;
  try {
    const result = await handleInstallModrinthPack(selectedVersionId.value, installName.value);
    if (result) {
      showInstallModal.value = false;
    } else {
      installError.value = "Installation failed. Check the profiles tab for details.";
    }
  } catch (e: unknown) {
    installError.value = e instanceof Error ? e.message : "Installation failed";
  } finally {
    isInstalling.value = false;
  }
}

const versionOptions = computed(() =>
  packVersions.value.map((v) => ({
    value: v.id,
    label: `${v.name} (${v.game_versions.join(", ")}) — ${v.version_type}`,
  }))
);

watch(
  () => props.slug,
  (slug) => {
    if (slug) fetchProject(slug);
  },
  { immediate: true },
);

const renderedBody = computed(() => {
  if (!project.value?.body) return "";
  const raw = marked.parse(project.value.body) as string;
  return DOMPurify.sanitize(raw, {
    ADD_TAGS: ["iframe"],
    ADD_ATTR: ["allow", "allowfullscreen", "frameborder", "scrolling"],
  });
});

const sortedGallery = computed(() => {
  if (!project.value?.gallery?.length) return [];
  return [...project.value.gallery].sort((a, b) => {
    if (a.featured && !b.featured) return -1;
    if (!a.featured && b.featured) return 1;
    return a.ordering - b.ordering;
  });
});

const externalLinks = computed(() => {
  if (!project.value) return [];
  const links: { icon: string; label: string; url: string }[] = [];
  const p = project.value;
  if (p.issues_url) links.push({ icon: "lucide:bug", label: "Issues", url: p.issues_url });
  if (p.source_url) links.push({ icon: "lucide:code-2", label: "Source", url: p.source_url });
  if (p.wiki_url) links.push({ icon: "lucide:book-open", label: "Wiki", url: p.wiki_url });
  if (p.discord_url) links.push({ icon: "simple-icons:discord", label: "Discord", url: p.discord_url });
  return links;
});

function envLabel(p: ModrinthProject): string {
  const c = p.client_side;
  const s = p.server_side;
  if (c === "required" && s === "required") return "Client and server";
  if (c === "required" && s === "optional") return "Client";
  if (c === "optional" && s === "required") return "Server";
  if (c === "required" && s === "unsupported") return "Client-only";
  if (c === "unsupported" && s === "required") return "Server-only";
  if (c === "optional" && s === "optional") return "Client or server";
  return "Unknown";
}

function colorToBg(color: number | null): string {
  if (color == null) return "background: linear-gradient(135deg, #1a1a2e, #16213e)";
  const hex = `#${color.toString(16).padStart(6, "0")}`;
  return `background: linear-gradient(135deg, ${hex}, ${hex}88)`;
}

function openExternal(url: string) {
  window.open(url, "_blank");
}
</script>

<template>
  <div class="flex flex-1 flex-col overflow-hidden">
    <!-- Top bar -->
    <div class="flex shrink-0 items-center gap-3 border-b border-white/[0.06] px-4 py-2.5 md:px-6">
      <button type="button"
        class="inline-flex items-center gap-1.5 rounded-md px-2 py-1.5 text-[length:var(--text-sm)] text-white/50 transition-colors hover:bg-white/[0.04] hover:text-white/80"
        @click="emit('back')">
        <Icon icon="lucide:arrow-left" class="size-[14px]" />
        Back
      </button>
      <div v-if="project"
        class="flex items-center gap-2 text-[length:var(--text-sm)] text-white/30">
        <span class="text-white/15">/</span>
        <span class="truncate font-semibold text-white/60">{{ project.title }}</span>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="flex flex-1 flex-col items-center justify-center gap-3">
      <Icon icon="lucide:loader-2" class="size-8 animate-spin text-white/20" />
      <span class="text-[length:var(--text-sm)] text-white/30">Loading project…</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="flex flex-1 flex-col items-center justify-center gap-3">
      <Icon icon="lucide:alert-triangle" class="size-10 text-[var(--danger)]/40" />
      <div class="text-[length:var(--text-md)] font-semibold text-white/30">Failed to load project</div>
      <div class="text-[length:var(--text-sm)] text-white/20">{{ error }}</div>
    </div>

    <!-- Content -->
    <div v-else-if="project" class="flex flex-1 overflow-hidden">
      <!-- Main scrollable content -->
      <div class="flex-1 overflow-y-auto">
        <!-- Hero header -->
        <div class="border-b border-white/[0.06] px-5 py-5 md:px-8">
          <div class="flex items-start gap-4">
            <!-- Project icon -->
            <div
              class="flex size-[72px] shrink-0 items-center justify-center overflow-hidden rounded-xl shadow-[0_2px_12px_rgba(0,0,0,0.3)]"
              :style="colorToBg(project.color)">
              <img v-if="project.icon_url" :src="project.icon_url" :alt="project.title"
                class="size-full object-cover" />
              <Icon v-else icon="lucide:package" class="size-8 text-white/30" />
            </div>

            <!-- Title + meta -->
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
              <h1 class="text-[length:var(--text-xl)] font-bold text-white leading-tight">{{ project.title }}</h1>
              <p class="text-[length:var(--text-base)] leading-relaxed text-white/50">{{ project.description }}</p>

              <!-- Stats row -->
              <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5 pt-1">
                <span class="inline-flex items-center gap-1.5 text-[length:var(--text-sm)] text-white/40">
                  <Icon icon="lucide:download" class="size-[12px]" />
                  {{ formatDownloads(project.downloads) }} downloads
                </span>
                <span class="inline-flex items-center gap-1.5 text-[length:var(--text-sm)] text-white/40">
                  <Icon icon="lucide:heart" class="size-[12px]" />
                  {{ formatDownloads(project.followers) }} followers
                </span>
                <span class="inline-flex items-center gap-1.5 text-[length:var(--text-sm)] text-white/40">
                  <Icon icon="lucide:clock" class="size-[12px]" />
                  Updated {{ formatRelativeDate(project.updated) }}
                </span>
                <span class="inline-flex items-center gap-1.5 text-[length:var(--text-sm)] text-white/40">
                  <Icon icon="lucide:calendar" class="size-[12px]" />
                  Created {{ formatRelativeDate(project.published) }}
                </span>
              </div>
            </div>

            <!-- Action buttons -->
            <div class="flex shrink-0 items-center gap-2 pt-1">
              <button type="button"
                class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/[0.08] bg-white/[0.03] px-3 py-[7px] text-[length:var(--text-sm)] font-semibold text-white/50 transition-all hover:bg-white/[0.07] hover:text-white/70"
                @click="openExternal(`https://modrinth.com/${project.project_type}/${project.slug}`)">
                <Icon icon="lucide:external-link" class="size-[13px]" />
                Modrinth
              </button>
              <button v-if="project.project_type === 'modpack'" type="button"
                class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg)] px-4 py-[7px] text-[length:var(--text-sm)] font-bold tracking-[0.05em] text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
                @click="openInstallModal">
                <Icon icon="lucide:download" class="size-[13px]" />
                Install
              </button>
            </div>
          </div>
        </div>

        <!-- Gallery -->
        <div v-if="sortedGallery.length" class="border-b border-white/[0.06] px-5 py-4 md:px-8">
          <div class="flex gap-2.5 overflow-x-auto pb-1">
            <div v-for="img in sortedGallery" :key="img.url"
              class="group relative shrink-0 cursor-pointer overflow-hidden rounded-lg border border-white/[0.06]"
              @click="openExternal(img.url)">
              <img :src="img.url" :alt="img.title || 'Gallery image'"
                class="h-[140px] w-auto object-cover transition-transform duration-200 group-hover:scale-[1.03]"
                loading="lazy" />
              <div v-if="img.title"
                class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent px-2 py-1.5">
                <span class="text-[length:var(--text-2xs)] font-medium text-white/80">{{ img.title }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Markdown body -->
        <div class="px-5 py-5 md:px-8">
          <div class="modrinth-body prose max-w-none" v-html="renderedBody" />
        </div>
      </div>

      <!-- Right sidebar -->
      <aside class="hidden w-[220px] shrink-0 flex-col gap-4 overflow-y-auto border-l border-white/[0.06] p-4 lg:flex">
        <!-- Categories -->
        <div>
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">CATEGORIES</div>
          <div class="flex flex-wrap gap-1.5">
            <span v-for="cat in project.categories" :key="cat"
              class="rounded-[5px] bg-[var(--accent-bg-soft)] px-2 py-[3px] text-[length:var(--text-2xs)] font-medium capitalize text-[var(--primary)]/60">
              {{ cat.replace(/-/g, ' ') }}
            </span>
          </div>
        </div>

        <!-- Loaders -->
        <div v-if="project.loaders.length">
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">LOADERS</div>
          <div class="flex flex-wrap gap-1.5">
            <span v-for="loader in project.loaders" :key="loader"
              class="rounded-[5px] border border-white/[0.06] px-2 py-[3px] text-[length:var(--text-2xs)] font-medium capitalize text-white/50">
              {{ loader }}
            </span>
          </div>
        </div>

        <!-- Game versions -->
        <div v-if="project.game_versions.length">
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">GAME VERSIONS</div>
          <div class="flex flex-wrap gap-1">
            <span v-for="v in project.game_versions.slice(-10).reverse()" :key="v"
              class="rounded-[4px] bg-white/[0.04] px-1.5 py-[2px] text-[length:var(--text-2xs)] text-white/40">
              {{ v }}
            </span>
            <span v-if="project.game_versions.length > 10"
              class="px-1 text-[length:var(--text-2xs)] text-white/25">
              +{{ project.game_versions.length - 10 }} more
            </span>
          </div>
        </div>

        <!-- Environment -->
        <div>
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">ENVIRONMENT</div>
          <div class="flex flex-col gap-1.5">
            <div class="flex items-center gap-2 text-[length:var(--text-sm)] text-white/50">
              <Icon icon="lucide:monitor" class="size-[12px] text-white/30" />
              Client: <span class="font-medium text-white/70">{{ project.client_side }}</span>
            </div>
            <div class="flex items-center gap-2 text-[length:var(--text-sm)] text-white/50">
              <Icon icon="lucide:server" class="size-[12px] text-white/30" />
              Server: <span class="font-medium text-white/70">{{ project.server_side }}</span>
            </div>
          </div>
        </div>

        <!-- License -->
        <div v-if="project.license">
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">LICENSE</div>
          <span class="text-[length:var(--text-sm)] text-white/50">{{ project.license.name || project.license.id }}</span>
        </div>

        <!-- Links -->
        <div v-if="externalLinks.length">
          <div class="mb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">LINKS</div>
          <div class="flex flex-col gap-1">
            <button v-for="link in externalLinks" :key="link.url" type="button"
              class="flex items-center gap-2 rounded-md px-2 py-[5px] text-left text-[length:var(--text-sm)] text-white/50 transition-colors hover:bg-white/[0.04] hover:text-white/70"
              @click="openExternal(link.url)">
              <Icon :icon="link.icon" class="size-[12px] shrink-0" />
              {{ link.label }}
            </button>
          </div>
        </div>
      </aside>
    </div>
  </div>

  <!-- Install modal -->
  <Teleport to="body">
    <div
      v-if="showInstallModal"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showInstallModal = false"
    >
      <div class="w-full max-w-[480px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="flex items-center gap-2.5">
            <img v-if="project?.icon_url" :src="project.icon_url" class="size-6 rounded-md object-cover" />
            <span class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">MODPACK INSTALLIEREN</span>
          </div>
          <button
            type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            @click="showInstallModal = false"
          >
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>

        <!-- Body -->
        <div class="flex flex-col gap-4 p-5">
          <!-- Profile name -->
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">PROFILNAME</label>
            <input
              v-model="installName"
              type="text"
              placeholder="Profilname…"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-[length:var(--text-md)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
              :disabled="isInstalling"
              @keyup.enter="confirmInstall"
            />
          </div>

          <!-- Version -->
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">VERSION</label>
            <VoltSelect
              v-model="selectedVersionId"
              :options="versionOptions"
              placeholder="Version wählen"
              :disabled="isInstalling || packVersionsLoading || !!packVersionsError"
            />
            <div class="mt-0.5 text-[length:var(--text-2xs)] text-white/40">
              <span v-if="packVersionsLoading" class="flex items-center gap-1.5">
                <Icon icon="lucide:loader-2" class="size-[11px] animate-spin" /> Lade Versionen…
              </span>
              <span v-else-if="packVersionsError" class="text-[var(--danger-text)]">{{ packVersionsError }}</span>
              <span v-else>{{ packVersions.length }} Versionen verfügbar</span>
            </div>
          </div>

          <!-- Error -->
          <div v-if="installError" class="rounded-lg border border-[var(--danger-border)] bg-[var(--danger-bg)] px-3 py-2.5 text-[length:var(--text-base)] text-[var(--danger-text)]">
            {{ installError }}
          </div>

          <!-- Actions -->
          <div class="mt-1 flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              :disabled="isInstalling"
              @click="showInstallModal = false"
            >
              Abbrechen
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isInstalling || !selectedVersionId || !installName.trim()"
              @click="confirmInstall"
            >
              <Icon v-if="isInstalling" icon="lucide:loader-2" class="size-[13px] animate-spin" />
              {{ isInstalling ? "Installiere…" : "Installieren" }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* ── Modrinth body markdown ──────────────────────────────────────────────── */
.modrinth-body {
  color: rgba(255, 255, 255, 0.65);
  font-size: var(--text-base);
  line-height: 1.7;
}

.modrinth-body :deep(h1) {
  font-size: var(--text-xl);
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  margin: 1.5em 0 0.5em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.modrinth-body :deep(h2) {
  font-size: var(--text-lg);
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
  margin: 1.3em 0 0.4em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.modrinth-body :deep(h3) {
  font-size: var(--text-md);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.8);
  margin: 1.1em 0 0.3em;
}

.modrinth-body :deep(h4),
.modrinth-body :deep(h5),
.modrinth-body :deep(h6) {
  font-size: var(--text-base);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
  margin: 1em 0 0.25em;
}

.modrinth-body :deep(p) {
  margin: 0.6em 0;
}

.modrinth-body :deep(a) {
  color: var(--primary);
  text-decoration: none;
  transition: opacity 0.15s;
}

.modrinth-body :deep(a:hover) {
  opacity: 0.8;
  text-decoration: underline;
}

.modrinth-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 0.8em 0;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.modrinth-body :deep(strong) {
  color: rgba(255, 255, 255, 0.85);
  font-weight: 600;
}

.modrinth-body :deep(em) {
  color: rgba(255, 255, 255, 0.6);
}

.modrinth-body :deep(code) {
  background: rgba(255, 255, 255, 0.06);
  padding: 0.15em 0.4em;
  border-radius: 4px;
  font-size: 0.9em;
  color: rgba(255, 255, 255, 0.7);
}

.modrinth-body :deep(pre) {
  background: rgba(5, 13, 26, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 1em;
  overflow-x: auto;
  margin: 0.8em 0;
}

.modrinth-body :deep(pre code) {
  background: none;
  padding: 0;
  border-radius: 0;
}

.modrinth-body :deep(blockquote) {
  border-left: 3px solid rgba(var(--primary-rgb), 0.3);
  padding-left: 1em;
  margin: 0.8em 0;
  color: rgba(255, 255, 255, 0.5);
}

.modrinth-body :deep(ul),
.modrinth-body :deep(ol) {
  padding-left: 1.5em;
  margin: 0.6em 0;
}

.modrinth-body :deep(li) {
  margin: 0.25em 0;
}

.modrinth-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  margin: 1.5em 0;
}

.modrinth-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.8em 0;
}

.modrinth-body :deep(th),
.modrinth-body :deep(td) {
  padding: 0.5em 0.75em;
  border: 1px solid rgba(255, 255, 255, 0.06);
  text-align: left;
}

.modrinth-body :deep(th) {
  background: rgba(255, 255, 255, 0.03);
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.modrinth-body :deep(details) {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 0.75em 1em;
  margin: 0.6em 0;
}

.modrinth-body :deep(summary) {
  cursor: pointer;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}
</style>
