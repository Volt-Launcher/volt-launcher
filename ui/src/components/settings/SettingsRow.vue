<script setup lang="ts">
/** A label/description column beside an arbitrary control. */
defineProps<{
  label: string;
  description?: string;
}>();
</script>

<template>
  <div
    class="flex flex-wrap items-center justify-between gap-3 rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]"
  >
    <div class="min-w-0 flex-1">
      <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">{{ label }}</div>
      <div v-if="description" class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">
        {{ description }}
      </div>
    </div>
    <div class="shrink-0"><slot /></div>
  </div>
</template>
