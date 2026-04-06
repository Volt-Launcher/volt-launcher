<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  authData,
  isAuthenticating,
  error,
  launcherMessage,
  activeTab,
  playerAvatarUrl,
  playerAvatarFallback,
  handleLogin,
  handleLogout,
  handleImgError,
} = useLauncher();

const navItems = [
  { id: 'home', label: 'PLAY', icon: 'lucide:play' },
  { id: 'profiles', label: 'PROFILES', icon: 'lucide:layers' },
  { id: 'skins', label: 'SKINS', icon: 'lucide:shirt' },
  { id: 'discover', label: 'DISCOVER', icon: 'lucide:search' },
  { id: 'settings', label: 'SETTINGS', icon: 'lucide:settings' },
] as const;
</script>
<template>
  <header class="relative z-[60] flex min-h-[52px] shrink-0 flex-wrap items-center gap-2 border-b border-white/5 bg-[rgba(3,9,18,.95)] px-3 py-3 backdrop-blur-2xl md:flex-nowrap md:px-4 md:py-0">
    <div class="mr-2 flex items-center gap-2 md:mr-7">
      <!--<div class="mr-2 hidden gap-[5px] md:flex">
        <div class="h-3 w-3 rounded-full bg-[#ff5f57]"></div>
        <div class="h-3 w-3 rounded-full bg-[#febc2e]"></div>
        <div class="h-3 w-3 rounded-full bg-[#28c840]"></div>
      </div>-->
      <div class="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-[7px] bg-gradient-to-br from-[var(--primary)] to-[var(--secondary)] text-white shadow-[0_0_16px_rgba(0,178,255,.4)]">
        <Icon icon="lucide:zap" class="size-4" />
      </div>
      <div class="whitespace-nowrap text-[13px] font-bold tracking-[0.11em] text-white">
        THE<span class="text-[var(--primary)]">LAUNCHER</span>
      </div>
    </div>
    <nav class="order-3 flex w-full flex-wrap gap-2 md:order-none md:w-auto md:flex-1">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        class="inline-flex items-center gap-[7px] rounded-[7px] border px-4 py-2 text-[12px] font-semibold tracking-[0.08em] transition-all duration-200"
        :class="activeTab === item.id
          ? 'border-[rgba(0,178,255,.2)] bg-[rgba(0,178,255,.1)] text-white'
          : 'border-transparent bg-transparent text-white/60 hover:bg-white/5 hover:text-white'"
        @click="activeTab = item.id"
      >
        <Icon :icon="item.icon" class="size-[13px]" :class="activeTab === item.id ? 'text-[var(--primary)]' : ''" />
        {{ item.label }}
      </button>
    </nav>
    <div class="ml-auto flex items-center gap-2">
      <button
        type="button"
        class="relative flex size-8 items-center justify-center rounded-[7px] border border-white/10 bg-white/5 text-white/40 transition-all duration-200 hover:bg-white/10 hover:text-white"
        :title="error ?? launcherMessage ?? ''"
        @click="error = null; launcherMessage = null;"
      >
        <Icon icon="lucide:bell" class="size-[14px]" />
        <span
          v-if="error || launcherMessage"
          class="absolute right-[7px] top-[7px] size-[6px] rounded-full"
          :class="error ? 'bg-[#ff5f57] shadow-[0_0_5px_#ff5f57]' : 'bg-[var(--primary)] shadow-[0_0_5px_var(--primary)]'"
        ></span>
      </button>
      <button
        type="button"
        class="flex items-center gap-2 rounded-lg border border-white/10 bg-white/5 px-[11px] py-[5px] pl-[6px] transition-all duration-200 hover:bg-white/10"
        @click="authData ? handleLogout() : handleLogin()"
      >
        <img
          :src="playerAvatarUrl"
          :data-fallback-src="playerAvatarFallback"
          alt=""
          class="size-6 rounded-[4px] object-cover [image-rendering:pixelated]"
          @error="handleImgError"
        />
        <span class="text-[12px] font-semibold tracking-[0.07em] text-white">
          {{ authData ? authData.username.toUpperCase() : (isAuthenticating ? 'WARTEN…' : 'LOGIN') }}
        </span>
        <Icon icon="lucide:chevron-down" class="size-[9px] text-white/40" />
      </button>
    </div>
  </header>
</template>
