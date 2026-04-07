<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  activeTab,
  showCreateModal,
  profileFilter,
  selectedInstanceName,
  filteredInstances,
  isLoadingInstances,
  versionEmoji,
  formatRelativeDate,
} = useLauncher();

const filters = ['ALL', 'RELEASE', 'SNAPSHOT', 'BETA', 'ALPHA'];

const versionGradientClass = (type: string) => ({
  release: 'bg-[linear-gradient(135deg,#0d3a18,#184d22)]',
  snapshot: 'bg-[linear-gradient(135deg,#0a2040,#001535)]',
  old_beta: 'bg-[linear-gradient(135deg,#3a1a08,#5a2a10)]',
  old_alpha: 'bg-[linear-gradient(135deg,#4d0f0f,#7a1a1a)]',
}[type] ?? 'bg-[linear-gradient(135deg,#0a1535,#122050)]');
</script>
<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'profiles' ? 'flex' : 'hidden'">
    <div class="flex shrink-0 flex-col gap-3 px-4 pt-4 md:px-6">
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex flex-wrap gap-[5px]">
          <button
            v-for="f in filters"
            :key="f"
            type="button"
            class="rounded-md border px-[13px] py-[5px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] transition-all duration-200"
            :class="profileFilter === f
              ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
              : 'border-white/10 bg-white/[0.03] text-white/40 hover:bg-white/[0.07] hover:text-white/70'"
            @click="profileFilter = f"
          >
            {{ f === 'ALL' ? 'ALLE' : f }}
          </button>
        </div>
        <div class="flex-1"></div>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
        >
          <Icon icon="lucide:download" class="size-[11px]" />IMPORT
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
          @click="showCreateModal = true"
        >
          <Icon icon="lucide:plus" class="size-[11px]" />NEUES PROFIL
        </button>
      </div>
    </div>
    <div class="mt-4 shrink-0 px-4 text-[length:var(--text-2xs)] font-bold tracking-[0.12em] text-white/40 md:px-6">
      PROFILE ({{ filteredInstances.length }})
    </div>
    <div class="grid flex-1 grid-cols-[repeat(auto-fill,minmax(172px,1fr))] gap-3 overflow-y-auto px-4 py-3 pb-6 md:px-6">
      <button
        type="button"
        class="flex h-48 flex-col items-center justify-center gap-2.5 rounded-xl border border-dashed border-white/15 bg-[var(--surface-panel-muted)] transition-all duration-200 hover:border-[var(--accent-border-emphasis)] hover:bg-[var(--accent-bg-soft)]"
        @click="showCreateModal = true"
      >
        <div class="flex size-[38px] items-center justify-center rounded-full border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] text-[var(--primary)]">
          <Icon icon="lucide:plus" class="size-4" />
        </div>
        <div class="text-center">
          <div class="text-[length:var(--text-base)] font-semibold text-white">Neues Profil</div>
          <div class="text-[length:var(--text-2xs)] text-white/30">Erstellen oder importieren</div>
        </div>
      </button>
      <button
        v-for="inst in filteredInstances"
        :key="inst.slug"
        type="button"
        class="group relative h-48 overflow-hidden rounded-xl border border-white/10 bg-[var(--surface-panel)] text-left transition-all duration-200 hover:-translate-y-1 hover:border-[var(--accent-border-hover)] hover:shadow-[0_12px_32px_rgba(0,0,0,.4),0_0_24px_rgba(var(--primary-rgb),.1)]"
        @click="selectedInstanceName = inst.name; activeTab = 'home'"
      >
        <div class="relative flex h-[100px] items-center justify-center overflow-hidden">
          <div
            class="absolute inset-0 flex items-center justify-center text-[40px] opacity-70 transition-transform duration-300 group-hover:scale-105"
            :class="versionGradientClass(inst.versionType)"
          >
            {{ versionEmoji(inst.versionType) }}
          </div>
          <div class="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-[rgba(8,18,34,.95)]"></div>
          <div class="absolute inset-0 flex items-center justify-center bg-[var(--accent-overlay)] opacity-0 transition-opacity duration-200 group-hover:opacity-100">
            <div class="inline-flex items-center gap-2 rounded-[7px] bg-[var(--primary)] px-4 py-2 text-[length:var(--text-base)] font-bold tracking-[0.08em] text-white">
              <Icon icon="lucide:play" class="size-[10px]" />PLAY
            </div>
          </div>
          <div class="absolute left-[7px] top-[7px] flex gap-1">
            <span v-if="inst.running" class="rounded border border-[var(--success-border)] bg-[var(--success-bg)] px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--accent)]">▶ AKTIV</span>
            <span v-if="selectedInstanceName === inst.name" class="rounded border border-[var(--warning-border)] bg-[var(--warning-bg)] px-1.5 py-0.5 text-[8px] font-bold tracking-[0.08em] text-[var(--warning)]">⭐</span>
          </div>
        </div>
        <div class="px-3 py-2.5">
          <div class="mb-1.5 truncate text-[length:var(--text-md-plus)] font-semibold leading-[1.35] text-white">{{ inst.name }}</div>
          <div class="flex flex-wrap gap-[5px] text-[length:var(--text-2xs)] text-white/60">
            <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#4caf50]"></span>{{ inst.versionId }}</span>
            <span class="inline-flex items-center gap-1"><span class="size-1.5 rounded-full bg-[#6c63ff]"></span>Java {{ inst.javaMajorVersion }}</span>
            <span class="text-white/25">{{ formatRelativeDate(inst.lastPlayedAt) }}</span>
          </div>
        </div>
      </button>
      <div v-if="filteredInstances.length === 0 && !isLoadingInstances" class="col-[1/-1] py-10 text-center text-[length:var(--text-md)] text-white/40">
        Keine Profile gefunden. Erstelle ein neues Profil mit dem Button oben.
      </div>
    </div>
  </div>
</template>
