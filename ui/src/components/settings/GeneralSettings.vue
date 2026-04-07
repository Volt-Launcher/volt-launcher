<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import { accentColors, generalToggles } from "./settingsData";

const { accentColor, toggleStates, setAccentColor } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <!-- Accent color -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:palette" class="size-[13px]" />ACCENT COLOR
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Choose your preferred accent color for the launcher
      </div>
      <div class="flex flex-wrap gap-[7px]">
        <button
          v-for="c in accentColors"
          :key="c.value"
          type="button"
          class="inline-block h-[30px] w-[30px] rounded-[7px] border-2 border-white/20 transition-transform duration-200 hover:scale-110"
          :class="[c.class, accentColor === c.value ? '!border-white shadow-[var(--shadow-accent-sm)]' : '']"
          @click="setAccentColor(c.value)"
        />
      </div>
    </div>

    <!-- General toggles -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">OPTIONS</div>
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <div
          v-for="t in generalToggles"
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
  </div>
</template>
