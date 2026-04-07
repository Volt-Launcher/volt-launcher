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
  handleWindowMinimize,
  handleWindowMaximize,
  handleWindowClose,
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
  <header class="relative z-[60] flex min-h-[52px] shrink-0 flex-wrap items-center gap-2 border-b border-white/5 bg-[var(--header-bg)] px-3 py-3 backdrop-blur-2xl md:flex-nowrap md:px-4 md:py-0">
    <div class="mr-2 flex items-center gap-2 md:mr-7">
      <!--<div class="mr-2 hidden gap-[5px] md:flex">
        <div class="h-3 w-3 rounded-full bg-[#ff5f57]"></div>
        <div class="h-3 w-3 rounded-full bg-[#febc2e]"></div>
        <div class="h-3 w-3 rounded-full bg-[#28c840]"></div>
      </div>-->
      <div class="flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-[7px] bg-gradient-to-br from-[var(--primary)] to-[var(--secondary)] text-white shadow-[var(--shadow-accent-lg)]">
        <Icon icon="lucide:zap" class="size-4" />
      </div>
      <div class="whitespace-nowrap text-[length:var(--text-md)] font-bold tracking-[0.11em] text-white">
        THE<span class="text-[var(--primary)]">LAUNCHER</span>PROJECT
      </div>
    </div>
    <nav class="order-3 flex w-full flex-wrap gap-2 md:order-none md:w-auto md:flex-1">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        class="inline-flex cursor-pointer items-center gap-[7px] rounded-[7px] border px-4 py-2 text-[length:var(--text-base)] font-semibold tracking-[0.08em] transition-all duration-200"
        :class="activeTab === item.id
          ? 'border-[var(--accent-border)] bg-[var(--accent-bg)] text-white'
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
          :class="error ? 'bg-[var(--danger)] shadow-[var(--shadow-danger-xs)]' : 'bg-[var(--primary)] shadow-[var(--shadow-accent-md)]'"
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
        <span class="text-[length:var(--text-base)] font-semibold tracking-[0.07em] text-white">
          {{ authData ? authData.username.toUpperCase() : (isAuthenticating ? 'WAITING…' : 'LOGIN') }}
        </span>
        <Icon icon="lucide:chevron-down" class="size-[9px] text-white/40" />
      </button>

      <!-- Window controls -->
      <div class="ml-1 flex items-center gap-[3px]">
        <button type="button" class="group cursor-pointer flex size-[30px] items-center justify-center rounded-[6px] transition-colors duration-150 hover:bg-white/10" title="Minimize" @click="handleWindowMinimize">
          <Icon icon="lucide:minus" class="size-[13px] text-white/40 transition-colors group-hover:text-white/80" />
        </button>
        <button type="button" class="group cursor-pointer flex size-[30px] items-center justify-center rounded-[6px] transition-colors duration-150 hover:bg-white/10" title="Maximize" @click="handleWindowMaximize">
          <Icon icon="lucide:square" class="size-[11px] text-white/40 transition-colors group-hover:text-white/80" />
        </button>
        <button type="button" class="group cursor-pointer flex size-[30px] items-center justify-center rounded-[6px] transition-colors duration-150 hover:bg-[#e81123]/80" title="Close" @click="handleWindowClose">
          <Icon icon="lucide:x" class="size-[13px] text-white/40 transition-colors group-hover:text-white" />
        </button>
      </div>
    </div>
  </header>
</template>
