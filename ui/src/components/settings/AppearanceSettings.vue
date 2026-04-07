<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";

const { animationsEnabled, uiScale, showFps } = useLauncher();

const scales = [
  { value: "compact" as const, label: "Compact" },
  { value: "default" as const, label: "Default" },
  { value: "comfortable" as const, label: "Comfortable" },
] as const;
</script>

<template>
  <div class="flex flex-col gap-3">
    <!-- UI Scale -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:maximize" class="size-[13px]" />UI SCALE
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Adjust the interface density
      </div>
      <div class="flex gap-2">
        <button
          v-for="s in scales"
          :key="s.value"
          type="button"
          class="rounded-lg border px-4 py-2 text-[length:var(--text-sm)] font-semibold tracking-[0.06em] transition-all duration-200"
          :class="uiScale === s.value
            ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
            : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07]'"
          @click="uiScale = s.value"
        >
          {{ s.label }}
        </button>
      </div>
    </div>

    <!-- Visual options -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">VISUAL</div>
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <div class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]">
          <div>
            <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">Animations</div>
            <div class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">Enable UI transitions</div>
          </div>
          <button
            type="button"
            class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
            :class="animationsEnabled ? 'bg-[var(--primary)]' : 'bg-white/10'"
            @click="animationsEnabled = !animationsEnabled"
          >
            <span class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300" :class="animationsEnabled ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'" />
          </button>
        </div>
        <div class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]">
          <div>
            <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">Show FPS</div>
            <div class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">Display frame counter</div>
          </div>
          <button
            type="button"
            class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
            :class="showFps ? 'bg-[var(--primary)]' : 'bg-white/10'"
            @click="showFps = !showFps"
          >
            <span class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300" :class="showFps ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
