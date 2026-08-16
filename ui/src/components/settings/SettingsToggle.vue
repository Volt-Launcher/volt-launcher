<script setup lang="ts">
/** Labelled on/off switch. `v-model` keeps the parent free of click plumbing. */
defineProps<{
  label: string;
  description?: string;
}>();

const model = defineModel<boolean>({ required: true });
</script>

<template>
  <div
    class="flex items-center justify-between gap-3 rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]"
  >
    <div class="min-w-0">
      <div class="mb-[3px] text-[length:var(--text-base-plus)] font-semibold text-white">{{ label }}</div>
      <div v-if="description" class="text-[length:var(--text-2xs-plus)] leading-[1.45] text-white/60">
        {{ description }}
      </div>
    </div>
    <button
      type="button"
      role="switch"
      :aria-checked="model"
      :aria-label="label"
      class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--primary)]"
      :class="model ? 'bg-[var(--primary)]' : 'bg-white/10'"
      @click="model = !model"
    >
      <span
        class="absolute top-[3px] size-4 rounded-full transition-all duration-300"
        :class="model ? 'left-[21px] bg-white' : 'left-[3px] bg-white/40'"
      />
    </button>
  </div>
</template>
