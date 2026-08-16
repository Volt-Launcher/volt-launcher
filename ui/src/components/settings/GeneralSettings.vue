<script setup lang="ts">
import { useLauncher } from "@/composables/useLauncher";
import { accentColors } from "./settingsData";
import SettingsPanel from "./SettingsPanel.vue";
import SettingsToggle from "./SettingsToggle.vue";
import SettingsRow from "./SettingsRow.vue";

const { t, settings, accentColor, locales } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel :title="t('settings.language')" :description="t('settings.languageHint')" icon="lucide:languages">
      <SettingsRow :label="t('settings.language')">
        <div class="flex gap-2">
          <button
            v-for="option in locales"
            :key="option.id"
            type="button"
            class="rounded-lg border px-3.5 py-1.5 text-[length:var(--text-sm)] font-semibold transition-all duration-200"
            :class="settings.language === option.id
              ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
              : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07]'"
            @click="settings.language = option.id"
          >
            {{ option.label }}
          </button>
        </div>
      </SettingsRow>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.accentColor')" :description="t('settings.accentColorHint')" icon="lucide:palette">
      <div class="flex flex-wrap gap-[7px]">
        <button
          v-for="colour in accentColors"
          :key="colour"
          type="button"
          class="size-[30px] rounded-[7px] border-2 transition-transform duration-200 hover:scale-110"
          :class="accentColor === colour ? 'border-white shadow-[var(--shadow-accent-sm)]' : 'border-white/20'"
          :style="{ background: colour }"
          :aria-label="colour"
          :aria-pressed="accentColor === colour"
          @click="accentColor = colour"
        />
      </div>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.options')" icon="lucide:sliders-horizontal">
      <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
        <SettingsToggle
          v-model="settings.discordPresence"
          :label="t('settings.discordPresence')"
          :description="t('settings.discordPresenceHint')"
        />
        <SettingsToggle
          v-model="settings.hideLauncherOnLaunch"
          :label="t('settings.hideLauncher')"
          :description="t('settings.hideLauncherHint')"
        />
        <SettingsToggle
          v-model="settings.openLogsOnLaunch"
          :label="t('settings.openLogs')"
          :description="t('settings.openLogsHint')"
        />
        <SettingsToggle
          v-model="settings.autoUpdate"
          :label="t('settings.autoUpdate')"
          :description="t('settings.autoUpdateHint')"
        />
      </div>
    </SettingsPanel>
  </div>
</template>
