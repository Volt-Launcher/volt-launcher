<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import { settingsNav } from "@/components/settings/settingsData";
import GeneralSettings from "@/components/settings/GeneralSettings.vue";
import AppearanceSettings from "@/components/settings/AppearanceSettings.vue";
import JavaMemorySettings from "@/components/settings/JavaMemorySettings.vue";
import UpdatesSettings from "@/components/settings/UpdatesSettings.vue";
import AccountSettings from "@/components/settings/AccountSettings.vue";
import AdvancedSettings from "@/components/settings/AdvancedSettings.vue";

const { activeTab, settingsNavItem } = useLauncher();
</script>
<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'settings' ? 'flex' : 'hidden'">
    <div class="flex flex-1 flex-col gap-5 overflow-y-auto p-4 md:flex-row md:p-6">
      <div class="flex w-full shrink-0 flex-col gap-[3px] md:w-[175px]">
        <div class="px-[13px] pt-1 pb-2 text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/40">SETTINGS</div>
        <button
          v-for="item in settingsNav"
          :key="item.id"
          type="button"
          class="flex items-center gap-2.5 rounded-lg border border-transparent px-[13px] py-[9px] text-left text-[length:var(--text-base-plus)] font-semibold tracking-[0.06em] text-white/60 transition-all duration-200 hover:bg-white/5 hover:text-white/95"
          :class="settingsNavItem === item.id ? 'border-[var(--accent-border)] bg-[var(--accent-bg)] text-[var(--primary)]' : ''"
          @click="settingsNavItem = item.id"
        >
          <Icon :icon="item.icon" class="size-[13px]" />
          {{ item.id }}
        </button>
        <div class="mt-auto pt-3">
          <button type="button" class="inline-flex w-full items-center justify-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]">
            <Icon icon="lucide:folder-open" class="size-[11px]" />Directory
          </button>
        </div>
      </div>

      <div class="flex flex-1 flex-col gap-3 overflow-y-auto">
        <GeneralSettings v-if="settingsNavItem === 'General'" />
        <AppearanceSettings v-else-if="settingsNavItem === 'Appearance'" />
        <JavaMemorySettings v-else-if="settingsNavItem === 'Java & Memory'" />
        <UpdatesSettings v-else-if="settingsNavItem === 'Updates'" />
        <AccountSettings v-else-if="settingsNavItem === 'Account'" />
        <AdvancedSettings v-else-if="settingsNavItem === 'Advanced'" />
      </div>
    </div>
  </div>
</template>

