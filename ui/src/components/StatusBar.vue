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
</script>
<template>
  <footer class="relative z-[60] flex min-h-9 shrink-0 flex-wrap items-center gap-3 border-t border-white/5 bg-[rgba(3,9,18,.96)] px-4 py-2 text-[10.5px] tracking-[0.06em] text-white/60">
    <div class="flex items-center gap-1.5" :class="!error ? 'text-[rgba(0,255,204,.7)]' : ''">
      <div
        class="size-1.5 rounded-full"
        :class="error ? 'bg-[#ff5f57] shadow-[0_0_5px_rgba(255,95,87,.5)]' : 'bg-[#00ffcc] shadow-[0_0_5px_rgba(0,255,204,.5)]'"
      ></div>
      {{ error ? 'FEHLER' : 'ONLINE' }}
    </div>
    <div class="flex items-center gap-1.5">
      <Icon icon="lucide:monitor" class="size-[10px]" />
      {{ runningInstancesCount > 0 ? `${runningInstancesCount} Spiel${runningInstancesCount > 1 ? 'e' : ''} läuft` : 'Kein Spiel läuft' }}
    </div>
    <div v-if="selectedInstance" class="flex items-center gap-1.5">
      <Icon icon="lucide:layers" class="size-[10px]" />
      Java {{ selectedInstance.javaMajorVersion }}
    </div>
    <button
      v-if="error"
      type="button"
      class="max-w-[260px] cursor-pointer truncate text-left text-[rgba(255,150,150,.8)]"
      :title="error"
      @click="error = null"
    >
      ⚠ {{ error }}
    </button>
    <button
      v-else-if="launcherMessage"
      type="button"
      class="max-w-[260px] cursor-pointer truncate text-left text-[rgba(0,255,204,.7)]"
      :title="launcherMessage"
      @click="launcherMessage = null"
    >
      ✓ {{ launcherMessage }}
    </button>
    <div class="flex-1"></div>
    <div class="flex flex-wrap gap-1.5">
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded px-2.5 py-1 font-semibold tracking-[0.07em] text-white/60 transition-all duration-200 hover:bg-white/10 hover:text-white"
        @click="activeTab = 'settings'"
      >
        <Icon icon="lucide:folder-open" class="size-[10px]" />
        Verzeichnis öffnen
      </button>
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded px-2.5 py-1 font-semibold tracking-[0.07em] text-white/60 transition-all duration-200 hover:bg-white/10 hover:text-white"
        @click="void loadInstances()"
      >
        <Icon icon="lucide:download" class="size-[10px]" />
        Updates prüfen
      </button>
    </div>
    <div class="text-[10.5px] tracking-[0.08em] text-white/40">v1.0.0-beta</div>
  </footer>
</template>
