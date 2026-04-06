<script setup lang="ts">
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
<div class="view" :class="{ on: activeTab === 'skins' }" style="flex:1;align-items:center;justify-content:center;flex-direction:column;gap:16px">
      <div style="font-size:48px">👤</div>
      <div style="font-size:18px;font-weight:700;letter-spacing:.12em">SKINS</div>
      <div style="font-size:12px;color:var(--text-faint);text-align:center;max-width:260px;line-height:1.6">Skin-Verwaltung wird in einer zukünftigen Version verfügbar sein.</div>
      <img :src="playerSkinUrl" :data-fallback-src="playerSkinFallback" alt="Skin" @error="handleImgError" style="height:180px;object-fit:contain;filter:drop-shadow(0 12px 32px rgba(0,178,255,.28));margin-top:8px" />
    </div>
</template>
