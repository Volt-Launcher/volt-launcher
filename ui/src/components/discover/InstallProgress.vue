<script setup lang="ts">
import { computed } from "vue";
import { Icon } from "@iconify/vue";
import { t } from "@/i18n";

/**
 * Progress readout for a running install.
 *
 * Countable work gets a determinate bar with a file counter; steps that cannot be counted
 * (reading the archive, running loader processors) get an indeterminate sweep, so the bar never
 * sits frozen at a number that is not actually advancing.
 */
const props = defineProps<{
  stage: string | null;
  completed: number;
  total: number;
  percent: number;
  compact?: boolean;
}>();

const determinate = computed(() => props.total > 0 && props.percent >= 0);

const label = computed(() => (props.stage ? t(`progress.${props.stage}`) : t("common.loading")));

const counter = computed(() =>
  determinate.value ? t("progress.ofFiles", { completed: props.completed, total: props.total }) : "",
);
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <div class="flex items-center gap-2">
      <Icon icon="lucide:loader-2" class="size-[12px] shrink-0 animate-spin text-[var(--primary)]" />
      <span
        class="min-w-0 flex-1 truncate text-white/70"
        :class="compact ? 'text-[length:var(--text-2xs)]' : 'text-[length:var(--text-sm)]'"
      >
        {{ label }}
      </span>
      <span
        v-if="determinate"
        class="shrink-0 font-mono tabular-nums text-[var(--primary)]"
        :class="compact ? 'text-[length:var(--text-2xs)]' : 'text-[length:var(--text-sm)]'"
      >
        {{ percent }}%
      </span>
    </div>

    <div class="h-[4px] w-full overflow-hidden rounded-full bg-white/10">
      <div
        v-if="determinate"
        class="h-full rounded-full bg-[var(--primary)] transition-[width] duration-300 ease-out"
        :style="{ width: `${percent}%` }"
      />
      <div
        v-else
        class="h-full w-1/3 rounded-full bg-[var(--primary)] animate-[shimmer_1.6s_ease-in-out_infinite]"
      />
    </div>

    <div v-if="counter && !compact" class="text-[length:var(--text-2xs)] tabular-nums text-white/35">
      {{ counter }}
    </div>
  </div>
</template>
