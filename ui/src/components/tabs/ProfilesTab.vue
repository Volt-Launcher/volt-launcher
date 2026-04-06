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
<div class="view profiles-view" :class="{ on: activeTab === 'profiles' }">
      <div class="ptool">
        <div class="ptool-row">
          <div class="ftabs">
            <div v-for="f in ['ALL','RELEASE','SNAPSHOT','BETA','ALPHA']" :key="f" class="ft" :class="{ on: profileFilter === f }" @click="profileFilter = f">{{ f === 'ALL' ? 'ALLE' : f }}</div>
          </div>
          <div class="flex1"></div>
          <button class="act-btn act-ghost" type="button"><Icon icon="lucide:download" class="w-[1em] h-[1em]" />IMPORT</button>
          <button class="act-btn act-prim" type="button" @click="showCreateModal = true"><Icon icon="lucide:plus" class="w-[1em] h-[1em]" />NEUES PROFIL</button>
        </div>
      </div>
      <div class="pview-label" style="margin-top:14px">PROFILE ({{ filteredInstances.length }})</div>
      <div class="profiles-grid">
        <div class="pcard-add fi fi1" @click="showCreateModal = true">
          <div class="add-icon"><Icon icon="lucide:plus" class="w-[1em] h-[1em]" /></div>
          <div style="text-align:center"><div style="font-size:12px;font-weight:600">Neues Profil</div><div style="font-size:10px;color:rgba(255,255,255,.28)">Erstellen oder importieren</div></div>
        </div>
        <div v-for="(inst, idx) in filteredInstances" :key="inst.slug" class="pcard" :class="`fi fi${Math.min(idx+2,6)}`" @click="selectedInstanceName = inst.name; activeTab = 'home'">
          <div class="pcard-cover">
            <div class="pcard-cover-bg" :style="`${versionGradient(inst.versionType)};position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:40px;opacity:.7`">{{ versionEmoji(inst.versionType) }}</div>
            <div class="pcard-cover-fade"></div>
            <div class="pcard-overlay"><div class="pcard-play"><Icon icon="lucide:play" class="w-[1em] h-[1em]" />PLAY</div></div>
            <div class="pcard-badges">
              <span v-if="inst.running" class="pbadge" style="background:rgba(0,255,204,.18);color:var(--accent);border:1px solid rgba(0,255,204,.3)">▶ AKTIV</span>
              <span v-if="selectedInstanceName === inst.name" class="pbadge pb-star">⭐</span>
            </div>
          </div>
          <div class="pcard-body">
            <div class="pcard-name">{{ inst.name }}</div>
            <div class="pcard-meta">
              <span class="pm"><div class="pm-dot" style="background:#4caf50"></div>{{ inst.versionId }}</span>
              <span class="pm"><div class="pm-dot" style="background:#6c63ff"></div>Java {{ inst.javaMajorVersion }}</span>
              <span class="pm" style="color:rgba(255,255,255,.22)">{{ formatRelativeDate(inst.lastPlayedAt) }}</span>
            </div>
          </div>
        </div>
        <div v-if="filteredInstances.length === 0 && !isLoadingInstances" style="grid-column:1/-1;text-align:center;padding:40px 0;color:var(--text-faint);font-size:13px">Keine Profile gefunden. Erstelle ein neues Profil mit dem Button oben.</div>
      </div>
    </div>
</template>
