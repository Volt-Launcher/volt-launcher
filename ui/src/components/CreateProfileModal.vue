<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  availableVersions,
  newInstanceName,
  selectedVersionId,
  includeSnapshots,
  includeBetas,
  includeAlphas,
  isCreatingInstance,
  isLoadingVersions,
  error,
  showCreateModal,
  selectedVersion,
  formatVersionType,
  formatReleaseTime,
  handleCreateInstance,
} = useLauncher();
</script>
<template>
  <Teleport to="body">
    <div
      v-if="showCreateModal"
      class="fixed inset-0 z-[200] flex items-center justify-center bg-black/60 p-5 backdrop-blur-md"
      @click.self="showCreateModal = false"
    >
      <div class="w-full max-w-[480px] overflow-hidden rounded-[14px] border border-[rgba(0,178,255,.2)] bg-[rgba(8,18,34,.96)] shadow-[0_32px_80px_rgba(0,0,0,.5),0_0_40px_rgba(0,178,255,.08)]">
        <div class="flex items-center justify-between border-b border-white/7 px-5 py-4">
          <div class="text-[14px] font-bold tracking-[0.1em] text-white">NEUES PROFIL ERSTELLEN</div>
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
            <label class="text-[10px] font-bold tracking-[0.14em] text-white/40">PROFILNAME</label>
            <input
              v-model="newInstanceName"
              type="text"
              placeholder="Mein Survival World"
              class="w-full rounded-lg border border-white/10 bg-[rgba(5,13,26,.8)] px-[13px] py-2.5 text-[13px] text-white outline-none transition-colors focus:border-[rgba(0,178,255,.4)]"
              @keyup.enter="handleCreateInstance"
            />
          </div>
          <div class="flex flex-col gap-1.5">
            <div class="text-[10px] font-bold tracking-[0.14em] text-white/40">VERSIONSFILTER</div>
            <div class="mt-1.5 flex flex-wrap gap-2">
              <label class="inline-flex cursor-pointer items-center gap-[7px] rounded-md border border-white/10 bg-white/5 px-3 py-[5px] text-[11.5px] font-semibold text-white/60 transition-all duration-200 hover:bg-white/10">
                <input v-model="includeSnapshots" type="checkbox" class="size-[13px] accent-[var(--primary)]" />
                <span>Snapshots</span>
              </label>
              <label class="inline-flex cursor-pointer items-center gap-[7px] rounded-md border border-white/10 bg-white/5 px-3 py-[5px] text-[11.5px] font-semibold text-white/60 transition-all duration-200 hover:bg-white/10">
                <input v-model="includeBetas" type="checkbox" class="size-[13px] accent-[var(--primary)]" />
                <span>Betas</span>
              </label>
              <label class="inline-flex cursor-pointer items-center gap-[7px] rounded-md border border-white/10 bg-white/5 px-3 py-[5px] text-[11.5px] font-semibold text-white/60 transition-all duration-200 hover:bg-white/10">
                <input v-model="includeAlphas" type="checkbox" class="size-[13px] accent-[var(--primary)]" />
                <span>Alphas</span>
              </label>
            </div>
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="text-[10px] font-bold tracking-[0.14em] text-white/40">MINECRAFT VERSION</label>
            <select
              v-model="selectedVersionId"
              class="w-full rounded-lg border border-white/10 bg-[rgba(5,13,26,.8)] px-[13px] py-2.5 text-[13px] text-white outline-none transition-colors focus:border-[rgba(0,178,255,.4)] disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="isLoadingVersions || !availableVersions.length"
            >
              <option disabled value="">Version wählen</option>
              <option v-for="v in availableVersions" :key="v.id" :value="v.id">{{ v.id }} — {{ formatVersionType(v.type) }} — {{ formatReleaseTime(v.releaseTime) }}</option>
            </select>
            <div class="mt-1.5 text-[10px] text-white/40">{{ isLoadingVersions ? 'Lade Versionen…' : `${availableVersions.length} Versionen verfügbar` }}</div>
            <div v-if="selectedVersion" class="mt-2 rounded-lg border border-[rgba(0,178,255,.15)] bg-[rgba(0,178,255,.06)] p-2.5 text-[11px] text-white/60">
              <strong class="text-[var(--primary)]">{{ selectedVersion.id }}</strong> · {{ formatVersionType(selectedVersion.type) }} · {{ formatReleaseTime(selectedVersion.releaseTime) }}
            </div>
          </div>
          <div v-if="error" class="rounded-lg border border-[rgba(255,95,87,.25)] bg-[rgba(255,95,87,.1)] px-3 py-2.5 text-[12px] text-[#ff9898]">
            {{ error }}
          </div>
          <div class="mt-1 flex justify-end gap-2">
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[11.5px] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
              @click="showCreateModal = false"
            >
              Abbrechen
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded-[7px] border border-[rgba(0,178,255,.25)] bg-[rgba(0,178,255,.12)] px-3.5 py-[7px] text-[11.5px] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[rgba(0,178,255,.2)] hover:shadow-[0_0_12px_rgba(0,178,255,.2)] disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="isCreatingInstance || !selectedVersionId || !newInstanceName.trim()"
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
