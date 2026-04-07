<script setup lang="ts">
import { computed, ref } from "vue";

const props = withDefaults(defineProps<{
  modelValue: number;
  min?: number;
  max?: number;
  step?: number;
  label?: string;
}>(), {
  min: 1,
  max: 10,
  step: 1,
});

const emit = defineEmits<{ "update:modelValue": [value: number] }>();

const trackRef = ref<HTMLDivElement | null>(null);
const dragging = ref(false);

const pct = computed(() =>
  ((props.modelValue - props.min) / (props.max - props.min)) * 100,
);

function valueFromEvent(e: MouseEvent | PointerEvent) {
  if (!trackRef.value) return props.modelValue;
  const rect = trackRef.value.getBoundingClientRect();
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
  const raw = props.min + ratio * (props.max - props.min);
  return Math.round(raw / props.step) * props.step;
}

function onPointerDown(e: PointerEvent) {
  dragging.value = true;
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
  emit("update:modelValue", valueFromEvent(e));
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return;
  emit("update:modelValue", valueFromEvent(e));
}

function onPointerUp() {
  dragging.value = false;
}
</script>

<template>
  <div>
    <div v-if="label" class="mb-2 flex justify-between">
      <span class="text-[length:var(--text-xs)] font-semibold text-white">{{ label }}</span>
      <span class="text-[length:var(--text-xs)] font-bold text-[var(--primary)]">{{ modelValue }}</span>
    </div>
    <div
      ref="trackRef"
      class="relative h-1 cursor-pointer select-none rounded-sm bg-white/10"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <div
        class="pointer-events-none absolute left-0 top-0 h-full rounded-sm bg-gradient-to-r from-[var(--primary)] to-[var(--secondary)]"
        :style="{ width: pct + '%' }"
      />
      <div
        class="pointer-events-none absolute top-1/2 h-[13px] w-[13px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--primary)] shadow-[var(--shadow-accent-md)] transition-[left] duration-75"
        :style="{ left: pct + '%' }"
      />
    </div>
    <div class="mt-[5px] flex justify-between text-[9px] text-white/20">
      <span>{{ min }}</span>
      <span>{{ Math.round((min + max) / 2) }}</span>
      <span>{{ max }}</span>
    </div>
  </div>
</template>
