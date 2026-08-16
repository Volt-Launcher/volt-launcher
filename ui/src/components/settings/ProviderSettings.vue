<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import SettingsPanel from "./SettingsPanel.vue";

const { t, settings, providers, loadProviders, isLoadingProviders } = useLauncher();

const isTesting = ref(false);

onMounted(() => void loadProviders());

/** Re-runs the backend's provider health check, which is what decides bridge availability. */
const testConnection = async () => {
  isTesting.value = true;
  try {
    await loadProviders();
  } finally {
    isTesting.value = false;
  }
};
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel :title="t('settings.providers')" :description="t('settings.providersHint')" icon="lucide:blocks">
      <div class="flex flex-col gap-2">
        <div
          v-for="provider in providers"
          :key="provider.id"
          class="flex flex-wrap items-center gap-3 rounded-[10px] border border-white/10 bg-[var(--surface-input-muted)] px-[15px] py-[13px]"
        >
          <Icon
            :icon="provider.id === 'modrinth' ? 'simple-icons:modrinth' : 'simple-icons:curseforge'"
            class="size-[18px] shrink-0"
            :style="{ color: provider.id === 'modrinth' ? '#00C853' : '#FF6D00' }"
          />
          <div class="min-w-0 flex-1">
            <div class="text-[length:var(--text-base-plus)] font-semibold text-white">
              {{ provider.displayName }}
            </div>
            <div
              class="text-[length:var(--text-2xs-plus)] leading-[1.45]"
              :class="provider.available ? 'text-[var(--primary)]/70' : 'text-white/50'"
            >
              {{ provider.available ? t("settings.bridgeOnline") : provider.unavailableReason }}
            </div>
          </div>
          <span
            class="size-2 shrink-0 rounded-full"
            :class="provider.available ? 'bg-emerald-400' : 'bg-white/20'"
          />
        </div>
      </div>
    </SettingsPanel>

    <SettingsPanel :title="t('settings.bridgeUrl')" :description="t('settings.bridgeUrlHint')" icon="lucide:link">
      <div class="flex flex-wrap items-center gap-2">
        <input
          v-model="settings.curseForgeBridgeUrl"
          type="text"
          spellcheck="false"
          placeholder="http://localhost:8787"
          class="min-w-0 flex-1 rounded-lg border border-white/10 bg-[var(--surface-input)] px-3 py-2 font-mono text-[length:var(--text-2xs)] text-white/80 placeholder-white/25 outline-none transition-colors duration-200 focus:border-[var(--accent-border-focus)]"
        />
        <button
          type="button"
          class="inline-flex shrink-0 items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold text-white/60 transition-all duration-200 hover:bg-white/10 hover:text-white/90"
          :disabled="isTesting || isLoadingProviders"
          @click="testConnection"
        >
          <Icon
            icon="lucide:plug-zap"
            class="size-[11px]"
            :class="isTesting || isLoadingProviders ? 'animate-pulse' : ''"
          />
          {{ t("settings.testConnection") }}
        </button>
      </div>
    </SettingsPanel>
  </div>
</template>
