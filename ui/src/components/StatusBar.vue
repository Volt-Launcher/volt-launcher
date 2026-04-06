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
<footer class="statusbar">
    <div class="sbi" :class="{ live: !error }">
      <div class="sdot" :class="error ? '' : 'g'" :style="error ? 'background:#ff5f57;box-shadow:0 0 5px rgba(255,95,87,.5)' : ''"></div>
      {{ error ? 'FEHLER' : 'ONLINE' }}
    </div>
    <div class="sbi"><Icon icon="lucide:monitor" class="w-[1em] h-[1em]" />{{ runningInstancesCount > 0 ? `${runningInstancesCount} Spiel${runningInstancesCount > 1 ? 'e' : ''} läuft` : 'Kein Spiel läuft' }}</div>
    <div class="sbi" v-if="selectedInstance"><Icon icon="lucide:layers" class="w-[1em] h-[1em]" />Java {{ selectedInstance.javaMajorVersion }}</div>
    <div v-if="error" class="sbi" style="color:rgba(255,150,150,.8);cursor:pointer;max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" @click="error = null" :title="error">⚠ {{ error }}</div>
    <div v-else-if="launcherMessage" class="sbi" style="color:rgba(0,255,204,.7);cursor:pointer;max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" @click="launcherMessage = null" :title="launcherMessage">✓ {{ launcherMessage }}</div>
    <div class="sb-sp"></div>
    <div class="sb-acts">
      <div class="sb-act" @click="activeTab = 'settings'"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /></svg>Verzeichnis öffnen</div>
      <div class="sb-act" @click="void loadInstances()"><Icon icon="lucide:download" class="w-[1em] h-[1em]" />Updates prüfen</div>
    </div>
    <div class="sb-ver">v1.0.0-beta</div>
  </footer>
</template>
