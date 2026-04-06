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
<div class="view home" :class="{ on: activeTab === 'home' }">
      <div class="home-main">
        <div class="floor">
          <svg viewBox="0 0 800 240" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
            <defs><linearGradient id="fg" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="rgba(0,178,255,0.2)" /><stop offset="100%" stop-color="rgba(0,178,255,0)" /></linearGradient></defs>
            <line x1="0" y1="20" x2="800" y2="20" stroke="url(#fg)" stroke-width=".6" /><line x1="0" y1="60" x2="800" y2="60" stroke="url(#fg)" stroke-width=".5" />
            <line x1="0" y1="110" x2="800" y2="110" stroke="url(#fg)" stroke-width=".5" /><line x1="0" y1="165" x2="800" y2="165" stroke="url(#fg)" stroke-width=".5" />
            <line x1="0" y1="220" x2="800" y2="220" stroke="url(#fg)" stroke-width=".5" /><line x1="400" y1="0" x2="400" y2="240" stroke="rgba(0,178,255,0.12)" stroke-width=".6" />
            <line x1="400" y1="0" x2="0" y2="240" stroke="rgba(0,178,255,0.05)" stroke-width=".5" /><line x1="400" y1="0" x2="800" y2="240" stroke="rgba(0,178,255,0.05)" stroke-width=".5" />
            <line x1="400" y1="0" x2="140" y2="240" stroke="rgba(0,178,255,0.06)" stroke-width=".5" /><line x1="400" y1="0" x2="660" y2="240" stroke="rgba(0,178,255,0.06)" stroke-width=".5" />
            <line x1="400" y1="0" x2="275" y2="240" stroke="rgba(0,178,255,0.07)" stroke-width=".5" /><line x1="400" y1="0" x2="525" y2="240" stroke="rgba(0,178,255,0.07)" stroke-width=".5" />
          </svg>
        </div>
        <div class="floor-fade"></div>
        <div class="player-area fi fi1">
          <div class="pname">{{ authData ? authData.username.toUpperCase() : 'SPIELER' }}</div>
          <div class="skin-wrap">
            <div class="skin-halo"></div>
            <img class="skin" :src="playerSkinUrl" :data-fallback-src="playerSkinFallback" alt="Skin" @error="handleImgError" @click="activeTab = 'skins'" />
          </div>
          <div class="srv-label">{{ selectedInstance?.running ? `▶ ${selectedInstance.name}` : 'HUGOSMP.NET' }}</div>
        </div>
        <div class="stat-strip fi fi2">
          <div class="sc">
            <div class="sc-v">{{ instances.length }}</div>
            <div class="sc-l">PROFILE</div>
          </div>
          <div class="sc">
            <div class="sc-v" :style="runningInstancesCount > 0 ? 'color:var(--accent)' : ''">{{ runningInstancesCount }}</div>
            <div class="sc-l">AKTIV</div>
          </div>
        </div>
        <div class="lbar fi fi3">
          <div class="lbar-info">
            <div class="lbar-name">{{ selectedInstance ? selectedInstance.name : (authData ? 'Kein Profil ausgewählt' : 'Bitte anmelden') }}</div>
            <div class="lbar-meta">
              <span v-if="selectedInstance"><Icon icon="lucide:layers" class="w-[1em] h-[1em]" />{{ selectedInstance.versionId }}</span>
              <span v-if="selectedInstance"><Icon icon="lucide:cpu" class="w-[1em] h-[1em]" />Java {{ selectedInstance.javaMajorVersion }}</span>
              <span v-if="selectedInstance"><Icon icon="lucide:clock" class="w-[1em] h-[1em]" />{{ formatRelativeDate(selectedInstance.lastPlayedAt) }}</span>
              <span v-if="!selectedInstance && !authData" style="color:var(--text-faint)">Microsoft-Login erforderlich</span>
              <span v-if="!selectedInstance && authData" style="color:var(--text-faint)">Profil im PROFILES-Tab wählen</span>
            </div>
          </div>
          <button v-if="selectedInstance?.running" class="launch-btn" @click="handleStop" :disabled="isLaunching">
            <Icon icon="lucide:square" class="w-[1em] h-[1em]" />{{ isLaunching ? 'STOPPE…' : 'STOPP' }}
          </button>
          <button v-else-if="authData && selectedInstance" class="launch-btn" @click="handleLaunch" :disabled="isLaunching">
            <Icon icon="lucide:play" class="w-[1em] h-[1em]" />{{ isLaunching ? 'STARTET…' : 'LAUNCH' }}
          </button>
          <button v-else-if="!authData" class="launch-btn" @click="handleLogin" :disabled="isAuthenticating">
            <Icon icon="lucide:log-in" class="w-[1em] h-[1em]" />{{ isAuthenticating ? 'WARTEN…' : 'LOGIN' }}
          </button>
          <button v-else class="launch-btn" style="background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.12);box-shadow:none" @click="activeTab = 'profiles'">
            <Icon icon="lucide:layers" class="w-[1em] h-[1em]" />PROFIL WÄHLEN
          </button>
          <div class="lgear" @click="activeTab = 'settings'">
            <Icon icon="lucide:settings" class="w-[1em] h-[1em]" />
          </div>
        </div>
      </div>
      <!-- News Side -->
      <div class="news-side">
        <div class="ns-hdr">
          <Icon icon="lucide:newspaper" class="w-[1em] h-[1em]" />
          <h3>NEWS &amp; UPDATES</h3>
        </div>
        <div class="news-scroll">
          <div class="nc fi fi1"><div class="nc-thumb"><div class="nc-img" style="background:linear-gradient(135deg,#04213d,#081c32)">🚀</div><div class="nc-fade"></div><span class="nc-badge nb-u">UPDATE</span></div><div class="nc-body"><h4>Launcher v1.0.0 Release</h4><p>Neues Top-Nav-Design, Discover-Tab und Status Bar jetzt live.</p><div class="nc-date">05. April 2026</div></div></div>
          <div class="nc fi fi2"><div class="nc-thumb"><div class="nc-img" style="background:linear-gradient(135deg,#180535,#2a1045)">✨</div><div class="nc-fade"></div><span class="nc-badge nb-s">SHOP</span></div><div class="nc-body"><h4>Oster Collection 2026</h4><p>Animierte Cloaks und Easter Skins – nur für kurze Zeit!</p><div class="nc-date">03. April 2026</div></div></div>
          <div class="nc fi fi3"><div class="nc-thumb"><div class="nc-img" style="background:linear-gradient(135deg,#062512,#0d3a1a)">🎉</div><div class="nc-fade"></div><span class="nc-badge nb-e">EVENT</span></div><div class="nc-body"><h4>Heaven Collection Drop</h4><p>Limitierte Belohnungen bis Ende April.</p><div class="nc-date">01. April 2026</div></div></div>
          <div class="nc fi fi4"><div class="nc-thumb"><div class="nc-img" style="background:linear-gradient(135deg,#251508,#3d2510)">🔧</div><div class="nc-fade"></div><span class="nc-badge nb-p">PATCH</span></div><div class="nc-body"><h4>Hotfix 1.0.1</h4><p>Startup-Crash auf Windows 11 behoben.</p><div class="nc-date">28. März 2026</div></div></div>
          <div class="nc fi fi5"><div class="nc-thumb"><div class="nc-img" style="background:linear-gradient(135deg,#001535,#002148)">🎆</div><div class="nc-fade"></div><span class="nc-badge nb-e">EVENT</span></div><div class="nc-body"><h4>Happy New Year – Cosmetics</h4><p>Silvester-Specials dauerhaft im Shop.</p><div class="nc-date">01. Jan. 2026</div></div></div>
        </div>
      </div>
    </div>
</template>
