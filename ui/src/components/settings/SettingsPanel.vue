<script setup lang="ts">
import { Icon } from "@iconify/vue";

/** A titled card. Every settings section is built from these so spacing stays uniform. */
defineProps<{
  title: string;
  description?: string;
  icon?: string;
}>();
</script>

<template>
  <section class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
    <header class="mb-[14px]">
      <div class="mb-1 flex items-center justify-between gap-3">
        <h3 class="flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60 uppercase">
          <Icon v-if="icon" :icon="icon" class="size-[13px]" />
          {{ title }}
        </h3>
        <slot name="action" />
      </div>
      <p v-if="description" class="text-[length:var(--text-base)] leading-[1.55] text-white/60">
        {{ description }}
      </p>
    </header>
    <slot />
  </section>
</template>
