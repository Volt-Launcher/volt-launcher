<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import { settingsNav } from "@/components/settings/settingsData";
import GeneralSettings from "@/components/settings/GeneralSettings.vue";
import AppearanceSettings from "@/components/settings/AppearanceSettings.vue";
import JavaMemorySettings from "@/components/settings/JavaMemorySettings.vue";
import ProviderSettings from "@/components/settings/ProviderSettings.vue";
import UpdatesSettings from "@/components/settings/UpdatesSettings.vue";
import AccountSettings from "@/components/settings/AccountSettings.vue";
import AdvancedSettings from "@/components/settings/AdvancedSettings.vue";

const { t, activeTab, settingsNavItem, isSavingSettings, openDirectory } = useLauncher();
</script>

<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'settings' ? 'flex' : 'hidden'">
    <div class="flex flex-1 flex-col gap-5 overflow-hidden p-4 md:flex-row md:p-6">
      <!-- Section navigation -->
      <nav class="flex w-full shrink-0 flex-col gap-[3px] overflow-y-auto md:w-[190px]">
        <div class="flex items-center justify-between px-[13px] pt-1 pb-2">
          <span class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40 uppercase">
            {{ t("settings.title") }}
          </span>
          <Icon
            v-if="isSavingSettings"
            icon="lucide:loader-2"
            class="size-[11px] animate-spin text-white/30"
            :aria-label="t('common.saving')"
          />
        </div>

        <button
          v-for="item in settingsNav"
          :key="item.id"
          type="button"
          class="flex items-center gap-2.5 rounded-lg border border-transparent px-[13px] py-[9px] text-left text-[length:var(--text-base-plus)] font-semibold tracking-[0.06em] text-white/60 transition-all duration-200 hover:bg-white/5 hover:text-white/95"
          :class="settingsNavItem === item.id ? 'border-[var(--accent-border)] bg-[var(--accent-bg)] text-[var(--primary)]' : ''"
          @click="settingsNavItem = item.id"
        >
          <Icon :icon="item.icon" class="size-[13px] shrink-0" />
          {{ t(item.labelKey) }}
        </button>

        <div class="mt-auto pt-3">
          <button
            type="button"
            class="inline-flex w-full items-center justify-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
            @click="openDirectory('root')"
          >
            <Icon icon="lucide:folder-open" class="size-[11px]" />
            {{ t("settings.directories") }}
          </button>
        </div>
      </nav>

      <!-- Active section -->
      <div class="flex flex-1 flex-col gap-3 overflow-y-auto pr-1">
        <GeneralSettings v-if="settingsNavItem === 'general'" />
        <AppearanceSettings v-else-if="settingsNavItem === 'appearance'" />
        <JavaMemorySettings v-else-if="settingsNavItem === 'java'" />
        <ProviderSettings v-else-if="settingsNavItem === 'providers'" />
        <UpdatesSettings v-else-if="settingsNavItem === 'updates'" />
        <AccountSettings v-else-if="settingsNavItem === 'account'" />
        <AdvancedSettings v-else-if="settingsNavItem === 'advanced'" />
      </div>
    </div>
  </div>
</template>
