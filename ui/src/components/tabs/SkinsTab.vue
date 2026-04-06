<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  activeTab,
  playerSkinUrl,
  playerSkinFallback,
  handleImgError,
} = useLauncher();
</script>
<template>
  <div class="flex-1 items-center justify-center" :class="activeTab === 'skins' ? 'flex' : 'hidden'">
    <div class="flex flex-col items-center justify-center gap-4 px-6 text-center">
      <div class="rounded-full border border-white/10 bg-white/5 p-5 text-[var(--primary)] shadow-[0_0_24px_rgba(0,178,255,.18)]">
        <Icon icon="lucide:shirt" class="size-12" />
      </div>
      <div class="text-[18px] font-bold tracking-[0.12em] text-white">SKINS</div>
      <div class="max-w-[260px] text-[12px] leading-[1.6] text-white/40">Skin-Verwaltung wird in einer zukünftigen Version verfügbar sein.</div>
      <img
        :src="playerSkinUrl"
        :data-fallback-src="playerSkinFallback"
        alt="Skin"
        class="mt-2 h-[180px] object-contain drop-shadow-[0_12px_32px_rgba(0,178,255,.28)]"
        @error="handleImgError"
      />
    </div>
  </div>
</template>
