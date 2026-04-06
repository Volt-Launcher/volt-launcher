<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  authData, instances, availableVersions,
  selectedInstanceName, newInstanceName, selectedVersionId,
  includeSnapshots, includeBetas, includeAlphas,
  isAuthenticating, isLaunching, isCreatingInstance, isLoadingInstances, isLoadingVersions,
  error, launcherMessage, authUrl, authState, authWindowWasClosed,
  activeTab, showCreateModal, profileFilter, discoverTabActive, settingsNavItem, accentColor, toggleStates,
  selectedInstance, runningInstancesCount, selectedVersion,
  playerName, playerSkinUrl, playerSkinFallback, playerAvatarUrl, playerAvatarFallback, filteredInstances,
  versionEmoji, versionGradient, formatRelativeDate, formatVersionType, formatReleaseTime,
  handleLogin, handleLogout, handleCreateInstance, handleLaunch, handleStop,
  loadInstances, loadVersions, setAccentColor, handleImgError
} = useLauncher();
</script>
<template>
<header class="topbar">
    <div class="brand">
      <div class="wc"><div class="w wr"></div><div class="w wy"></div><div class="w wg"></div></div>
      <div class="brand-logo">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="white"><path d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
      </div>
      <div class="brand-name">THE<b>LAUNCHER</b></div>
    </div>
    <nav class="top-nav">
      <button class="tnav" :class="{ on: activeTab === 'home' }" type="button" @click="activeTab = 'home'">
        <Icon icon="lucide:play" class="w-[1em] h-[1em]" />PLAY
      </button>
      <button class="tnav" :class="{ on: activeTab === 'profiles' }" type="button" @click="activeTab = 'profiles'">
        <Icon icon="lucide:layers" class="w-[1em] h-[1em]" />PROFILES
      </button>
      <button class="tnav" :class="{ on: activeTab === 'skins' }" type="button" @click="activeTab = 'skins'">
        <Icon icon="lucide:shirt" class="w-[1em] h-[1em]" />SKINS
      </button>
      <button class="tnav" :class="{ on: activeTab === 'discover' }" type="button" @click="activeTab = 'discover'">
        <Icon icon="lucide:search" class="w-[1em] h-[1em]" />DISCOVER
      </button>
      <button class="tnav" :class="{ on: activeTab === 'settings' }" type="button" @click="activeTab = 'settings'">
        <Icon icon="lucide:settings" class="w-[1em] h-[1em]" />SETTINGS
      </button>
    </nav>
    <div class="tb-space"></div>
    <div class="tb-right">
      <div class="notif-btn" :title="error ?? launcherMessage ?? ''" @click="error = null; launcherMessage = null;">
        <Icon icon="lucide:bell" class="w-[1em] h-[1em]" />
        <div v-if="error || launcherMessage" class="notif-dot" :style="error ? 'background:#ff5f57;box-shadow:0 0 5px #ff5f57' : ''"></div>
      </div>
      <div class="user-pill" @click="authData ? handleLogout() : handleLogin()">
        <img :src="playerAvatarUrl" :data-fallback-src="playerAvatarFallback" alt="" @error="handleImgError" />
        <span class="un">{{ authData ? authData.username.toUpperCase() : (isAuthenticating ? 'WARTEN…' : 'LOGIN') }}</span>
        <Icon icon="lucide:chevron-down" class="w-[1em] h-[1em]" />
      </div>
    </div>
  </header>
</template>
