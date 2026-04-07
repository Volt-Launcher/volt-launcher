<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

export interface CustomSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(defineProps<{
  modelValue: string;
  options: CustomSelectOption[];
  placeholder?: string;
  disabled?: boolean;
}>(), {
  placeholder: "Auswählen",
  disabled: false,
});

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const rootEl = ref<HTMLElement | null>(null);
const isOpen = ref(false);

const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue) ?? null);

const close = () => {
  isOpen.value = false;
};

const toggle = () => {
  if (props.disabled) {
    return;
  }

  isOpen.value = !isOpen.value;
};

const selectOption = (option: CustomSelectOption) => {
  if (option.disabled) {
    return;
  }

  emit("update:modelValue", option.value);
  close();
};

const onDocumentPointerDown = (event: Event) => {
  if (!rootEl.value) {
    return;
  }

  const target = event.target;
  if (target instanceof Node && !rootEl.value.contains(target)) {
    close();
  }
};

const onDocumentKeydown = (event: KeyboardEvent) => {
  if (event.key === "Escape") {
    close();
  }
};

onMounted(() => {
  document.addEventListener("pointerdown", onDocumentPointerDown);
  document.addEventListener("keydown", onDocumentKeydown);
});

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", onDocumentPointerDown);
  document.removeEventListener("keydown", onDocumentKeydown);
});
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-3 rounded-lg border border-white/10 bg-[var(--surface-input)] px-[13px] py-2.5 text-left text-[length:var(--text-md)] text-white outline-none transition-all duration-200 hover:border-white/15 hover:bg-[var(--surface-panel-strong)] hover:shadow-[0_10px_24px_rgba(0,0,0,.16)] focus-visible:border-[var(--accent-border-focus)] focus-visible:ring-2 focus-visible:ring-[var(--accent-bg)] disabled:cursor-not-allowed disabled:opacity-60"
      :class="isOpen ? 'border-[var(--accent-border-focus)] bg-[var(--surface-panel-strong)] shadow-[0_12px_28px_rgba(0,0,0,.22)]' : ''"
      :aria-expanded="isOpen"
      aria-haspopup="listbox"
      :disabled="disabled"
      @click="toggle"
    >
      <span class="truncate" :class="selectedOption ? 'text-white' : 'text-white/40'">
        {{ selectedOption?.label ?? placeholder }}
      </span>
      <Icon
        icon="lucide:chevron-down"
        class="size-[15px] shrink-0 text-white/45 transition-all duration-200"
        :class="isOpen ? 'rotate-180 text-[var(--primary)]' : ''"
      />
    </button>

    <Transition name="ui-select-menu">
      <div
        v-if="isOpen"
        class="absolute left-0 right-0 top-[calc(100%+8px)] z-50 overflow-hidden rounded-xl border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] shadow-[var(--shadow-modal)] ring-1 ring-white/5 backdrop-blur-xl"
      >
        <div class="max-h-60 overflow-y-auto p-1.5">
          <button
            v-for="option in options"
            :key="option.value"
            type="button"
            role="option"
            class="flex w-full items-start justify-between gap-3 rounded-[10px] px-3 py-2.5 text-left text-[length:var(--text-sm)] outline-none transition-all duration-200 focus-visible:bg-white/5"
            :class="option.value === modelValue
              ? 'bg-[var(--accent-bg-strong)] text-[var(--primary)]'
              : 'text-white/70 hover:bg-white/[0.07] hover:text-white'"
            :aria-selected="option.value === modelValue"
            :disabled="option.disabled"
            @click="selectOption(option)"
          >
            <span class="leading-[1.45]">{{ option.label }}</span>
            <Transition name="ui-check-icon" mode="out-in">
              <Icon v-if="option.value === modelValue" icon="lucide:check" class="mt-0.5 size-[13px] shrink-0 text-[var(--primary)]" />
              <span v-else class="size-[13px] shrink-0"></span>
            </Transition>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>