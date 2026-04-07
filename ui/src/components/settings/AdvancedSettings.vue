<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { ref } from "vue";
import { useLauncher } from "@/composables/useLauncher";
import CustomSlider from "@/components/CustomSlider.vue";
import { advancedToggles } from "./settingsData";

const { toggleStates } = useLauncher();

const simultaneousDownloads = ref(5);
const concurrentIO = ref(10);
</script>

<template>
  <div class="flex flex-col gap-3">
    <!-- Performance sliders -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:gauge" class="size-[13px]" />PERFORMANCE
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Configure download and I/O concurrency
      </div>
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <CustomSlider v-model="simultaneousDownloads" :min="1" :max="10" :step="1" label="Simultaneous Downloads" />
        <CustomSlider v-model="concurrentIO" :min="1" :max="20" :step="1" label="Concurrent I/O" />
      </div>
    </div>

    <!-- Advanced toggles -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">ADVANCED OPTIONS</div>
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <div
          v-for="t in advancedToggles"
          :key="t.key"
          class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]"
        >
          <div>
            <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">{{ t.name }}</div>
            <div class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">{{ t.sub }}</div>
          </div>
          <button
            type="button"
            class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
            :class="toggleStates[t.key as keyof typeof toggleStates] ? 'bg-[var(--primary)]' : 'bg-white/10'"
            :aria-pressed="toggleStates[t.key as keyof typeof toggleStates]"
            @click="(toggleStates[t.key as keyof typeof toggleStates] as boolean) = !toggleStates[t.key as keyof typeof toggleStates]"
          >
            <span
              class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300"
              :class="toggleStates[t.key as keyof typeof toggleStates] ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'"
            />
          </button>
        </div>
      </div>
    </div>

    <!-- Debug -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">DEBUG</div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Diagnostic tools and log access
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/50 transition-all duration-200 hover:bg-white/10"
        >
          <Icon icon="lucide:folder-open" class="size-[11px]" />Open Logs Folder
        </button>
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/50 transition-all duration-200 hover:bg-white/10"
        >
          <Icon icon="lucide:clipboard-copy" class="size-[11px]" />Copy Debug Info
        </button>
      </div>
    </div>
  </div>
</template>
