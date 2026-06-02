<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
import type { LauncherInstance } from '@/composables/useLauncher';

const {
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
  filteredInstances,
  pendingInstances,
  isLoadingInstances,
  isLaunching,
  versionEmoji,
  formatRelativeDate,
  formatLoaderId,
  handleDeleteInstance,
  handleRenameInstance,
  handleOpenInstanceFolder,
  handleLaunch,
} = useLauncher();

const filters = ['ALL', 'RELEASE', 'SNAPSHOT', 'NEOFORGE', 'FORGE', 'FABRIC', 'QUILT'];

// ── Pending card step cycling ─────────────────────────────────────────────────
const CREATION_STEPS = [
  'Manifest laden...',
  'Assets herunterladen...',
  'Bibliotheken installieren...',
  'Natives extrahieren...',
  'Fertigstellen...',
];
const stepIndex = ref(0);
let stepTimer: ReturnType<typeof setInterval> | null = null;
onMounted(() => { stepTimer = setInterval(() => { stepIndex.value = (stepIndex.value + 1) % CREATION_STEPS.length; }, 3500); });
onUnmounted(() => { if (stepTimer !== null) clearInterval(stepTimer); });

const versionGradientClass = (type: string) => ({
  release: 'bg-[linear-gradient(135deg,#0d3a18,#184d22)]',
  snapshot: 'bg-[linear-gradient(135deg,#0a2040,#001535)]',
  old_beta: 'bg-[linear-gradient(135deg,#3a1a08,#5a2a10)]',
  old_alpha: 'bg-[linear-gradient(135deg,#4d0f0f,#7a1a1a)]',
}[type] ?? 'bg-[linear-gradient(135deg,#0a1535,#122050)]');

// ── Manage view ───────────────────────────────────────────────────────────────
const managingInstance = ref<LauncherInstance | null>(null);

function openManage(inst: LauncherInstance) {
  managingInstance.value = inst;
}

function closeManage() {
  managingInstance.value = null;
}

// ── Play action ───────────────────────────────────────────────────────────────
function playInstance(inst: LauncherInstance) {
  selectInstance(inst);
  void handleLaunch();
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
          <button type="button"
            class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10">
            <Icon icon="lucide:download" class="size-[11px]" />IMPORT
          </button>
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

      <div class="overflow-y-scroll grid grid-cols-[repeat(auto-fit,minmax(250px,1fr))] gap-3 px-4 py-3 md:px-6">
        <!-- New profile card -->
        <button type="button"
          class="flex h-48 flex-col items-center justify-center gap-2.5 rounded-xl border border-dashed border-white/15 bg-[var(--surface-panel-muted)] transition-all duration-200 hover:border-[var(--accent-border-emphasis)] hover:bg-[var(--accent-bg-soft)]"
          @click.stop="showCreateModal = true">
          <div
            class="flex size-[38px] items-center justify-center rounded-full border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] text-[var(--primary)]">
            <Icon icon="lucide:plus" class="size-4" />
          </div>
          <div class="text-center">
            <div class="text-[length:var(--text-base)] font-semibold text-white">Neues Profil</div>
            <div class="text-[length:var(--text-2xs)] text-white/30">Erstellen oder importieren</div>
          </div>
        </button>

        <!-- Pending (creating) cards -->
        <div v-for="pending in pendingInstances" :key="pending.id"
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
              {{ CREATION_STEPS[stepIndex] }}
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
          class="group relative h-48 overflow-visible rounded-xl border border-white/10 bg-[var(--surface-panel)] text-left transition-all duration-200 hover:-translate-y-1 hover:border-[var(--accent-border-hover)] hover:shadow-[0_12px_32px_rgba(0,0,0,.4),0_0_24px_rgba(var(--primary-rgb),.1)] cursor-pointer"
          @click.stop="openManage(inst)">
          <!-- Card image area -->
          <div
            class="relative flex h-[100px] items-center justify-center overflow-hidden rounded-t-xl pointer-events-none">
            <div
              class="absolute inset-0 flex items-center justify-center text-[40px] opacity-70 transition-transform duration-300 group-hover:scale-105"
              :class="versionGradientClass(inst.versionType)">
              {{ versionEmoji(inst.versionType) }}
            </div>
            <div class="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-[rgba(8,18,34,.95)]">
            </div>

            <!-- Play button overlay — pointer-events enabled, stops card click -->
            <div
              class="pointer-events-auto absolute inset-0 flex items-center justify-center gap-2 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
              <button type="button"
                class="h-10 inline-flex items-center gap-2 bg-[var(--primary)] rounded-[7px] px-4 py-2 text-[length:var(--text-base)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!authData || isLaunching" @click.stop="playInstance(inst)">
                <Icon icon="lucide:play" class="size-[10px]" />PLAY
              </button>
              <button v-if="selectedInstanceName !== inst.name" type="button"
                class="h-10 inline-flex items-center gap-2 rounded-r-[7px] bg-(--surface-input) px-4 py-2 text-[length:var(--text-base)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                @click.stop="selectInstance(inst)">
                <Icon icon="mynaui:arrow-left-right" class="size-[24px] brightness-60" />
              </button>
            </div>

            <div class="absolute left-[7px] top-[7px] flex gap-1 pointer-events-none">
              <span v-if="inst.running"
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
            <div class="flex flex-wrap gap-[5px] text-[length:var(--text-2xs)] text-white/60">
              <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#4caf50]"></span>{{
                formatLoaderId(inst.versionId) }}</span>
              <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#6c63ff]"></span>Java
                {{ inst.javaMajorVersion }}</span>
              <span class="text-white/25">{{ formatRelativeDate(inst.lastPlayedAt) }}</span>
            </div>
          </div>

          <!-- 3-dot menu -->
          <div class="absolute right-[7px] top-[7px] z-10" @click.stop>
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
            <div class="flex h-28 items-center justify-center text-[60px]"
              :class="versionGradientClass(managingInstance.versionType)">
              {{ versionEmoji(managingInstance.versionType) }}
            </div>
            <div class="flex flex-col gap-1.5 p-3">
              <div class="truncate text-[length:var(--text-md)] font-semibold text-white">{{ managingInstance.name }}
              </div>
              <div class="text-[length:var(--text-xs)] text-white/50">{{ formatLoaderId(managingInstance.versionId) }}
              </div>
            </div>
            <!-- Play button -->
            <div class="px-3 pb-3 flex flex-col gap-2">
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--accent-border-strong) bg-(--primary) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!authData || isLaunching || managingInstance.running"
                @click="playInstance(managingInstance)">
                <Icon icon="lucide:play" class="size-[10px]" />PLAY
              </button>
              <button
                v-if="managingInstance.versionType === 'neoforge' || managingInstance.versionType === 'forge' || managingInstance.versionType === 'fabric' || managingInstance.versionType === 'quilt'"
                type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--surface-panel-strong) bg-(--surface-input) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white"
                :disabled="false" @click="">
                <Icon icon="lucide:puzzle" class="size-[10px]" />Mods
              </button>
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--surface-panel-strong) bg-(--surface-input) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white"
                :disabled="false" @click="">
                <Icon icon="lucide:play" class="lucide:palette" />Resource Packs
              </button>
              <button
                v-if="managingInstance.versionType === 'neoforge' || managingInstance.versionType === 'forge' || managingInstance.versionType === 'fabric' || managingInstance.versionType === 'quilt'"
                type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--surface-panel-strong) bg-(--surface-input) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white"
                :disabled="false" @click="">
                <Icon icon="lucide:sparkles" class="size-[10px]" />Shaders
              </button>
              <button type="button"
                class="w-full inline-flex items-center justify-center gap-2 rounded-[7px] border border-(--surface-panel-strong) bg-(--surface-input) py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white"
                :disabled="false" @click="">
                <Icon icon="lucide:database" class="size-[10px]" />Data Packs
              </button>
            </div>
          </div>

          <!-- Right: info + actions -->
          <div class="flex flex-1 flex-col gap-4 overflow-y-auto">
            <div class="flex gap-4">
              <!-- Stats -->
              <div class="rounded-xl border border-white/8 bg-[var(--surface-panel)] p-4 grow-1">
                <div class="mb-3 text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40">INFO</div>
                <div class="grid grid-cols-2 gap-3">
                  <div class="flex flex-col gap-0.5">
                    <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">VERSION</div>
                    <div class="text-[length:var(--text-sm)] font-medium text-white">{{
                      formatLoaderId(managingInstance.versionId) }}</div>
                  </div>
                  <div class="flex flex-col gap-0.5">
                    <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">JAVA</div>
                    <div class="text-[length:var(--text-sm)] font-medium text-white">Java {{
                      managingInstance.javaMajorVersion }}</div>
                  </div>
                  <div class="flex flex-col gap-0.5">
                    <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">ERSTELLT</div>
                    <div class="text-[length:var(--text-sm)] font-medium text-white">{{
                      formatRelativeDate(managingInstance.createdAt) }}</div>
                  </div>
                  <div class="flex flex-col gap-0.5">
                    <div class="text-[length:var(--text-2xs)] text-white/35 tracking-[0.08em]">ZULETZT GESPIELT</div>
                    <div class="text-[length:var(--text-sm)] font-medium text-white">{{
                      formatRelativeDate(managingInstance.lastPlayedAt) }}</div>
                  </div>
                </div>
              </div>

              <!-- Actions -->
              <div class="rounded-xl border border-white/8 bg-[var(--surface-panel)] p-4 w-auto">
                <div class="mb-3 text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40">AKTIONEN</div>
                <div class="flex flex-col gap-1.5">
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3.5 py-2.5 text-[length:var(--text-sm)] text-white/70 transition-all hover:bg-white/8 hover:text-white"
                    @click="startEdit(managingInstance)">
                    <Icon icon="lucide:pencil" class="size-3.5 shrink-0 text-white/40" />
                    <span>Umbenennen</span>
                  </button>
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-white/8 bg-white/[0.03] px-3.5 py-2.5 text-[length:var(--text-sm)] text-white/70 transition-all hover:bg-white/8 hover:text-white"
                    @click="openFolder(managingInstance)">
                    <Icon icon="lucide:folder-open" class="size-3.5 shrink-0 text-white/40" />
                    <span>Ordner öffnen</span>
                  </button>
                  <button type="button"
                    class="flex items-center gap-3 rounded-lg border border-red-500/20 bg-red-500/[0.06] px-3.5 py-2.5 text-[length:var(--text-sm)] text-[var(--danger-text,#f87171)] transition-all hover:bg-red-500/15"
                    :disabled="managingInstance.running" @click="startDelete(managingInstance)">
                    <Icon icon="lucide:trash-2" class="size-3.5 shrink-0" />
                    <span>Löschen</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>

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
              @keyup.enter="handleRenameInstance(editTargetInstance!.name, editNewName)" />
          </div>
          <div class="flex justify-end gap-2">
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showEditModal = false">
              Abbrechen
            </button>
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!editNewName.trim() || editNewName.trim() === editTargetInstance.name"
              @click="handleRenameInstance(editTargetInstance!.name, editNewName)">
              Speichern
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
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">PROFIL LÖSCHEN</div>
          <button type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            @click="showDeleteModal = false">
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <p class="text-[length:var(--text-base)] text-white/70">
            Profil <span class="font-semibold text-white">{{ deleteTargetInstance.name }}</span> wirklich löschen? Diese
            Aktion kann nicht rückgängig gemacht werden.
          </p>
          <div class="flex justify-end gap-2">
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showDeleteModal = false">
              Abbrechen
            </button>
            <button type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-red-500/40 bg-red-500/20 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-red-400 transition-all duration-200 hover:bg-red-500/30"
              @click="handleDeleteInstance(deleteTargetInstance!.name)">
              <Icon icon="lucide:trash-2" class="size-[11px]" />Löschen
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
