<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { useLauncher } from '@/composables/useLauncher';
import AuroraBackground from '@/components/AuroraBackground.vue';
import TopBar from '@/components/TopBar.vue';
import StatusBar from '@/components/StatusBar.vue';
import CreateProfileModal from '@/components/CreateProfileModal.vue';
import HomeTab from '@/components/tabs/HomeTab.vue';
import ProfilesTab from '@/components/tabs/ProfilesTab.vue';
import SkinsTab from '@/components/tabs/SkinsTab.vue';
import DiscoverTab from '@/components/tabs/DiscoverTab.vue';
import SettingsTab from '@/components/tabs/SettingsTab.vue';
const { init, cleanup, accentColor } = useLauncher();

const themeVars = computed(() => ({
  '--primary': accentColor.value,
  '--secondary': '#6c63ff',
  '--violet': '#8b5cf6',
  '--accent': '#00ffcc',
}));

onMounted(() => {
  init();
});
onBeforeUnmount(() => {
  cleanup();
});
</script>
<template>
  <div
    class="relative flex h-full w-full flex-col overflow-hidden bg-[#030912] text-[rgba(255,255,255,0.96)]"
    :style="themeVars"
  >
    <AuroraBackground />
    <TopBar />
    <div class="relative z-10 flex flex-1 flex-col overflow-hidden">
      <HomeTab />
      <ProfilesTab />
      <SkinsTab />
      <DiscoverTab />
      <SettingsTab />
    </div>
    <StatusBar />
    <CreateProfileModal />
  </div>
</template>
