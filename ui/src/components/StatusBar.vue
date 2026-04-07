<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  error,
  launcherMessage,
  activeTab,
  selectedInstance,
  runningInstancesCount,
  loadInstances,
} = useLauncher();

const appVersion = import.meta.env.VITE_APP_VERSION || 'Unknown';
</script>

<template>
  <footer class="relative z-[60] flex min-h-9 shrink-0 flex-wrap items-center gap-3 border-t border-white/5 bg-[var(--footer-bg)] px-4 py-2 text-[length:var(--text-2xs-plus)] tracking-[0.06em] text-white/60">
    <div class="flex items-center gap-1.5" :class="!error ? 'text-[var(--success-text)]' : ''">
      <div
        class="size-1.5 rounded-full"
        :class="error ? 'bg-[var(--danger)] shadow-[var(--shadow-danger-xs)]' : 'bg-[var(--accent)] shadow-[var(--shadow-success-xs)]'"
      ></div>
      {{ error ? 'Error' : 'Online' }}
    </div>
    <div class="flex items-center gap-1.5">
      <Icon icon="lucide:monitor" class="size-[10px]" />
      {{ runningInstancesCount > 0 ? `${runningInstancesCount} profile${runningInstancesCount > 1 ? 's' : ''} running` : 'No profile running' }}
    </div>
    <div v-if="selectedInstance" class="flex items-center gap-1.5">
      <Icon icon="lucide:layers" class="size-[10px]" />
      Java {{ selectedInstance.javaMajorVersion }}
    </div>
    <button
      v-if="error"
      type="button"
      class="max-w-[260px] cursor-pointer truncate text-left text-[var(--danger-text-soft)] hover:text-[var(--danger-text)] transition-all duration-200"
      :title="error"
      @click="error = null"
    >
      ⚠ Reload
    </button>
    <button
      v-else-if="launcherMessage"
      type="button"
      class="max-w-[260px] cursor-pointer truncate text-left text-[var(--success-text)]"
      :title="launcherMessage"
      @click="launcherMessage = null"
    >
      ✓ {{ launcherMessage }}
    </button>
    <div class="flex-1"></div>
    <div class="flex flex-wrap gap-1.5">
      <button
        type="button"
        class="inline-flex cursor-pointer items-center gap-1 rounded px-2.5 py-1 font-semibold tracking-[0.07em] text-white/60 transition-all duration-200 hover:bg-white/10 hover:text-white"
        @click="activeTab = 'settings'"
      >
        <Icon icon="lucide:folder-open" class="size-[10px]" />
        Open Directory
      </button>
      <button
        type="button"
        class="inline-flex cursor-pointer items-center gap-1 rounded px-2.5 py-1 font-semibold tracking-[0.07em] text-white/60 transition-all duration-200 hover:bg-white/10 hover:text-white"
        @click="void loadInstances()"
      >
        <Icon icon="lucide:download" class="size-[10px]" />
        Check for Updates
      </button>
    </div>
    <div class="text-[length:var(--text-2xs-plus)] tracking-[0.08em] text-white/40">{{ appVersion }}</div>
  </footer>
</template>
