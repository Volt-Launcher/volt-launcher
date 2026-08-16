<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import SettingsPanel from "./SettingsPanel.vue";

const { t, settings, java } = useLauncher();

onMounted(() => void java.loadRuntimes());
onBeforeUnmount(() => java.stopAllPolling());

/** Memory is stored in megabytes but presented in gigabytes, which is how people think about it. */
const minMemoryGb = computed({
  get: () => Math.round((settings.value.defaultMinMemoryMb / 1024) * 10) / 10,
  set: (value: number) => {
    settings.value.defaultMinMemoryMb = Math.round(value * 1024);
  },
});

const maxMemoryGb = computed({
  get: () => Math.round((settings.value.defaultMaxMemoryMb / 1024) * 10) / 10,
  set: (value: number) => {
    settings.value.defaultMaxMemoryMb = Math.round(value * 1024);
    // The minimum can never exceed the maximum, so pull it down with the slider.
    if (settings.value.defaultMinMemoryMb > settings.value.defaultMaxMemoryMb) {
      settings.value.defaultMinMemoryMb = settings.value.defaultMaxMemoryMb;
    }
  },
});

/** One row per offerable Java version, merged with what is installed and what is downloading. */
const runtimeRows = computed(() =>
  java.installable.value.map((majorVersion) => {
    const detected = java.detected.value.find((runtime) => runtime.majorVersion === majorVersion);
    const managed = java.managed.value.find((runtime) => runtime.majorVersion === majorVersion);
    const configured = settings.value.javaRuntimes.find((entry) => entry.majorVersion === majorVersion);
    const job = java.installJobs.value[majorVersion];

    return {
      majorVersion,
      available: Boolean(detected ?? managed),
      path: configured?.path ?? managed?.path ?? detected?.path ?? "",
      source: managed ? t("settings.javaManaged") : detected ? detected.source : "",
      downloading: job?.phase === "downloading",
      failed: job?.phase === "failed",
      message: job?.message ?? "",
    };
  }),
);

/** Writes a manual override, or clears it when the field is emptied. */
const setCustomPath = (majorVersion: number, path: string) => {
  const trimmed = path.trim();
  const others = settings.value.javaRuntimes.filter((entry) => entry.majorVersion !== majorVersion);
  settings.value.javaRuntimes = trimmed ? [...others, { majorVersion, path: trimmed }] : others;
};

const customPathOf = (majorVersion: number) =>
  settings.value.javaRuntimes.find((entry) => entry.majorVersion === majorVersion)?.path ?? "";
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel
      :title="t('settings.memory')"
      :description="t('settings.memoryHint')"
      icon="lucide:memory-stick"
    >
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <VoltSlider v-model="minMemoryGb" :min="1" :max="16" :step="1" :label="t('settings.minMemory')" />
        <VoltSlider v-model="maxMemoryGb" :min="1" :max="32" :step="1" :label="t('settings.maxMemory')" />
      </div>
    </SettingsPanel>

    <SettingsPanel
      :title="t('settings.javaRuntimes')"
      :description="t('settings.javaRuntimesHint')"
      icon="lucide:coffee"
    >
      <template #action>
        <button
          type="button"
          class="inline-flex items-center gap-1 rounded-md border border-white/10 bg-white/5 px-2 py-1 text-[length:var(--text-2xs)] font-semibold tracking-[0.07em] text-white/50 transition-all duration-200 hover:bg-white/10 hover:text-white/80"
          :disabled="java.isLoading.value"
          @click="java.loadRuntimes()"
        >
          <Icon
            icon="lucide:refresh-cw"
            class="size-[10px]"
            :class="java.isLoading.value ? 'animate-spin' : ''"
          />
          {{ t("common.retry") }}
        </button>
      </template>

      <div class="flex flex-col gap-2">
        <div
          v-for="row in runtimeRows"
          :key="row.majorVersion"
          class="rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-3 py-2.5"
        >
          <div class="flex flex-wrap items-center gap-2">
            <div
              class="flex size-9 shrink-0 items-center justify-center rounded-lg text-[length:var(--text-sm)] font-bold"
              :class="row.available
                ? 'bg-[var(--accent-bg-strong)] text-[var(--primary)]'
                : 'bg-white/5 text-white/30'"
            >
              {{ row.majorVersion }}
            </div>

            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-[length:var(--text-base-plus)] font-semibold text-white">
                  Java {{ row.majorVersion }}
                </span>
                <span
                  v-if="row.available"
                  class="rounded-[4px] bg-[var(--accent-bg-soft)] px-1.5 py-0.5 text-[length:var(--text-2xs)] font-bold tracking-[0.1em] text-[var(--primary)]/80"
                >
                  {{ t("settings.javaInstalled") }}
                </span>
              </div>
              <div class="truncate font-mono text-[length:var(--text-2xs)] text-white/35">
                {{ row.path || t("settings.javaNoneDetected") }}
              </div>
            </div>

            <button
              v-if="!row.available && !row.downloading"
              type="button"
              class="inline-flex shrink-0 items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3 py-[6px] text-[length:var(--text-sm)] font-semibold text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)]"
              @click="java.installRuntime(row.majorVersion)"
            >
              <Icon icon="lucide:download" class="size-[11px]" />
              {{ t("settings.javaInstall") }}
            </button>

            <span
              v-else-if="row.downloading"
              class="inline-flex shrink-0 items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3 py-[6px] text-[length:var(--text-sm)] font-semibold text-white/60"
            >
              <Icon icon="lucide:loader-2" class="size-[11px] animate-spin" />
              {{ t("settings.javaInstalling") }}
            </span>
          </div>

          <div v-if="row.failed" class="mt-2 text-[length:var(--text-2xs)] text-[var(--danger-text)]">
            {{ row.message }}
          </div>

          <input
            :value="customPathOf(row.majorVersion)"
            type="text"
            :placeholder="t('settings.javaAutoDetect')"
            class="mt-2 w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-1.5 font-mono text-[length:var(--text-2xs)] text-white/70 placeholder-white/25 outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)]"
            @change="setCustomPath(row.majorVersion, ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.jvmArgs')" :description="t('settings.jvmArgsHint')" icon="lucide:terminal">
      <input
        v-model="settings.defaultJvmArgs"
        type="text"
        class="w-full rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 font-mono text-[length:var(--text-2xs)] text-white/70 outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)]"
      />
    </SettingsPanel>
  </div>
</template>
