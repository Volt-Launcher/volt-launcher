<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import type { SavedSkin } from "@/composables/types";
import SkinModel from "@/components/SkinModel.vue";

const {
  t,
  activeTab,
  authData,
  skins,
  selectedSkinId,
  isLoadingSkins,
  isSavingSkin,
  applyingSkinId,
  showSaveSkinModal,
  showEditSkinModal,
  showDeleteSkinModal,
  editTargetSkin,
  editSkinNewName,
  deleteTargetSkin,
  skinImageUrl,
  loadSkins,
  handleSaveSkin,
  handleRenameSkin,
  handleDeleteSkin,
  handleSelectSkin,
  formatRelativeDate,
  error,
} = useLauncher();

onMounted(() => void loadSkins());

// ── Card menu ─────────────────────────────────────────────────────────────────

const openMenuId = ref<string | null>(null);
const toggleMenu = (id: string) => {
  openMenuId.value = openMenuId.value === id ? null : id;
};
const closeMenu = () => {
  openMenuId.value = null;
};

const startEdit = (skin: SavedSkin) => {
  editTargetSkin.value = skin;
  editSkinNewName.value = skin.name;
  showEditSkinModal.value = true;
  closeMenu();
};

const startDelete = (skin: SavedSkin) => {
  deleteTargetSkin.value = skin;
  showDeleteSkinModal.value = true;
  closeMenu();
};

// ── Upload form ───────────────────────────────────────────────────────────────

const fileInput = ref<HTMLInputElement | null>(null);
const newSkinName = ref("");
const newSkinSlim = ref(false);
const newSkinPreviewUrl = ref<string | null>(null);
const newSkinBase64 = ref<string | null>(null);
const isDragging = ref(false);

const resetSaveForm = () => {
  newSkinName.value = "";
  newSkinSlim.value = false;
  newSkinPreviewUrl.value = null;
  newSkinBase64.value = null;
  isDragging.value = false;
};

const openSaveModal = () => {
  resetSaveForm();
  showSaveSkinModal.value = true;
};

const closeSaveModal = () => {
  if (isSavingSkin.value) return;
  showSaveSkinModal.value = false;
  resetSaveForm();
};

const loadFile = (file: File | undefined) => {
  if (!file) return;
  // The backend re-validates; this only avoids a pointless round trip for obvious mistakes.
  if (file.type && file.type !== "image/png") {
    error.value = t("skins.requirements");
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    const dataUrl = reader.result as string;
    newSkinBase64.value = dataUrl;
    newSkinPreviewUrl.value = dataUrl;
    if (!newSkinName.value.trim()) {
      newSkinName.value = file.name.replace(/\.png$/i, "");
    }
  };
  reader.readAsDataURL(file);
};

const onBrowse = (event: Event) => {
  const target = event.target as HTMLInputElement;
  loadFile(target.files?.[0]);
  // Reset so re-picking the same file fires change again.
  target.value = "";
};

const onDrop = (event: DragEvent) => {
  isDragging.value = false;
  loadFile(event.dataTransfer?.files?.[0]);
};

const submitSaveSkin = async () => {
  if (!newSkinBase64.value) {
    error.value = t("skins.fileRequired");
    return;
  }
  const saved = await handleSaveSkin(newSkinName.value, newSkinSlim.value, newSkinBase64.value);
  if (saved) resetSaveForm();
};
</script>

<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'skins' ? 'flex' : 'hidden'" @click="closeMenu">
    <div class="flex shrink-0 flex-wrap items-center gap-3 px-4 pt-4 md:px-6">
      <div class="text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40 uppercase">
        {{ t("skins.count", { n: skins.length }) }}
      </div>
      <div class="flex-1"></div>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
        @click.stop="openSaveModal"
      >
        <Icon icon="lucide:plus" class="size-[11px]" />{{ t("skins.save") }}
      </button>
    </div>

    <!-- Applying a skin needs an account; saving to the library does not. -->
    <div
      v-if="!authData"
      class="mx-4 mt-3 flex items-center gap-2 rounded-lg border border-white/10 bg-white/[0.03] px-3 py-2 text-[length:var(--text-sm)] text-white/50 md:mx-6"
    >
      <Icon icon="lucide:info" class="size-[13px] shrink-0" />{{ t("skins.signInFirst") }}
    </div>

    <div class="grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-3 overflow-y-auto px-4 py-3 md:px-6">
      <!-- Upload card -->
      <button
        type="button"
        class="flex h-64 flex-col items-center justify-center gap-2.5 rounded-xl border border-dashed border-white/15 bg-[var(--surface-panel-muted)] transition-all duration-200 hover:border-[var(--accent-border-emphasis)] hover:bg-[var(--accent-bg-soft)]"
        @click.stop="openSaveModal"
      >
        <div
          class="flex size-[38px] items-center justify-center rounded-full border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] text-[var(--primary)]"
        >
          <Icon icon="lucide:plus" class="size-4" />
        </div>
        <div class="text-center">
          <div class="text-[length:var(--text-base)] font-semibold text-white">{{ t("skins.save") }}</div>
          <div class="text-[length:var(--text-2xs)] text-white/30">{{ t("skins.saveHint") }}</div>
        </div>
      </button>

      <!-- Skin cards -->
      <div
        v-for="skin in skins"
        :key="skin.id"
        class="group relative flex h-64 flex-col overflow-visible rounded-xl border bg-[var(--surface-panel)] transition-all duration-200 hover:-translate-y-1 hover:shadow-[0_12px_32px_rgba(0,0,0,.4),0_0_24px_rgba(var(--primary-rgb),.1)]"
        :class="selectedSkinId === skin.id
          ? 'border-[var(--primary-border)]'
          : 'border-white/10 hover:border-[var(--accent-border-hover)]'"
      >
        <div
          class="relative flex h-[190px] items-center justify-center overflow-hidden rounded-t-xl bg-[linear-gradient(135deg,#0a1535,#122050)]"
        >
          <SkinModel
            :skin="skinImageUrl(skin.id)"
            :slim="skin.slim"
            :width="170"
            :height="190"
            animation="idle"
            :interactive="false"
            :auto-rotate="true"
          />

          <div class="pointer-events-none absolute top-[7px] left-[7px] flex gap-1">
            <span
              v-if="selectedSkinId === skin.id"
              class="rounded border border-[var(--primary-border)] bg-[var(--primary)]/20 px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--primary)] uppercase"
            >
              {{ t("skins.active") }}
            </span>
          </div>

          <!-- Apply overlay -->
          <div
            v-if="selectedSkinId !== skin.id"
            class="pointer-events-auto absolute inset-0 flex items-center justify-center opacity-0 transition-opacity duration-200 group-hover:opacity-100"
          >
            <button
              type="button"
              class="inline-flex items-center gap-2 rounded-[7px] bg-[var(--primary)] px-4 py-2 text-[length:var(--text-sm)] font-bold tracking-[0.08em] text-white disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="applyingSkinId !== null || !authData"
              :title="!authData ? t('skins.signInFirst') : undefined"
              @click.stop="handleSelectSkin(skin.id)"
            >
              <Icon
                :icon="applyingSkinId === skin.id ? 'lucide:loader-2' : 'mynaui:arrow-left-right'"
                class="size-[12px]"
                :class="applyingSkinId === skin.id ? 'animate-spin' : ''"
              />
              {{ applyingSkinId === skin.id ? t("skins.applying") : t("skins.apply") }}
            </button>
          </div>
        </div>

        <div class="flex flex-1 items-center justify-between gap-2 px-3 py-2.5">
          <div class="min-w-0">
            <div class="truncate text-[length:var(--text-md-plus)] leading-[1.35] font-semibold text-white">
              {{ skin.name }}
            </div>
            <div class="text-[length:var(--text-2xs)] text-white/40">
              {{ formatRelativeDate(skin.createdAt) }}
              <template v-if="skin.slim"> · {{ t("skins.slimModel") }}</template>
            </div>
          </div>
        </div>

        <!-- Card menu -->
        <div class="absolute top-[7px] right-[7px] z-10" @click.stop>
          <button
            type="button"
            class="flex size-6 items-center justify-center rounded-md border border-white/10 bg-black/40 text-white/40 opacity-0 backdrop-blur-sm transition-all duration-150 group-hover:opacity-100 hover:bg-white/15 hover:!text-white"
            @click="toggleMenu(skin.id)"
          >
            <Icon icon="lucide:more-vertical" class="size-3" />
          </button>
          <div
            v-if="openMenuId === skin.id"
            class="absolute top-8 right-0 z-20 min-w-[148px] overflow-hidden rounded-[9px] border border-white/10 bg-[var(--surface-panel-strong)] shadow-[0_8px_24px_rgba(0,0,0,.5)] backdrop-blur-md"
          >
            <button
              v-if="selectedSkinId !== skin.id && authData"
              type="button"
              class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-white/70 transition-colors hover:bg-white/8 hover:text-white"
              @click="handleSelectSkin(skin.id); closeMenu()"
            >
              <Icon icon="mynaui:arrow-left-right" class="size-3.5 shrink-0" />{{ t("skins.apply") }}
            </button>
            <button
              type="button"
              class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-white/70 transition-colors hover:bg-white/8 hover:text-white"
              @click="startEdit(skin)"
            >
              <Icon icon="lucide:pencil" class="size-3.5 shrink-0" />{{ t("common.rename") }}
            </button>
            <div class="mx-2 my-0.5 h-px bg-white/7"></div>
            <button
              type="button"
              class="flex w-full items-center gap-2.5 px-3 py-2 text-[length:var(--text-sm)] text-[var(--danger-text)] transition-colors hover:bg-red-500/10"
              @click="startDelete(skin)"
            >
              <Icon icon="lucide:trash-2" class="size-3.5 shrink-0" />{{ t("common.delete") }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="isLoadingSkins && skins.length === 0" class="col-[1/-1] flex justify-center gap-2 py-10 text-white/40">
        <Icon icon="lucide:loader-2" class="size-5 animate-spin" />{{ t("common.loading") }}
      </div>
      <div v-else-if="skins.length === 0" class="col-[1/-1] flex flex-col items-center gap-2 py-10 text-center">
        <Icon icon="lucide:shirt" class="size-9 text-white/15" />
        <div class="text-[length:var(--text-md)] font-semibold text-white/35">{{ t("skins.empty") }}</div>
        <div class="text-[length:var(--text-sm)] text-white/25">{{ t("skins.emptyHint") }}</div>
      </div>
    </div>
  </div>

  <!-- Upload modal -->
  <Teleport to="body">
    <div
      v-if="showSaveSkinModal"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="closeSaveModal"
    >
      <div
        class="w-full max-w-[440px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]"
      >
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">{{ t("skins.save") }}</div>
          <button
            type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            :aria-label="t('common.close')"
            @click="closeSaveModal"
          >
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>

        <div class="flex flex-col gap-4 p-5">
          <input ref="fileInput" type="file" accept="image/png" class="hidden" @change="onBrowse" />

          <div
            v-if="!newSkinPreviewUrl"
            class="flex h-[180px] cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed transition-colors"
            :class="isDragging
              ? 'border-[var(--accent-border-strong)] bg-[var(--accent-bg-soft)]'
              : 'border-white/15 bg-white/[0.03]'"
            @click="fileInput?.click()"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="onDrop"
          >
            <Icon icon="lucide:image-plus" class="size-8 text-white/25" />
            <div class="text-[length:var(--text-sm)] text-white/50">{{ t("skins.dropHint") }}</div>
            <div class="px-6 text-center text-[length:var(--text-2xs)] text-white/30">
              {{ t("skins.requirements") }}
            </div>
          </div>

          <div
            v-else
            class="flex h-[180px] items-center justify-center overflow-hidden rounded-lg bg-[linear-gradient(135deg,#0a1535,#122050)]"
          >
            <SkinModel
              :skin="newSkinPreviewUrl"
              :slim="newSkinSlim"
              :width="150"
              :height="170"
              animation="idle"
              :interactive="false"
            />
          </div>

          <button
            v-if="newSkinPreviewUrl"
            type="button"
            class="self-start text-[length:var(--text-2xs)] font-semibold tracking-[0.06em] text-white/40 hover:text-white/70"
            @click="fileInput?.click()"
          >
            {{ t("skins.chooseAnother") }}
          </button>

          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 uppercase">
              {{ t("profiles.name") }}
            </label>
            <input
              v-model="newSkinName"
              type="text"
              :placeholder="t('skins.namePlaceholder')"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-[length:var(--text-md)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
              @keyup.enter="submitSaveSkin"
            />
          </div>

          <label class="flex cursor-pointer items-center gap-2 text-[length:var(--text-sm)] text-white/60">
            <input v-model="newSkinSlim" type="checkbox" class="size-3.5 accent-[var(--primary)]" />
            {{ t("skins.slimModel") }}
          </label>

          <div class="flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10 disabled:opacity-40"
              :disabled="isSavingSkin"
              @click="closeSaveModal"
            >
              {{ t("common.cancel") }}
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!newSkinPreviewUrl || !newSkinName.trim() || isSavingSkin"
              @click="submitSaveSkin"
            >
              <Icon
                :icon="isSavingSkin ? 'lucide:loader-2' : 'lucide:save'"
                class="size-[12px]"
                :class="isSavingSkin ? 'animate-spin' : ''"
              />
              {{ t("common.save") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Rename modal -->
  <Teleport to="body">
    <div
      v-if="showEditSkinModal && editTargetSkin"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showEditSkinModal = false"
    >
      <div
        class="w-full max-w-[420px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]"
      >
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">
            {{ t("skins.renameTitle") }}
          </div>
          <button
            type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            :aria-label="t('common.close')"
            @click="showEditSkinModal = false"
          >
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 uppercase">
              {{ t("profiles.name") }}
            </label>
            <input
              v-model="editSkinNewName"
              type="text"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-[length:var(--text-md)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
              @keyup.enter="handleRenameSkin(editTargetSkin!.id, editSkinNewName)"
            />
          </div>
          <div class="flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showEditSkinModal = false"
            >
              {{ t("common.cancel") }}
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="!editSkinNewName.trim() || editSkinNewName.trim() === editTargetSkin.name"
              @click="handleRenameSkin(editTargetSkin!.id, editSkinNewName)"
            >
              {{ t("common.save") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Delete confirmation -->
  <Teleport to="body">
    <div
      v-if="showDeleteSkinModal && deleteTargetSkin"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showDeleteSkinModal = false"
    >
      <div
        class="w-full max-w-[400px] rounded-[14px] border border-white/10 bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]"
      >
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">
            {{ t("skins.deleteTitle") }}
          </div>
          <button
            type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            :aria-label="t('common.close')"
            @click="showDeleteSkinModal = false"
          >
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <p class="text-[length:var(--text-base)] text-white/70">
            {{ t("skins.deleteConfirm", { name: deleteTargetSkin.name }) }}
          </p>
          <div class="flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showDeleteSkinModal = false"
            >
              {{ t("common.cancel") }}
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--danger-border)] bg-[var(--danger-bg)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--danger-text)] transition-all duration-200 hover:brightness-125"
              @click="handleDeleteSkin(deleteTargetSkin!.id)"
            >
              <Icon icon="lucide:trash-2" class="size-[11px]" />{{ t("common.delete") }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
