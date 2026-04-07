<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import ModrinthDiscover from "@/components/ModrinthDiscover.vue";

const { activeTab, discoverTabActive, discoverPlatformActive } = useLauncher();

const discoverTabs = [
  { id: "MODPACKS", icon: "lucide:package" },
  { id: "MODS", icon: "lucide:puzzle" },
  { id: "RESOURCE PACKS", icon: "lucide:palette" },
  { id: "SHADERS", icon: "lucide:sparkles" },
  { id: "DATA PACKS", icon: "lucide:database" },
];

const discoverPlatforms = [
  { id: "MODRINTH", icon: "simple-icons:modrinth", color: "#00C853" },
  { id: "CURSEFORGE", icon: "simple-icons:curseforge", color: "#FF6D00" },
  { id: "TECHNIC", icon: "lucide:wrench", color: "#2962FF" },
  { id: "FTB", icon: "lucide:flame", color: "#D50000" },
];
</script>

<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'discover' ? 'flex' : 'hidden'">
    <!-- Top tabs + platform selector -->
    <div class="flex shrink-0 flex-col gap-3 border-b border-white/[0.06] px-4 pt-4 pb-3 md:px-6">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex gap-2">
          <button v-for="dt in discoverTabs" :key="dt.id" type="button"
            class="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-[length:var(--text-sm)] font-semibold tracking-[0.07em] transition-all duration-200"
            :class="discoverTabActive === dt.id
              ? 'border-[var(--accent-border-active)] bg-[var(--accent-bg-strong)] text-[var(--primary)]'
              : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07] hover:text-white/70'"
            @click="discoverTabActive = dt.id">
            <Icon :icon="dt.icon" class="size-[11px]" />
            {{ dt.id }}
          </button>
        </div>
        <div class="flex gap-2">
          <button v-for="dp in discoverPlatforms" :key="dp.id" type="button"
            class="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-[length:var(--text-sm)] font-semibold tracking-[0.07em] transition-all duration-200"
            :class="discoverPlatformActive === dp.id
              ? 'border-transparent'
              : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07] hover:text-white/70'"
            :style="discoverPlatformActive === dp.id
              ? `color: ${dp.color}; background: ${dp.color}18; border-color: ${dp.color}30`
              : undefined"
            @click="discoverPlatformActive = dp.id">
            <Icon :icon="dp.icon" class="size-[11px]" />
            {{ dp.id }}
          </button>
        </div>
      </div>
    </div>

    <!-- Platform content -->
    <ModrinthDiscover v-if="discoverPlatformActive === 'MODRINTH'" class="flex flex-1 flex-col overflow-hidden" />

    <!-- Placeholder for other platforms -->
    <div v-else class="flex flex-1 flex-col items-center justify-center gap-3">
      <Icon icon="lucide:construction" class="size-10 text-white/15" />
      <div class="text-[length:var(--text-md)] font-semibold text-white/30">{{ discoverPlatformActive }} coming soon</div>
      <div class="text-[length:var(--text-sm)] text-white/20">This platform is not yet supported</div>
    </div>
  </div>
</template>
