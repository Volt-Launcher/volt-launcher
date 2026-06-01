<script setup lang="ts">
import { computed } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';

const {
  availableMinecraftVersions,
  loaderVersions,
  newInstanceName,
  selectedPlatformId,
  selectedMinecraftVersionId,
  selectedLoaderVersionId,
  includeSnapshots,
  includeBetas,
  includeAlphas,
  isCreatingInstance,
  isLoadingVersions,
  isLoadingLoaderVersions,
  requiresLoaderSelection,
  platformOptions,
  error,
  showCreateModal,
  selectedVersion,
  formatVersionType,
  formatReleaseTime,
  formatLoaderId,
  handleCreateInstance,
} = useLauncher();

const minecraftVersionOptions = computed(() => availableMinecraftVersions.value.map((version) => ({
  value: version.id,
  label: `${version.id} — ${formatVersionType(version.type)} — ${formatReleaseTime(version.releaseTime)}`,
})));

const loaderVersionOptions = computed(() => loaderVersions.value.map((version) => ({
  value: version.id,
  label: formatLoaderId(version.id),
})));
</script>
<template>
  <Teleport to="body">
    <div
      v-if="showCreateModal"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showCreateModal = false"
    >
      <div class="w-full max-w-[480px] rounded-[14px] border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[length:var(--text-lg)] font-bold tracking-[0.1em] text-white">NEUES PROFIL ERSTELLEN</div>
          <button
            type="button"
            class="flex size-7 items-center justify-center rounded-md border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
            @click="showCreateModal = false"
          >
            <Icon icon="lucide:x" class="size-[14px]" />
          </button>
        </div>
        <div class="flex flex-col gap-4 p-5">
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">PROFILNAME</label>
            <input
              v-model="newInstanceName"
              type="text"
              placeholder="Mein Survival World"
              class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-[length:var(--text-md)] text-white outline-none transition-colors focus:border-[var(--accent-border-focus)]"
              @keyup.enter="handleCreateInstance"
            />
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">PLATTFORM</label>
            <VoltSelect
              v-model="selectedPlatformId"
              :options="platformOptions"
              placeholder="Plattform wählen"
            />
          </div>
          <div class="flex flex-col gap-1.5">
            <div class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">VERSIONSFILTER</div>
            <div class="mt-1.5 flex flex-wrap gap-2">
              <VoltCheckbox v-model="includeSnapshots">Snapshots</VoltCheckbox>
              <VoltCheckbox v-model="includeBetas">Betas</VoltCheckbox>
              <VoltCheckbox v-model="includeAlphas">Alphas</VoltCheckbox>
            </div>
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">MINECRAFT VERSION</label>
            <VoltSelect
              v-model="selectedMinecraftVersionId"
              :options="minecraftVersionOptions"
              placeholder="Version wählen"
              :disabled="isLoadingVersions || !availableMinecraftVersions.length"
            />
            <div class="mt-1.5 text-[length:var(--text-2xs)] text-white/40">{{ isLoadingVersions ? 'Lade Versionen…' : `${availableMinecraftVersions.length} Versionen verfügbar` }}</div>
          </div>
          <div v-if="requiresLoaderSelection" class="flex flex-col gap-1.5">
            <label class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">LOADER VERSION</label>
            <VoltSelect
              v-model="selectedLoaderVersionId"
              :options="loaderVersionOptions"
              placeholder="Loader-Version wählen"
              :disabled="isLoadingLoaderVersions || !selectedMinecraftVersionId || !loaderVersions.length"
            />
            <div class="mt-1.5 text-[length:var(--text-2xs)] text-white/40">
              {{ isLoadingLoaderVersions ? 'Lade Loader-Versionen…' : `${loaderVersions.length} Loader-Versionen verfügbar` }}
            </div>
          </div>
          <div class="flex flex-col gap-1.5">
            <div v-if="selectedVersion" class="mt-2 rounded-lg border border-[var(--accent-border-soft)] bg-[var(--accent-bg-subtle)] p-2.5 text-[length:var(--text-xs)] text-white/60">
              <strong class="text-[var(--primary)]">{{ selectedVersion.id }}</strong> · {{ formatVersionType(selectedVersion.type) }} · {{ formatReleaseTime(selectedVersion.releaseTime) }}
            </div>
          </div>
          <div v-if="error" class="rounded-lg border border-[var(--danger-border)] bg-[var(--danger-bg)] px-3 py-2.5 text-[length:var(--text-base)] text-[var(--danger-text)]">
            {{ error }}
          </div>
          <div class="mt-1 flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showCreateModal = false"
            >
              Abbrechen
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isCreatingInstance || !selectedMinecraftVersionId || (requiresLoaderSelection && !selectedLoaderVersionId) || !newInstanceName.trim()"
              @click="handleCreateInstance"
            >
              {{ isCreatingInstance ? 'Erstelle…' : 'Profil erstellen' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
