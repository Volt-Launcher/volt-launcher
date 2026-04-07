<script setup lang="ts">
import { Icon } from "@iconify/vue";

const props = withDefaults(defineProps<{
  modelValue: boolean;
  disabled?: boolean;
}>(), {
  disabled: false,
});

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
}>();

const toggle = () => {
  if (props.disabled) {
    return;
  }

  emit("update:modelValue", !props.modelValue);
};
</script>

<template>
  <button
    type="button"
    role="checkbox"
    :aria-checked="modelValue"
    :aria-disabled="disabled"
    class="inline-flex items-center gap-1.5 rounded-md border px-3.5 py-1.5 text-[length:var(--text-sm)] font-semibold tracking-[0.07em] outline-none transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2"
    :class="modelValue
      ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)] focus-visible:ring-[var(--accent-bg-hover)]'
      : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07] hover:text-white/70 focus-visible:border-[var(--accent-border-focus)] focus-visible:ring-[var(--accent-bg)]'"
    :disabled="disabled"
    @click="toggle"
  >
    <Transition name="ui-check-icon" mode="out-in">
      <Icon v-if="modelValue" icon="lucide:check" class="size-[11px]" />
      <Icon v-else icon="lucide:x" class="size-[11px]" />
    </Transition>
    <slot />
  </button>
</template>