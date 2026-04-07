<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";

const { toggleStates } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:download" class="size-[13px]" />UPDATE CHANNEL
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Control how the launcher receives updates
      </div>
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <div class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]">
          <div>
            <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">Auto Updates</div>
            <div class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">Download updates automatically</div>
          </div>
          <button
            type="button"
            class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
            :class="toggleStates.autoUpdate ? 'bg-[var(--primary)]' : 'bg-white/10'"
            @click="toggleStates.autoUpdate = !toggleStates.autoUpdate"
          >
            <span class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300" :class="toggleStates.autoUpdate ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'" />
          </button>
        </div>
        <div class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]">
          <div>
            <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">Beta Channel</div>
            <div class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">Receive pre-release builds</div>
          </div>
          <button
            type="button"
            class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
            :class="toggleStates.betaUpdates ? 'bg-[var(--primary)]' : 'bg-white/10'"
            @click="toggleStates.betaUpdates = !toggleStates.betaUpdates"
          >
            <span class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300" :class="toggleStates.betaUpdates ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'" />
          </button>
        </div>
      </div>
    </div>

    <!-- Check for updates -->
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">MANUAL CHECK</div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Check for a newer version of the launcher right now
      </div>
      <div class="flex items-center gap-3">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
        >
          <Icon icon="lucide:refresh-cw" class="size-[11px]" />Check for Updates
        </button>
        <span class="text-[length:var(--text-2xs)] text-white/40">v1.0.0-beta · up to date</span>
      </div>
    </div>
  </div>
</template>
