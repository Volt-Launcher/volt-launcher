<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
import InstallProgress from '@/components/discover/InstallProgress.vue';
import type {
  LauncherInstance,
  ContentEntry,
  ContentType,
  ContentUpdate,
  ExportFormat,
  ExportResult,
  InstanceSettings,
  ModpackStatus,
  ProjectVersion,
} from '@/composables/useLauncher';

const {
  t,
  authData,
  activeTab,
  showCreateModal,
  showEditModal,
  showDeleteModal,
  editTargetInstance,
  editNewName,
  deleteTargetInstance,
  profileFilter,
  selectedInstanceName,
  instances,
  filteredInstances,
  settings,
  pendingInstances,
  visiblePendingInstances,
  isLoadingInstances,
  isLaunching,
  versionEmoji,
  formatRelativeDate,
  formatRelativeIso,
  formatLoaderId,
  handleImgError,
  handleDeleteInstance,
  handleRenameInstance,
  handleOpenInstanceFolder,
  handleLaunch,
  handleUpdateInstanceSettings,
  loadInstanceContent,
  addInstanceContent,
  removeInstanceContent,
  toggleInstanceContent,
  identifyContent,
  checkContentUpdates,
  loadContentVersions,
  changeContentVersion,
  loadModpackStatus,
  loadModpackVersions,
  startModpackUpdate,
  exportInstance,
  importModpack,
  trackModpackJob,
  loadInstances,
  openDirectory,
  launcherMessage,
  error,
} = useLauncher();

const filters = ['ALL', 'RELEASE', 'SNAPSHOT', 'NEOFORGE', 'FORGE', 'FABRIC', 'QUILT'];

const versionGradientClass = (type: string) => ({
  release: 'bg-[linear-gradient(135deg,#0d3a18,#184d22)]',
  snapshot: 'bg-[linear-gradient(135deg,#0a2040,#001535)]',
  old_beta: 'bg-[linear-gradient(135deg,#3a1a08,#5a2a10)]',
  old_alpha: 'bg-[linear-gradient(135deg,#4d0f0f,#7a1a1a)]',
}[type] ?? 'bg-[linear-gradient(135deg,#0a1535,#122050)]');

// ── Manage view ───────────────────────────────────────────────────────────────
// Only the name is held; the profile itself is looked up in the live list on every render. A
// snapshot would freeze at whatever the profile looked like when the view opened and would go
// stale the moment the three-second poll refreshed the list.
const managingInstanceName = ref<string | null>(null);
const managingInstance = computed(
  () => instances.value.find((inst) => inst.name === managingInstanceName.value) ?? null,
);

function openManage(inst: LauncherInstance) {
  // A profile mid-install has no stable content to manage yet.
  if (inst.busy) return;
  managingInstanceName.value = inst.name;
}

function closeManage() {
  managingInstanceName.value = null;
}

/** Keeps the manage view on the same profile after a rename, instead of dropping back to the grid. */
async function renameManaged(oldName: string, newName: string) {
  const trimmed = newName.trim();
  await handleRenameInstance(oldName, trimmed);
  if (managingInstanceName.value === oldName && instances.value.some((i) => i.name === trimmed)) {
    managingInstanceName.value = trimmed;
  }
}

// ── Play action ───────────────────────────────────────────────────────────────
function playInstance(inst: LauncherInstance) {
  // Belt and braces: the button is hidden while busy, but this is also reachable by keyboard.
  if (inst.busy) return;
  selectInstance(inst);
  void handleLaunch(inst.name);
}

// ── Select action ───────────────────────────────────────────────────────────────
function selectInstance(inst: LauncherInstance) {
  selectedInstanceName.value = inst.name;
}

// ── 3-dot menu ────────────────────────────────────────────────────────────────
const openMenuSlug = ref<string | null>(null);

function toggleMenu(slug: string) {
  openMenuSlug.value = openMenuSlug.value === slug ? null : slug;
}

function closeMenu() {
  openMenuSlug.value = null;
}

function startEdit(inst: LauncherInstance) {
  editTargetInstance.value = inst;
  editNewName.value = inst.name;
  showEditModal.value = true;
  closeMenu();
}

function startDelete(inst: LauncherInstance) {
  deleteTargetInstance.value = inst;
  showDeleteModal.value = true;
  closeMenu();
}

function openFolder(inst: LauncherInstance) {
  void handleOpenInstanceFolder(inst.name);
  closeMenu();
}

// ── Manage view panels (content + settings) ────────────────────────────────────
type Panel = ContentType | 'settings' | null;
const activePanel = ref<Panel>(null);
const contentItems = ref<ContentEntry[]>([]);
const isLoadingContent = ref(false);
const isDragging = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const CONTENT_LABELS: Record<ContentType, string> = {
  mods: 'Mods',
  resourcepacks: 'Resource Packs',
  shaderpacks: 'Shaders',
  datapacks: 'Data Packs',
};

const isLoaderInstance = computed(() => {
  const t = managingInstance.value?.versionType;
  return t === 'fabric' || t === 'forge' || t === 'neoforge' || t === 'quilt';
});

const isContentPanel = computed(() => activePanel.value !== null && activePanel.value !== 'settings');
const activeContentType = computed(() => (isContentPanel.value ? (activePanel.value as ContentType) : null));

// Reset panels only when switching to a *different* profile, not on in-place updates.
watch(() => managingInstance.value?.name, () => { activePanel.value = null; contentItems.value = []; });

async function openPanel(panel: Panel) {
  activePanel.value = panel;
  if (panel === 'settings') { loadSettingsForm(); return; }
  if (panel) await refreshContent(panel);
}

async function refreshContent(type: ContentType) {
  if (!managingInstance.value) return;
  isLoadingContent.value = true;
  contentItems.value = await loadInstanceContent(managingInstance.value.name, type);
  isLoadingContent.value = false;
  // Update state belongs to the file list that produced it.
  contentUpdates.value = [];
  hasCheckedUpdates.value = false;
}

// ── Content version management ────────────────────────────────────────────────
const contentUpdates = ref<ContentUpdate[]>([]);
const hasCheckedUpdates = ref(false);
const isCheckingUpdates = ref(false);
const updatingFiles = ref<string[]>([]);

/** The pending update for one file, or undefined when it is current. */
const updateFor = (fileName: string) => contentUpdates.value.find((u) => u.fileName === fileName);

/**
 * Identifying unknown files runs first: a jar the launcher did not install has no project behind
 * it, so it would be skipped by the update sweep entirely — which reads as "this mod never
 * updates" rather than "the launcher does not know what it is".
 */
async function onCheckUpdates() {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  const name = managingInstance.value.name;

  isCheckingUpdates.value = true;
  const identified = await identifyContent(name, type);
  if (identified > 0) {
    contentItems.value = await loadInstanceContent(name, type);
    launcherMessage.value = t('content.identified', { n: identified });
  }
  contentUpdates.value = await checkContentUpdates(name, type);
  isCheckingUpdates.value = false;
  hasCheckedUpdates.value = true;
}

async function applyUpdate(update: ContentUpdate) {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  updatingFiles.value = [...updatingFiles.value, update.fileName];
  const ok = await changeContentVersion(
    managingInstance.value.name, type, update.fileName, update.latestVersionId);
  updatingFiles.value = updatingFiles.value.filter((f) => f !== update.fileName);
  if (ok) {
    contentUpdates.value = contentUpdates.value.filter((u) => u.fileName !== update.fileName);
    await refreshContent(type);
    // refreshContent clears the list, so put back what is still pending.
    contentUpdates.value = contentUpdates.value.filter((u) => u.fileName !== update.fileName);
  }
}

/** Updates sequentially — the backend marks the profile busy for each one. */
async function applyAllUpdates() {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  const pending = [...contentUpdates.value];
  for (const update of pending) {
    updatingFiles.value = [...updatingFiles.value, update.fileName];
    await changeContentVersion(managingInstance.value.name, type, update.fileName, update.latestVersionId);
    updatingFiles.value = updatingFiles.value.filter((f) => f !== update.fileName);
  }
  await refreshContent(type);
  await onCheckUpdates();
}

// ── Per-file version picker ───────────────────────────────────────────────────
const versionPickerFile = ref<ContentEntry | null>(null);
const versionPickerList = ref<ProjectVersion[]>([]);
const isLoadingVersionList = ref(false);

async function openVersionPicker(item: ContentEntry) {
  const type = activeContentType.value;
  if (!managingInstance.value || !type || !item.tracked) return;
  versionPickerFile.value = item;
  isLoadingVersionList.value = true;
  versionPickerList.value = await loadContentVersions(managingInstance.value.name, type, item.fileName);
  isLoadingVersionList.value = false;
}

function closeVersionPicker() {
  versionPickerFile.value = null;
  versionPickerList.value = [];
}

async function pickVersion(version: ProjectVersion) {
  const type = activeContentType.value;
  const item = versionPickerFile.value;
  if (!managingInstance.value || !type || !item) return;
  const ok = await changeContentVersion(managingInstance.value.name, type, item.fileName, version.versionId);
  closeVersionPicker();
  if (ok) await refreshContent(type);
}

// Electron 32+ removed File.path; the absolute path must be resolved via webUtils.
function resolveFilePath(file: File): string | null {
  const w = window as unknown as { require?: (m: string) => { webUtils?: { getPathForFile?: (f: File) => string } } };
  try {
    if (typeof w.require === 'function') {
      const electron = w.require('electron');
      const resolved = electron?.webUtils?.getPathForFile?.(file);
      if (resolved) return resolved;
    }
  } catch { /* fall through to legacy */ }
  const legacy = (file as unknown as { path?: string }).path;
  return legacy && legacy.length > 0 ? legacy : null;
}

function extractPaths(files: FileList | null): string[] {
  if (!files) return [];
  const paths: string[] = [];
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    if (!file) continue;
    const p = resolveFilePath(file);
    if (p) paths.push(p);
  }
  return paths;
}

async function applyAddedFiles(files: FileList | null) {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  const hadFiles = !!files && files.length > 0;
  const paths = extractPaths(files);
  if (paths.length === 0) {
    if (hadFiles) error.value = t("content.addFailed");
    return;
  }
  const ok = await addInstanceContent(managingInstance.value.name, type, paths);
  if (ok) await refreshContent(type);
}

async function onDrop(e: DragEvent) {
  isDragging.value = false;
  await applyAddedFiles(e.dataTransfer?.files ?? null);
}

async function onBrowse(e: Event) {
  const target = e.target as HTMLInputElement;
  const files = target.files;
  await applyAddedFiles(files);
  target.value = '';
}

async function onToggleContent(item: ContentEntry) {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  const updated = await toggleInstanceContent(managingInstance.value.name, type, item.fileName);
  if (updated) {
    const idx = contentItems.value.findIndex(c => c.fileName === item.fileName);
    if (idx !== -1) contentItems.value[idx] = updated;
  }
}

async function onDeleteContent(item: ContentEntry) {
  const type = activeContentType.value;
  if (!managingInstance.value || !type) return;
  const ok = await removeInstanceContent(managingInstance.value.name, type, item.fileName);
  if (ok) contentItems.value = contentItems.value.filter(c => c.fileName !== item.fileName);
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// ── Modpack import ────────────────────────────────────────────────────────────
const packFileInput = ref<HTMLInputElement | null>(null);
const isImportingPack = ref(false);
const isDraggingPack = ref(false);

const PACK_EXTENSIONS = ['.mrpack', '.zip'];

async function importPackFrom(paths: string[]) {
  const pack = paths.find((p) => PACK_EXTENSIONS.some((ext) => p.toLowerCase().endsWith(ext)));
  if (!pack) {
    error.value = t('import.wrongType');
    return;
  }
  isImportingPack.value = true;
  const created = await importModpack(pack);
  isImportingPack.value = false;
  if (created) await loadInstances();
}

async function onPackBrowse(e: Event) {
  const target = e.target as HTMLInputElement;
  await importPackFrom(extractPaths(target.files));
  target.value = '';
}

async function onPackDrop(e: DragEvent) {
  isDraggingPack.value = false;
  await importPackFrom(extractPaths(e.dataTransfer?.files ?? null));
}

// ── Modpack updating ──────────────────────────────────────────────────────────
const modpackStatus = ref<ModpackStatus | null>(null);
const isUpdatingPack = ref(false);
const packReleases = ref<ProjectVersion[]>([]);
const showReleasePicker = ref(false);

/** Loaded whenever the manage view lands on another profile. */
watch(() => managingInstance.value?.name, async (name) => {
  modpackStatus.value = null;
  packReleases.value = [];
  showReleasePicker.value = false;
  if (name) modpackStatus.value = await loadModpackStatus(name);
}, { immediate: true });

async function runPackUpdate(versionId: string) {
  if (!managingInstance.value) return;
  const name = managingInstance.value.name;
  isUpdatingPack.value = true;
  showReleasePicker.value = false;
  const jobId = await startModpackUpdate(name, versionId);
  if (jobId) {
    const finished = await trackModpackJob(jobId, name);
    if (finished) launcherMessage.value = t('modpack.updated');
  }
  isUpdatingPack.value = false;
  await loadInstances();
  modpackStatus.value = await loadModpackStatus(name);
  if (activeContentType.value) await refreshContent(activeContentType.value);
}

async function openReleasePicker() {
  if (!managingInstance.value) return;
  showReleasePicker.value = true;
  packReleases.value = await loadModpackVersions(managingInstance.value.name);
}

// ── Export ────────────────────────────────────────────────────────────────────
// Reachable both from the manage view and from a card's menu, so the target is held explicitly
// rather than assumed to be whatever profile is being managed.
const exportTargetName = ref('');
const showExportModal = ref(false);
const exportFormat = ref<ExportFormat>('mrpack');
const exportVersion = ref('1.0.0');
const isExporting = ref(false);
const exportResult = ref<ExportResult | null>(null);

function openExportFor(inst: LauncherInstance) {
  exportTargetName.value = inst.name;
  exportResult.value = null;
  exportVersion.value = '1.0.0';
  showExportModal.value = true;
  closeMenu();
}

function openExport() {
  if (!managingInstance.value) return;
  exportTargetName.value = managingInstance.value.name;
  exportResult.value = null;
  exportVersion.value = modpackStatus.value?.modpack?.versionNumber || '1.0.0';
  showExportModal.value = true;
  closeMenu();
}

async function runExport() {
  if (!exportTargetName.value) return;
  isExporting.value = true;
  exportResult.value = await exportInstance(
    exportTargetName.value, exportFormat.value, exportVersion.value);
  isExporting.value = false;
}

// ── Settings form ───────────────────────────────────────────────────────────────
/**
 * The numeric fields are bound to `<input type="number">`, and Vue applies the `.number` modifier
 * to those implicitly — so they hold a number as soon as the user types one, and the empty string
 * only while the field is blank. Both shapes have to be accepted on the way back out.
 */
interface SettingsForm {
  maxMemoryMb: string | number;
  minMemoryMb: string | number;
  jvmArgs: string;
  resolutionWidth: string | number;
  resolutionHeight: string | number;
  javaPath: string;
}

const settingsForm = reactive<SettingsForm>({
  maxMemoryMb: '',
  minMemoryMb: '',
  jvmArgs: '',
  resolutionWidth: '',
  resolutionHeight: '',
  javaPath: '',
});
const isSavingSettings = ref(false);
const settingsSaved = ref(false);

/**
 * What an empty field falls back to at launch, so the placeholders tell the truth about what the
 * profile will actually use rather than showing a hardcoded number.
 */
const settingsPlaceholders = computed(() => ({
  maxMemoryMb: String(settings.value.defaultMaxMemoryMb),
  minMemoryMb: String(settings.value.defaultMinMemoryMb),
  jvmArgs: settings.value.defaultJvmArgs || '-XX:+UseG1GC',
  javaPath: t('profiles.settingsJavaAuto', { version: managingInstance.value?.javaMajorVersion ?? '' }),
}));

function loadSettingsForm() {
  settingsSaved.value = false;
  const s = managingInstance.value?.settings;
  settingsForm.maxMemoryMb = s?.maxMemoryMb != null ? String(s.maxMemoryMb) : '';
  settingsForm.minMemoryMb = s?.minMemoryMb != null ? String(s.minMemoryMb) : '';
  settingsForm.jvmArgs = s?.jvmArgs ?? '';
  settingsForm.resolutionWidth = s?.resolutionWidth != null ? String(s.resolutionWidth) : '';
  settingsForm.resolutionHeight = s?.resolutionHeight != null ? String(s.resolutionHeight) : '';
  settingsForm.javaPath = s?.javaPath ?? '';
}

/** An empty, blank or non-positive field means "no override" and is stored as null. */
function parseIntOrNull(value: string | number): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0 ? Math.trunc(value) : null;
  }
  const trimmed = String(value ?? '').trim();
  if (trimmed === '') return null;
  const parsed = Number.parseInt(trimmed, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

async function saveSettings() {
  if (!managingInstance.value) return;
  const payload: InstanceSettings = {
    maxMemoryMb: parseIntOrNull(settingsForm.maxMemoryMb),
    minMemoryMb: parseIntOrNull(settingsForm.minMemoryMb),
    jvmArgs: settingsForm.jvmArgs.trim() === '' ? null : settingsForm.jvmArgs.trim(),
    resolutionWidth: parseIntOrNull(settingsForm.resolutionWidth),
    resolutionHeight: parseIntOrNull(settingsForm.resolutionHeight),
    javaPath: settingsForm.javaPath.trim() === '' ? null : settingsForm.javaPath.trim(),
  };
  isSavingSettings.value = true;
  const ok = await handleUpdateInstanceSettings(managingInstance.value.name, payload);
  isSavingSettings.value = false;
  if (ok) {
    // The saved profile is written back into the shared list, which `managingInstance` reads
    // from — so reloading the form now shows exactly what the backend stored.
    loadSettingsForm();
    settingsSaved.value = true;
    window.setTimeout(() => { settingsSaved.value = false; }, 2500);
  }
}
</script>

<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'profiles' ? 'flex' : 'hidden'" @click="closeMenu">

    <!-- ── Grid view ─────────────────────────────────────────────────────────── -->
    <template v-if="!managingInstance">
      <div class="flex shrink-0 flex-col gap-3 px-4 pt-4 md:px-6">
        <div class="flex flex-wrap items-center gap-2">
          <div class="flex flex-wrap gap-[5px]">
            <button v-for="f in filters" :key="f" type="button"
              class="rounded-md border px-[13px] py-[5px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] transition-all duration-200"
              :class="profileFilter === f
                ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
                : 'border-white/10 bg-white/[0.03] text-white/40 hover:bg-white/[0.07] hover:text-white/70'"
              @click.stop="profileFilter = f">
              {{ f === 'ALL' ? 'ALLE' : f }}
            </button>
          </div>
          <div class="flex-1"></div>
          <button type="button" :disabled="isImportingPack" :title="t('import.hint')"
            class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
            @click.stop="packFileInput?.click()">
            <Icon :icon="isImportingPack ? 'lucide:loader-2' : 'lucide:download'" class="size-[11px]"
              :class="isImportingPack ? 'animate-spin' : ''" />IMPORT
          </button>
          <input ref="packFileInput" type="file" accept=".mrpack,.zip" class="hidden" @change="onPackBrowse" />
          <button type="button"
            class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
            @click.stop="showCreateModal = true">
            <Icon icon="lucide:plus" class="size-[11px]" />NEUES PROFIL
          </button>
        </div>
      </div>

      <div class="mt-4 shrink-0 px-4 text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40 md:px-6">
        PROFILE ({{ filteredInstances.length }})
      </div>

      <div class="relative overflow-y-scroll grid grid-cols-[repeat(auto-fit,minmax(250px,1fr))] gap-3 px-4 py-3 md:px-6"
        @dragover.prevent="isDraggingPack = true" @dragleave.prevent="isDraggingPack = false"
        @drop.prevent="onPackDrop">

        <!-- Dropping a .mrpack or CurseForge .zip here imports it as a new profile. -->
        <div v-if="isDraggingPack"
          class="pointer-events-none absolute inset-2 z-20 flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)] backdrop-blur-sm">
          <Icon icon="lucide:package-plus" class="size-8 text-[var(--primary)]" />
          <span class="text-[length:var(--text-base)] font-semibold text-[var(--primary)]">{{ t("import.title") }}</span>
          <span class="text-[length:var(--text-2xs)] text-white/50">{{ t("import.hint") }}</span>
        </div>

        <!-- New profile card -->
        <button type="button"
          class="flex h-48 flex-col items-center justify-center gap-2.5 rounded-xl border border-dashed border-white/15 bg-[var(--surface-panel-muted)] transition-all duration-200 hover:border-[var(--accent-border-emphasis)] hover:bg-[var(--accent-bg-soft)]"
          @click.stop="showCreateModal = true">
          <div
            class="flex size-[38px] items-center justify-center rounded-full border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] text-[var(--primary)]">
            <Icon icon="lucide:plus" class="size-4" />
          </div>
          <div class="text-center">
            <div class="text-[length:var(--text-base)] font-semibold text-white">{{ t("profiles.create") }}</div>
            <div class="text-[length:var(--text-2xs)] text-white/30">{{ t("profiles.emptyHint") }}</div>
          </div>
        </button>

        <!-- Pending (creating) cards -->
        <div v-for="pending in visiblePendingInstances" :key="pending.id"
          class="relative h-48 overflow-hidden rounded-xl border bg-[var(--surface-panel)]"
          :class="pending.failed ? 'border-red-500/40' : 'border-white/10'">
          <!-- Image area -->
          <div
            class="relative flex h-[100px] items-center justify-center overflow-hidden rounded-t-xl bg-[linear-gradient(135deg,#0a1535,#122050)]">
            <span class="text-[40px] opacity-40">{{ versionEmoji(pending.platformId) }}</span>
            <!-- Progress bar -->
            <div v-if="!pending.failed" class="absolute bottom-0 left-0 right-0 h-[3px] bg-white/10 overflow-hidden">
              <div class="h-full w-1/2 bg-[var(--primary)] rounded-full animate-[shimmer_1.6s_ease-in-out_infinite]" />
            </div>
            <div v-else class="absolute bottom-0 left-0 right-0 h-[3px] bg-red-500/60" />
          </div>
          <!-- Info -->
          <div class="px-3 py-2.5">
            <div class="mb-1 truncate text-[length:var(--text-md-plus)] font-semibold text-white">{{ pending.name }}
            </div>
            <div v-if="!pending.failed" class="flex items-center gap-1.5 text-[length:var(--text-2xs)] text-white/45">
              <span class="inline-block size-1.5 shrink-0 rounded-full bg-[var(--primary)] animate-pulse" />
              {{ pending.failed ? (pending.errorMessage ?? t("profiles.createFailed")) : t("profiles.installing") }}
            </div>
            <div v-else class="text-[length:var(--text-2xs)] text-red-400 leading-[1.4]">
              {{ pending.errorMessage }}
            </div>
          </div>
          <!-- Dismiss on error -->
          <button v-if="pending.failed" type="button"
            class="absolute right-2 top-2 flex size-5 items-center justify-center rounded bg-white/10 text-white/50 hover:bg-white/20 hover:text-white"
            @click.stop="pendingInstances.splice(pendingInstances.indexOf(pending), 1)">
            <Icon icon="lucide:x" class="size-3" />
          </button>
        </div>

        <!-- Instance cards -->
        <div v-for="inst in filteredInstances" :key="inst.slug"
          class="group relative h-48 overflow-visible rounded-xl border bg-[var(--surface-panel)] text-left transition-all duration-200"
          :class="inst.busy
            ? 'border-[var(--accent-border)] cursor-progress'
            : 'border-white/10 cursor-pointer hover:-translate-y-1 hover:border-[var(--accent-border-hover)] hover:shadow-[0_12px_32px_rgba(0,0,0,.4),0_0_24px_rgba(var(--primary-rgb),.1)]'"
          @click.stop="openManage(inst)">
          <!-- Card image area -->
          <div
            class="relative flex h-[100px] items-center justify-center overflow-hidden rounded-t-xl pointer-events-none">
            <!-- Profiles from a modpack show its artwork; hand-made ones keep the version emoji. -->
            <template v-if="inst.packIconUrl">
              <img :src="inst.packIconUrl" alt=""
                class="absolute inset-0 size-full object-cover transition-transform duration-300 group-hover:scale-105"
                loading="lazy" @error="handleImgError" />
              <div class="absolute inset-0 bg-black/25"></div>
            </template>
            <div v-else
              class="absolute inset-0 flex items-center justify-center text-[40px] opacity-70 transition-transform duration-300 group-hover:scale-105"
              :class="versionGradientClass(inst.versionType)">
              {{ versionEmoji(inst.versionType) }}
            </div>
            <div class="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-[rgba(8,18,34,.95)]">
            </div>

            <!-- Play button overlay — pointer-events enabled, stops card click.
                 Suppressed while the profile is still being installed. -->
            <div v-if="!inst.busy"
              class="pointer-events-auto absolute inset-0 flex items-center justify-center gap-2 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
              <button type="button"
                class="h-10 inline-flex items-center gap-2 bg-[var(--primary)] rounded-[7px] px-4 py-2 text-[length:var(--text-base)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!authData || isLaunching" @click.stop="playInstance(inst)">
                <Icon icon="lucide:play" class="size-[10px]" />{{ t("profiles.play") }}
              </button>
              <button v-if="selectedInstanceName !== inst.name" type="button"
                class="h-10 inline-flex items-center gap-2 rounded-r-[7px] bg-(--surface-input) px-4 py-2 text-[length:var(--text-base)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                @click.stop="selectInstance(inst)">
                <Icon icon="mynaui:arrow-left-right" class="size-[24px] brightness-60" />
              </button>
            </div>

            <div class="absolute left-[7px] top-[7px] flex gap-1 pointer-events-none">
              <span v-if="inst.busy"
                class="inline-flex items-center gap-1 rounded border border-[var(--accent-border)] bg-[var(--accent-bg-strong)] px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--primary)]">
                <Icon icon="lucide:loader-2" class="size-[8px] animate-spin" />INSTALLING</span>
              <span v-else-if="inst.running"
                class="rounded border border-[var(--success-border)] bg-[var(--success-bg)] px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--accent)]">▶
                ACTIVE</span>
              <span v-else-if="selectedInstanceName === inst.name"
                class="rounded border border-[var(--primary-border)] bg-(--primary)/20 px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--primary)]">▶
                SELECTED</span>
            </div>
          </div>

          <!-- Card info -->
          <div class="px-3 py-2.5 pointer-events-none">
            <div class="mb-1.5 truncate text-[length:var(--text-md-plus)] font-semibold leading-[1.35] text-white">{{
              inst.name }}</div>
            <InstallProgress v-if="inst.busy" compact :stage="inst.busyStage ?? null"
              :completed="inst.busyCompleted ?? 0" :total="inst.busyTotal ?? 0" :percent="inst.busyPercent ?? -1" />
            <div v-else class="flex flex-wrap gap-[5px] text-[length:var(--text-2xs)] text-white/60">
              <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#4caf50]"></span>{{
                formatLoaderId(inst.versionId) }}</span>
              <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#6c63ff]"></span>Java
                {{ inst.javaMajorVersion }}</span>
              <span class="text-white/25">{{ formatRelativeDate(inst.lastPlayedAt) }}</span>
            </div>
          </div>

          <!-- 3-dot menu -->
          <div v-if="!inst.busy" class="absolute right-[7px] top-[7px] z-10" @click.stop>
            <button type="button"
              class="flex size-6 items-center justify-center rounded-md border border-white/10 bg-black/40 text-white/40 opacity-0 backdrop-blur-sm transition-all duration-150 group-hover:opacity-100 hover:!text-white hover:bg-white/15"
              @click="toggleMenu(inst.slug)">
              <Icon icon="lucide:more-vertical" class="size-3" />
            </button>
            <div v-if="openMenuSlug === inst.slug"
              class="absolute right-0 top-8 z-20 min-w-[148px] overflow-hidden rounded-[9px] border border-white/10 bg-[var(--surface-panel-strong)] shadow-[0_8px_24px_rgba(0,0,0,.5)] backdrop-blur-md">
              <button type="button"
                class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-white/70 transition-colors hover:bg-white/8 hover:text-white"
                @click="startEdit(inst)">
                <Icon icon="lucide:pencil" class="size-3.5 shrink-0" />Rename
              </button>
              <button type="button"
                class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-white/70 transition-colors hover:bg-white/8 hover:text-white"
                @click="openFolder(inst)">
                <Icon icon="lucide:folder-open" class="size-3.5 shrink-0" />Open folder
              </button>
              <button type="button"
                class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-white/70 transition-colors hover:bg-white/8 hover:text-white"
                @click="openExportFor(inst)">
                <Icon icon="lucide:package-open" class="size-3.5 shrink-0" />{{ t("export.title") }}
              </button>
              <div class="mx-2 my-0.5 h-px bg-white/7"></div>
              <button type="button"
                class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-[var(--danger-text,#f87171)] transition-colors hover:bg-red-500/10"
                @click="startDelete(inst)">
                <Icon icon="lucide:trash-2" class="size-3.5 shrink-0" />Delete
              </button>
            </div>
          </div>
        </div>

        <div v-if="filteredInstances.length === 0 && !isLoadingInstances"
          class="col-[1/-1] py-10 text-center text-[length:var(--text-md)] text-white/40">
          No profile found. Create a new one or import one.
        </div>
      </div>
    </template>

    <!-- ── Manage / detail view ───────────────────────────────────────────────── -->
    <template v-else>
      <div class="flex flex-1 flex-col overflow-hidden px-4 pt-4 md:px-6" @click.stop>
        <!-- Header -->
        <div class="mb-5 flex items-center gap-3">
          <button type="button"
            class="flex size-8 items-center justify-center rounded-lg border border-white/10 bg-white/5 text-white/50 transition-all hover:bg-white/10 hover:text-white"
            @click="closeManage">
            <Icon icon="lucide:arrow-left" class="size-4" />
          </button>
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">{{
            managingInstance.name.toUpperCase() }}</div>
          <span v-if="managingInstance.running"
            class="rounded border border-[var(--success-border)] bg-[var(--success-bg)] px-2 py-0.5 text-[10px] font-bold tracking-[0.08em] text-[var(--accent)]">▶
            AKTIV</span>
        </div>

        <div class="flex flex-1 gap-5 overflow-hidden">
          <!-- Left: hero card -->
          <div
            class="relative flex w-56 h-fit shrink-0 flex-col gap-4 overflow-hidden rounded-xl border border-white/10 bg-(--surface-panel)">
            <div class="relative flex h-28 items-center justify-center overflow-hidden text-[60px]"
              :class="managingInstance.packIconUrl ? '' : versionGradientClass(managingInstance.versionType)">
              <img v-if="managingInstance.packIconUrl" :src="managingInstance.packIconUrl" alt=""
                class="size-full object-cover" loading="lazy" @error="handleImgError" />
              <template v-else>{{ versionEmoji(managingInstance.versionType) }}</template>
            </div>
            <div class="flex flex-col gap-1.5 p-3">
              <div class="truncate text-[length:var(--text-md)] font-semibold text-white">{{ managingInstance.name }}
              </div>
              <div class="text-[length:var(--text-xs)] text-white/50">{{ formatLoaderId(managingInstance.versionId) }}
              </div>
            </div>
            <!-- Play button + panel nav -->
            <div class="px-3 pb-3 flex flex-col gap-2">
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--accent-border-strong) bg-(--primary) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!authData || isLaunching || managingInstance.running"
                @click="playInstance(managingInstance)">
                <Icon icon="lucide:play" class="size-[10px]" />PLAY
              </button>
              <button v-if="isLoaderInstance" type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] transition-all"
                :class="activePanel === 'mods' ? 'border-(--accent-border-strong) bg-(--accent-bg-strong) text-(--primary)' : 'border-(--surface-panel-strong) bg-(--surface-input) text-white hover:bg-white/10'"
                @click="openPanel('mods')">
                <Icon icon="lucide:puzzle" class="size-[10px]" />Mods
              </button>
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] transition-all"
                :class="activePanel === 'resourcepacks' ? 'border-(--accent-border-strong) bg-(--accent-bg-strong) text-(--primary)' : 'border-(--surface-panel-strong) bg-(--surface-input) text-white hover:bg-white/10'"
                @click="openPanel('resourcepacks')">
                <Icon icon="lucide:palette" class="size-[10px]" />Resource Packs
              </button>
              <button v-if="isLoaderInstance" type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] transition-all"
                :class="activePanel === 'shaderpacks' ? 'border-(--accent-border-strong) bg-(--accent-bg-strong) text-(--primary)' : 'border-(--surface-panel-strong) bg-(--surface-input) text-white hover:bg-white/10'"
                @click="openPanel('shaderpacks')">
                <Icon icon="lucide:sparkles" class="size-[10px]" />Shaders
              </button>
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] transition-all"
                :class="activePanel === 'datapacks' ? 'border-(--accent-border-strong) bg-(--accent-bg-strong) text-(--primary)' : 'border-(--surface-panel-strong) bg-(--surface-input) text-white hover:bg-white/10'"
                @click="openPanel('datapacks')">
                <Icon icon="lucide:database" class="size-[10px]" />Data Packs
              </button>
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] transition-all"
                :class="activePanel === 'settings' ? 'border-(--accent-border-strong) bg-(--accent-bg-strong) text-(--primary)' : 'border-(--surface-panel-strong) bg-(--surface-input) text-white hover:bg-white/10'"
                @click="openPanel('settings')">
                <Icon icon="lucide:sliders-horizontal" class="size-[10px]" />Settings
              </button>
            </div>
          </div>

          <!-- Right: profile info (always on top) + active panel -->
          <div class="flex flex-1 flex-col gap-4 overflow-hidden">

            <!-- Profile info — always visible across all sub-tabs -->
            <div class="shrink-0 rounded-xl border border-white/8 bg-[var(--surface-panel)] p-4">
              <div class="mb-3 text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40">INFO</div>
              <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div class="flex flex-col gap-0.5">
                  <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">VERSION</div>
                  <div class="truncate text-[length:var(--text-sm)] font-medium text-white">{{ formatLoaderId(managingInstance.versionId) }}</div>
                </div>
                <div class="flex flex-col gap-0.5">
                  <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">JAVA</div>
                  <div class="text-[length:var(--text-sm)] font-medium text-white">Java {{ managingInstance.javaMajorVersion }}</div>
                </div>
                <div class="flex flex-col gap-0.5">
                  <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">ERSTELLT</div>
                  <div class="text-[length:var(--text-sm)] font-medium text-white">{{ formatRelativeDate(managingInstance.createdAt) }}</div>
                </div>
                <div class="flex flex-col gap-0.5">
                  <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">ZULETZT GESPIELT</div>
                  <div class="text-[length:var(--text-sm)] font-medium text-white">{{ formatRelativeDate(managingInstance.lastPlayedAt) }}</div>
                </div>
              </div>
            </div>

            <!-- Modpack banner — only for profiles that came from a pack -->
            <div v-if="modpackStatus?.modpack"
              class="shrink-0 rounded-xl border px-4 py-3"
              :class="modpackStatus.updateAvailable
                ? 'border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)]'
                : 'border-white/8 bg-[var(--surface-panel)]'">
              <div class="flex flex-wrap items-center gap-3">
                <Icon :icon="modpackStatus.updateAvailable ? 'lucide:arrow-up-circle' : 'lucide:package-check'"
                  class="size-4 shrink-0" :class="modpackStatus.updateAvailable ? 'text-[var(--primary)]' : 'text-white/35'" />
                <div class="min-w-0 flex-1">
                  <div class="truncate text-[length:var(--text-sm)] font-semibold text-white">
                    {{ modpackStatus.modpack.name || t("modpack.title") }}
                    <span v-if="modpackStatus.modpack.versionNumber" class="font-normal text-white/40">
                      · {{ modpackStatus.modpack.versionNumber }}
                    </span>
                  </div>
                  <div class="text-[length:var(--text-2xs)] text-white/40">
                    <template v-if="isUpdatingPack">{{ t("modpack.updating") }}</template>
                    <template v-else-if="modpackStatus.updateAvailable">
                      {{ t("modpack.updateAvailable", { version: modpackStatus.latestVersionNumber }) }}
                      · {{ t("modpack.keepsContent") }}
                    </template>
                    <template v-else-if="modpackStatus.modpack.sourceFile">
                      {{ t("modpack.imported", { file: modpackStatus.modpack.sourceFile }) }}
                    </template>
                    <template v-else-if="modpackStatus.reason">{{ modpackStatus.reason }}</template>
                    <template v-else>{{ t("modpack.upToDate") }}</template>
                  </div>
                </div>
                <div class="flex shrink-0 items-center gap-1.5">
                  <button v-if="modpackStatus.updateAvailable" type="button" :disabled="isUpdatingPack"
                    class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3 py-1.5 text-[length:var(--text-sm)] font-semibold text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                    @click="runPackUpdate(modpackStatus.latestVersionId)">
                    <Icon :icon="isUpdatingPack ? 'lucide:loader-2' : 'lucide:arrow-up'" class="size-3"
                      :class="isUpdatingPack ? 'animate-spin' : ''" />{{ t("modpack.update") }}
                  </button>
                  <button v-if="modpackStatus.modpack.updatable" type="button" :disabled="isUpdatingPack"
                    class="inline-flex items-center gap-1 rounded-[6px] border border-white/10 bg-white/5 px-2.5 py-1.5 text-[length:var(--text-2xs)] font-semibold text-white/50 transition-all hover:bg-white/10 hover:text-white disabled:opacity-50"
                    @click="openReleasePicker">
                    <Icon icon="lucide:history" class="size-3" />{{ t("modpack.chooseRelease") }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Content panel (mods / resourcepacks / shaderpacks / datapacks) -->
            <div v-if="isContentPanel && activeContentType"
              class="flex flex-1 flex-col overflow-hidden rounded-xl border border-white/8 bg-[var(--surface-panel)]">
              <div class="flex items-center justify-between border-b border-white/7 px-4 py-3">
                <div class="flex items-center gap-2 text-[length:var(--text-sm)] font-bold tracking-[0.1em] text-white">
                  <Icon icon="lucide:package" class="size-3.5 text-white/40" />
                  {{ CONTENT_LABELS[activeContentType].toUpperCase() }}
                  <span class="text-white/35 font-medium">({{ contentItems.length }})</span>
                </div>
                <div class="flex items-center gap-1.5">
                  <button type="button" :disabled="isCheckingUpdates || contentItems.length === 0"
                    class="inline-flex items-center gap-1.5 rounded-[6px] border border-white/10 bg-white/5 px-2.5 py-1.5 text-[length:var(--text-2xs)] font-semibold tracking-[0.06em] text-white/50 transition-all hover:bg-white/10 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                    @click="onCheckUpdates">
                    <Icon :icon="isCheckingUpdates ? 'lucide:loader-2' : 'lucide:refresh-ccw-dot'" class="size-3"
                      :class="isCheckingUpdates ? 'animate-spin' : ''" />
                    {{ isCheckingUpdates ? t("content.checking") : t("content.checkUpdates") }}
                  </button>
                  <button v-if="contentUpdates.length > 0" type="button" :disabled="updatingFiles.length > 0"
                    class="inline-flex items-center gap-1.5 rounded-[6px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-2.5 py-1.5 text-[length:var(--text-2xs)] font-semibold tracking-[0.06em] text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:opacity-50"
                    @click="applyAllUpdates">
                    <Icon icon="lucide:arrow-up" class="size-3" />{{ t("content.updateAll") }} ({{ contentUpdates.length }})
                  </button>
                  <button type="button"
                    class="inline-flex items-center gap-1.5 rounded-[6px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-2.5 py-1.5 text-[length:var(--text-2xs)] font-semibold tracking-[0.06em] text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)]"
                    @click="fileInput?.click()">
                    <Icon icon="lucide:plus" class="size-3" />{{ t("content.addFiles") }}
                  </button>
                  <button type="button"
                    class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all hover:bg-white/10 hover:text-white"
                    title="Aktualisieren" @click="refreshContent(activeContentType)">
                    <Icon icon="lucide:refresh-cw" class="size-3" />
                  </button>
                </div>
              </div>
              <div v-if="hasCheckedUpdates"
                class="shrink-0 border-b border-white/7 px-4 py-1.5 text-[length:var(--text-2xs)]"
                :class="contentUpdates.length > 0 ? 'text-[var(--primary)]' : 'text-white/35'">
                {{ contentUpdates.length > 0
                  ? t("content.updatesFound", { n: contentUpdates.length })
                  : t("content.upToDate") }}
              </div>
              <input ref="fileInput" type="file" multiple class="hidden" @change="onBrowse" />

              <!-- Dropzone + list -->
              <div class="relative flex flex-1 flex-col overflow-hidden"
                @dragover.prevent="isDragging = true" @dragleave.prevent="isDragging = false" @drop.prevent="onDrop">
                <div v-if="isDragging"
                  class="pointer-events-none absolute inset-2 z-10 flex flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)] backdrop-blur-sm">
                  <Icon icon="lucide:download" class="size-7 text-[var(--primary)]" />
                  <span class="text-[length:var(--text-sm)] font-semibold text-[var(--primary)]">{{ t("content.addFiles") }}</span>
                </div>

                <div class="flex-1 overflow-y-auto p-2.5">
                  <div v-if="isLoadingContent" class="py-10 text-center text-[length:var(--text-sm)] text-white/40">Lädt…</div>
                  <div v-else-if="contentItems.length === 0"
                    class="flex h-full min-h-[160px] flex-col items-center justify-center gap-2 text-center">
                    <Icon icon="lucide:inbox" class="size-8 text-white/20" />
                    <div class="text-[length:var(--text-sm)] text-white/40">{{ t("content.empty") }}</div>
                    <div class="text-[length:var(--text-2xs)] text-white/25">{{ t("content.emptyHint") }}</div>
                  </div>
                  <div v-else class="flex flex-col gap-1.5">
                    <div v-for="item in contentItems" :key="item.fileName"
                      class="group flex items-center justify-between gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3 py-2.5"
                      :class="!item.enabled ? 'opacity-50' : ''">
                      <div class="flex min-w-0 items-center gap-2.5">
                        <!-- The project's own icon once it is known; a glyph until then. -->
                        <img v-if="item.source?.iconUrl" :src="item.source.iconUrl" alt=""
                          class="size-7 shrink-0 rounded-[6px] border border-white/10 bg-black/20 object-cover"
                          loading="lazy" @error="handleImgError" />
                        <div v-else
                          class="flex size-7 shrink-0 items-center justify-center rounded-[6px] border border-white/8 bg-white/5">
                          <Icon icon="lucide:file-archive" class="size-3.5 text-white/35" />
                        </div>
                        <div class="min-w-0">
                          <div class="truncate text-[length:var(--text-sm)] font-medium text-white">
                            {{ item.source?.projectName || item.fileName }}
                          </div>
                          <div class="flex flex-wrap items-center gap-x-1.5 text-[length:var(--text-2xs)] text-white/35">
                            <span v-if="item.source?.versionNumber"
                              class="rounded-[4px] bg-white/8 px-1.5 py-px font-mono text-white/55">
                              {{ item.source.versionNumber }}
                            </span>
                            <span v-else-if="!item.tracked" :title="t('content.untrackedHint')"
                              class="rounded-[4px] bg-white/5 px-1.5 py-px text-white/30">
                              {{ t("content.untracked") }}
                            </span>
                            <span>{{ formatBytes(item.size) }}</span>
                            <span v-if="!item.enabled">· deaktiviert</span>
                            <span v-if="updateFor(item.fileName)" class="text-[var(--primary)]">
                              → {{ updateFor(item.fileName)!.latestVersionNumber }}
                            </span>
                          </div>
                        </div>
                      </div>
                      <div class="flex shrink-0 items-center gap-1.5">
                        <button v-if="updateFor(item.fileName)" type="button"
                          :disabled="updatingFiles.includes(item.fileName)"
                          class="inline-flex items-center gap-1 rounded-[6px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-2 py-1 text-[length:var(--text-2xs)] font-semibold text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:opacity-50"
                          @click="applyUpdate(updateFor(item.fileName)!)">
                          <Icon :icon="updatingFiles.includes(item.fileName) ? 'lucide:loader-2' : 'lucide:arrow-up'"
                            class="size-3" :class="updatingFiles.includes(item.fileName) ? 'animate-spin' : ''" />
                          {{ t("content.update") }}
                        </button>
                        <button v-if="item.tracked" type="button"
                          class="flex size-7 items-center justify-center rounded-md border border-white/8 bg-white/[0.03] text-white/35 transition-all hover:bg-white/10 hover:text-white"
                          :title="t('content.changeVersion')" @click="openVersionPicker(item)">
                          <Icon icon="lucide:history" class="size-3.5" />
                        </button>
                        <button type="button"
                          class="inline-flex items-center gap-1 rounded-[6px] border px-2 py-1 text-[length:var(--text-2xs)] font-semibold transition-all"
                          :class="item.enabled
                            ? 'border-[var(--success-border)] bg-[var(--success-bg)] text-[var(--accent)]'
                            : 'border-white/10 bg-white/5 text-white/40 hover:bg-white/10'"
                          @click="onToggleContent(item)">
                          <Icon :icon="item.enabled ? 'lucide:check' : 'lucide:x'" class="size-3" />
                          {{ item.enabled ? 'Aktiv' : 'Aus' }}
                        </button>
                        <button type="button"
                          class="flex size-7 items-center justify-center rounded-md border border-white/8 bg-white/[0.03] text-white/35 transition-all hover:border-red-500/30 hover:bg-red-500/10 hover:text-red-400"
                          :title="t('common.delete')" @click="onDeleteContent(item)">
                          <Icon icon="lucide:trash-2" class="size-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Settings panel -->
            <div v-else-if="activePanel === 'settings'"
              class="flex flex-1 flex-col overflow-hidden rounded-xl border border-white/8 bg-[var(--surface-panel)]">
              <div class="flex items-center justify-between border-b border-white/7 px-4 py-3">
                <div class="flex items-center gap-2 text-[length:var(--text-sm)] font-bold tracking-[0.1em] text-white">
                  <Icon icon="lucide:sliders-horizontal" class="size-3.5 text-white/40" />EINSTELLUNGEN
                </div>
                <span v-if="settingsSaved"
                  class="inline-flex items-center gap-1 rounded-[5px] border border-[var(--success-border)] bg-[var(--success-bg)] px-2 py-0.5 text-[length:var(--text-2xs)] font-bold tracking-[0.06em] text-[var(--accent)]">
                  <Icon icon="lucide:check" class="size-3" />Gespeichert
                </span>
              </div>
              <div class="flex-1 overflow-y-auto p-4">
                <div class="flex flex-col gap-5">
                  <!-- Memory -->
                  <div class="flex flex-col gap-2">
                    <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">ARBEITSSPEICHER (MB)</label>
                    <div class="flex gap-3">
                      <div class="flex flex-1 flex-col gap-1">
                        <span class="text-[length:var(--text-2xs)] text-white/35">Max (-Xmx)</span>
                        <input v-model="settingsForm.maxMemoryMb" type="number" min="512" step="256"
                          :placeholder="settingsPlaceholders.maxMemoryMb"
                          class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                      </div>
                      <div class="flex flex-1 flex-col gap-1">
                        <span class="text-[length:var(--text-2xs)] text-white/35">Min (-Xms)</span>
                        <input v-model="settingsForm.minMemoryMb" type="number" min="256" step="256"
                          :placeholder="settingsPlaceholders.minMemoryMb"
                          class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                      </div>
                    </div>
                  </div>
                  <!-- Resolution -->
                  <div class="flex flex-col gap-2">
                    <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">FENSTERGRÖSSE</label>
                    <div class="flex gap-3">
                      <div class="flex flex-1 flex-col gap-1">
                        <span class="text-[length:var(--text-2xs)] text-white/35">Breite</span>
                        <input v-model="settingsForm.resolutionWidth" type="number" min="640" step="1" placeholder="1280"
                          class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                      </div>
                      <div class="flex flex-1 flex-col gap-1">
                        <span class="text-[length:var(--text-2xs)] text-white/35">Höhe</span>
                        <input v-model="settingsForm.resolutionHeight" type="number" min="480" step="1" placeholder="720"
                          class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                      </div>
                    </div>
                  </div>
                  <!-- JVM args -->
                  <div class="flex flex-col gap-1.5">
                    <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">ZUSÄTZLICHE JVM-ARGUMENTE</label>
                    <input v-model="settingsForm.jvmArgs" type="text" :placeholder="settingsPlaceholders.jvmArgs"
                      class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 font-mono text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                  </div>
                  <!-- Java override -->
                  <div class="flex flex-col gap-1.5">
                    <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">JAVA-PFAD (OPTIONAL)</label>
                    <input v-model="settingsForm.javaPath" type="text" :placeholder="settingsPlaceholders.javaPath"
                      class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 font-mono text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
                    <span class="text-[length:var(--text-2xs)] text-white/30">Pfad zur java-Executable oder zum JDK-Verzeichnis. Leer = automatisch.</span>
                  </div>
                  <div class="flex justify-end gap-2 pt-1">
                    <button type="button"
                      class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all hover:bg-white/10"
                      @click="activePanel = null">
                      {{ t("common.cancel") }}
                    </button>
                    <button type="button"
                      class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
                      :disabled="isSavingSettings" @click="saveSettings">
                      <Icon :icon="isSavingSettings ? 'lucide:loader-2' : 'lucide:save'" class="size-[12px]" :class="isSavingSettings ? 'animate-spin' : ''" />
                      {{ t("common.save") }}
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Default: actions -->
            <div v-else class="flex flex-1 flex-col overflow-hidden rounded-xl border border-white/8 bg-[var(--surface-panel)]">
              <div class="border-b border-white/7 px-4 py-3 text-[length:var(--text-sm)] font-bold tracking-[0.1em] text-white">AKTIONEN</div>
              <div class="flex-1 overflow-y-auto p-4">
                <div class="flex flex-col gap-1.5">
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3.5 py-2.5 text-[length:var(--text-sm)] text-white/70 transition-all hover:bg-white/8 hover:text-white"
                    @click="startEdit(managingInstance)">
                    <Icon icon="lucide:pencil" class="size-3.5 shrink-0 text-white/40" />
                    <span>{{ t("common.rename") }}</span>
                  </button>
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3.5 py-2.5 text-[length:var(--text-sm)] text-white/70 transition-all hover:bg-white/8 hover:text-white"
                    @click="openFolder(managingInstance)">
                    <Icon icon="lucide:folder-open" class="size-3.5 shrink-0 text-white/40" />
                    <span>{{ t("profiles.openFolder") }}</span>
                  </button>
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3.5 py-2.5 text-[length:var(--text-sm)] text-white/70 transition-all hover:bg-white/8 hover:text-white"
                    @click="openExport">
                    <Icon icon="lucide:package-open" class="size-3.5 shrink-0 text-white/40" />
                    <span>{{ t("export.title") }}</span>
                  </button>
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-red-500/20 bg-red-500/[0.06] px-3.5 py-2.5 text-[length:var(--text-sm)] text-[var(--danger-text,#f87171)] transition-all hover:bg-red-500/15"
                    :disabled="managingInstance.running" @click="startDelete(managingInstance)">
                    <Icon icon="lucide:trash-2" class="size-3.5 shrink-0" />
                    <span>{{ t("common.delete") }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>

  <!-- Version picker for one installed file -->
  <Teleport to="body">
    <div v-if="versionPickerFile"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="closeVersionPicker">
      <div class="flex max-h-[70vh] w-full max-w-[520px] flex-col rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="min-w-0">
            <div class="truncate text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">
              {{ t("content.chooseVersion").toUpperCase() }}
            </div>
            <div class="truncate text-[length:var(--text-2xs)] text-white/40">
              {{ versionPickerFile.source?.projectName || versionPickerFile.fileName }}
            </div>
          </div>
          <button type="button"
            class="flex size-7 shrink-0 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all hover:bg-white/10 hover:text-white"
            @click="closeVersionPicker">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-3">
          <div v-if="isLoadingVersionList" class="py-8 text-center text-[length:var(--text-sm)] text-white/40">
            {{ t("content.checking") }}
          </div>
          <div v-else-if="versionPickerList.length === 0"
            class="py-8 text-center text-[length:var(--text-sm)] text-white/40">
            {{ t("install.noCompatibleVersion") }}
          </div>
          <div v-else class="flex flex-col gap-1.5">
            <button v-for="version in versionPickerList" :key="version.versionId" type="button"
              :disabled="!version.downloadable"
              class="flex items-center justify-between gap-3 rounded-lg border px-3 py-2.5 text-left transition-all disabled:cursor-not-allowed disabled:opacity-40"
              :class="version.versionId === versionPickerFile.source?.versionId
                ? 'border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)]'
                : 'border-white/8 bg-white/[0.03] hover:bg-white/8'"
              @click="pickVersion(version)">
              <div class="min-w-0">
                <div class="truncate text-[length:var(--text-sm)] font-medium text-white">
                  {{ version.versionNumber || version.name }}
                </div>
                <div class="truncate text-[length:var(--text-2xs)] text-white/35">
                  {{ version.releaseType }} · {{ version.gameVersions.slice(0, 3).join(", ") }}
                  <span v-if="version.datePublished"> · {{ formatRelativeIso(version.datePublished) }}</span>
                </div>
              </div>
              <span v-if="version.versionId === versionPickerFile.source?.versionId"
                class="shrink-0 rounded-[5px] border border-[var(--success-border)] bg-[var(--success-bg)] px-2 py-0.5 text-[length:var(--text-2xs)] font-bold text-[var(--accent)]">
                {{ t("content.currentVersion") }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Modpack release picker -->
  <Teleport to="body">
    <div v-if="showReleasePicker"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showReleasePicker = false">
      <div class="flex max-h-[70vh] w-full max-w-[520px] flex-col rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">
            {{ t("modpack.chooseRelease").toUpperCase() }}
          </div>
          <button type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all hover:bg-white/10 hover:text-white"
            @click="showReleasePicker = false">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-3">
          <div v-if="packReleases.length === 0" class="py-8 text-center text-[length:var(--text-sm)] text-white/40">
            {{ t("content.checking") }}
          </div>
          <div v-else class="flex flex-col gap-1.5">
            <button v-for="release in packReleases" :key="release.versionId" type="button"
              :disabled="!release.downloadable"
              class="flex items-center justify-between gap-3 rounded-lg border px-3 py-2.5 text-left transition-all disabled:cursor-not-allowed disabled:opacity-40"
              :class="release.versionId === modpackStatus?.modpack?.versionId
                ? 'border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)]'
                : 'border-white/8 bg-white/[0.03] hover:bg-white/8'"
              @click="runPackUpdate(release.versionId)">
              <div class="min-w-0">
                <div class="truncate text-[length:var(--text-sm)] font-medium text-white">
                  {{ release.versionNumber || release.name }}
                </div>
                <div class="truncate text-[length:var(--text-2xs)] text-white/35">
                  {{ release.releaseType }} · {{ release.gameVersions.slice(0, 3).join(", ") }}
                </div>
              </div>
              <span v-if="release.versionId === modpackStatus?.modpack?.versionId"
                class="shrink-0 rounded-[5px] border border-[var(--success-border)] bg-[var(--success-bg)] px-2 py-0.5 text-[length:var(--text-2xs)] font-bold text-[var(--accent)]">
                {{ t("content.currentVersion") }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Export modal -->
  <Teleport to="body">
    <div v-if="showExportModal"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showExportModal = false">
      <div class="w-full max-w-[460px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">
            {{ t("export.title").toUpperCase() }}
          </div>
          <button type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all hover:bg-white/10 hover:text-white"
            @click="showExportModal = false">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>

        <div class="flex flex-col gap-4 p-5">
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">
              {{ t("export.format").toUpperCase() }}
            </label>
            <div class="flex gap-2">
              <button v-for="f in (['mrpack', 'curseforge'] as ExportFormat[])" :key="f" type="button"
                class="flex-1 rounded-lg border px-3 py-2 text-[length:var(--text-sm)] font-semibold transition-all"
                :class="exportFormat === f
                  ? 'border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
                  : 'border-white/10 bg-white/[0.03] text-white/50 hover:bg-white/8'"
                @click="exportFormat = f; exportResult = null">
                {{ f === 'mrpack' ? t("export.mrpack") : t("export.curseforge") }}
              </button>
            </div>
          </div>

          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">
              {{ t("export.version").toUpperCase() }}
            </label>
            <input v-model="exportVersion" type="text" placeholder="1.0.0"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 text-[length:var(--text-sm)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]" />
            <span class="text-[length:var(--text-2xs)] text-white/30">{{ t("export.hint") }}</span>
          </div>

          <div v-if="exportResult"
            class="rounded-lg border border-[var(--success-border)] bg-[var(--success-bg)] px-3 py-2.5">
            <div class="text-[length:var(--text-sm)] font-semibold text-[var(--accent)]">
              {{ t("export.done", { file: exportResult.fileName }) }}
            </div>
            <div class="mt-0.5 text-[length:var(--text-2xs)] text-white/45">
              {{ t("export.summary", { referenced: exportResult.referenced, bundled: exportResult.bundled }) }}
            </div>
            <div v-for="note in exportResult.notes" :key="note" class="mt-0.5 text-[length:var(--text-2xs)] text-white/35">
              {{ note }}
            </div>
          </div>

          <div class="flex justify-end gap-2">
            <button v-if="exportResult" type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold text-white/50 transition-all hover:bg-white/10"
              @click="openDirectory('exports')">
              <Icon icon="lucide:folder-open" class="size-[12px]" />{{ t("export.showFolder") }}
            </button>
            <button type="button" :disabled="isExporting"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold text-[var(--primary)] transition-all hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              @click="runExport">
              <Icon :icon="isExporting ? 'lucide:loader-2' : 'lucide:package-open'" class="size-[12px]"
                :class="isExporting ? 'animate-spin' : ''" />
              {{ isExporting ? t("export.running") : t("export.action") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Edit / Rename modal -->
  <Teleport to="body">
    <div v-if="showEditModal && editTargetInstance"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showEditModal = false">
      <div
        class="w-full max-w-[420px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">PROFIL UMBENENNEN</div>
          <button type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            @click="showEditModal = false">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">PROFILNAME</label>
            <input v-model="editNewName" type="text"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-[length:var(--text-md)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
              @keyup.enter="renameManaged(editTargetInstance!.name, editNewName)" />
          </div>
          <div class="flex justify-end gap-2">
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showEditModal = false">
              {{ t("common.cancel") }}
            </button>
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!editNewName.trim() || editNewName.trim() === editTargetInstance.name"
              @click="renameManaged(editTargetInstance!.name, editNewName)">
              {{ t("common.save") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Delete confirmation modal -->
  <Teleport to="body">
    <div v-if="showDeleteModal && deleteTargetInstance"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showDeleteModal = false">
      <div
        class="w-full max-w-[400px] rounded-[14px] border border-white/10 bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">{{ t("profiles.deleteTitle") }}</div>
          <button type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            @click="showDeleteModal = false">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <p class="text-[length:var(--text-base)] text-white/70">
            {{ t("profiles.deleteConfirm", { name: deleteTargetInstance.name }) }}
          </p>
          <div class="flex justify-end gap-2">
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showDeleteModal = false">
              {{ t("common.cancel") }}
            </button>
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-red-500/40 bg-red-500/20 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-red-400 transition-all duration-200 hover:bg-red-500/30"
              @click="handleDeleteInstance(deleteTargetInstance!.name)">
              <Icon icon="lucide:trash-2" class="size-[11px]" />{{ t("common.delete") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
