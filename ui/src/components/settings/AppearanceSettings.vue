<script setup lang="ts">
import { computed } from "vue";
import { useLauncher } from "@/composables/useLauncher";
import SettingsPanel from "./SettingsPanel.vue";
import SettingsToggle from "./SettingsToggle.vue";

const { t, settings, uiScale } = useLauncher();

const scales = computed(() => [
  { value: "compact" as const, label: t("settings.scaleCompact") },
  { value: "default" as const, label: t("settings.scaleDefault") },
  { value: "comfortable" as const, label: t("settings.scaleComfortable") },
]);
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel :title="t('settings.uiScale')" :description="t('settings.uiScaleHint')" icon="lucide:maximize">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="scale in scales"
          :key="scale.value"
          type="button"
          class="rounded-lg border px-4 py-2 text-[length:var(--text-sm)] font-semibold tracking-[0.06em] transition-all duration-200"
          :class="uiScale === scale.value
            ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
            : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07]'"
          @click="uiScale = scale.value"
        >
          {{ scale.label }}
        </button>
      </div>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.visual')" icon="lucide:sparkles">
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <SettingsToggle
          v-model="settings.animationsEnabled"
          :label="t('settings.animations')"
          :description="t('settings.animationsHint')"
        />
        <SettingsToggle
          v-model="settings.showFps"
          :label="t('settings.showFps')"
          :description="t('settings.showFpsHint')"
        />
      </div>
    </SettingsPanel>
  </div>
</template>
