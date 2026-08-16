<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import SettingsPanel from "./SettingsPanel.vue";
import SettingsToggle from "./SettingsToggle.vue";

const { t, settings, launcherVersion } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel
      :title="t('settings.updateChannel')"
      :description="t('settings.updateChannelHint')"
      icon="lucide:download"
    >
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <SettingsToggle
          v-model="settings.autoUpdate"
          :label="t('settings.autoUpdate')"
          :description="t('settings.autoUpdateHint')"
        />
        <SettingsToggle
          v-model="settings.betaUpdates"
          :label="t('settings.betaUpdates')"
          :description="t('settings.betaUpdatesHint')"
        />
      </div>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.checkUpdates')" icon="lucide:refresh-cw">
      <div class="flex flex-wrap items-center gap-3">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
        >
          <Icon icon="lucide:refresh-cw" class="size-[11px]" />
          {{ t("settings.checkUpdates") }}
        </button>
        <span class="text-[length:var(--text-2xs)] text-white/40">
          v{{ launcherVersion || "0.2.0" }} · {{ t("settings.upToDate") }}
        </span>
      </div>
    </SettingsPanel>
  </div>
</template>
