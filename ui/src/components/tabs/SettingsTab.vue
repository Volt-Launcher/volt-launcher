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
<div class="view settings-view" :class="{ on: activeTab === 'settings' }">
      <div class="settings-inner">
        <div class="settings-nav fi fi1">
          <div class="snav-lbl">EINSTELLUNGEN</div>
          <div v-for="item in [{id:'Allgemein',icon:'sun'},{id:'Darstellung',icon:'monitor'},{id:'Java & Speicher',icon:'layers'},{id:'Updates',icon:'upload'},{id:'Account',icon:'user'},{id:'Erweitert',icon:'adv'}]" :key="item.id"
            class="snav-item" :class="{ on: settingsNavItem === item.id }" @click="settingsNavItem = item.id">
            <svg v-if="item.icon==='sun'" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3" /><path d="M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83" /></svg>
            <Icon v-else-if="item.icon==='monitor'" icon="lucide:monitor" class="w-[1em] h-[1em]" />
            <Icon v-else-if="item.icon==='layers'" icon="lucide:layers" class="w-[1em] h-[1em]" />
            <Icon v-else-if="item.icon==='upload'" icon="lucide:download" class="w-[1em] h-[1em]" />
            <svg v-else-if="item.icon==='user'" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /></svg>
            <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="2" /><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" /></svg>
            {{ item.id }}
          </div>
          <div style="margin-top:auto">
            <button class="act-btn act-prim" style="width:100%;justify-content:center" type="button"><Icon icon="lucide:folder-open" class="w-[1em] h-[1em]" />Verzeichnis</button>
          </div>
        </div>
        <div class="settings-content fi fi2">
          <!-- Account page -->
          <div v-if="settingsNavItem === 'Account'" class="sbox">
            <div class="sbox-title">ACCOUNT</div>
            <div class="sbox-desc">Microsoft-Session verwalten</div>
            <div v-if="authData" style="display:flex;flex-direction:column;gap:10px">
              <div style="display:flex;justify-content:space-between;align-items:center;padding:12px;background:rgba(5,13,26,.6);border:1px solid rgba(255,255,255,.07);border-radius:10px">
                <div><div style="font-size:13px;font-weight:600;margin-bottom:4px">{{ authData.username }}</div><div style="font-size:10px;color:var(--text-faint);font-family:monospace">{{ authData.uuid }}</div></div>
                <button class="act-btn act-ghost" type="button" @click="handleLogout">Abmelden</button>
              </div>
            </div>
            <div v-else style="display:flex;flex-direction:column;gap:12px">
              <div style="font-size:12px;color:var(--text-muted);line-height:1.6">Mit Microsoft anmelden um Minecraft starten zu können.</div>
              <button class="act-btn act-prim" type="button" @click="handleLogin" :disabled="isAuthenticating" style="align-self:flex-start">{{ isAuthenticating ? 'Warten…' : 'Mit Microsoft anmelden' }}</button>
              <div v-if="isAuthenticating" style="font-size:11px;color:var(--text-muted)">Login im Popup-Fenster abschließen.<a v-if="authUrl" :href="authUrl" target="_blank" rel="noopener" style="color:var(--primary);text-decoration:underline;display:block;margin-top:4px;word-break:break-all">{{ authUrl }}</a></div>
            </div>
          </div>
          <!-- Default pages -->
          <div v-else>
            <div class="sbox" style="margin-bottom:12px">
              <div class="sbox-title"><Icon icon="lucide:palette" class="w-[1em] h-[1em]" />AKZENTFARBE</div>
              <div class="sbox-desc">Wähle deine bevorzugte Akzentfarbe für den Launcher</div>
              <div style="display:flex;gap:7px;flex-wrap:wrap">
                <div v-for="c in ['#00b2ff','#6c63ff','#8b5cf6','#ec4899','#10b981','#00ffcc','#f59e0b','#ef4444','#f97316','#64748b']" :key="c" class="sw" :class="{ on: accentColor === c }" :style="`background:${c}`" @click="setAccentColor(c)"></div>
              </div>
            </div>
            <div class="sbox" style="margin-bottom:12px">
              <div class="sbox-title">OPTIONEN</div>
              <div class="toggles">
                <div class="trow" v-for="t in [{key:'autoUpdate',name:'Auto Updates',sub:'Updates automatisch laden'},{key:'discordPresence',name:'Discord Presence',sub:'Status in Discord zeigen'},{key:'betaUpdates',name:'Beta Updates',sub:'Pre-Release Builds'},{key:'openLogs',name:'Logs öffnen',sub:'Nach Spielstart anzeigen'},{key:'hwAccel',name:'Hardware-Beschl.',sub:'GPU-Beschleunigung'},{key:'hideLauncher',name:'Launcher verstecken',sub:'Beim Spielstart'}]" :key="t.key">
                  <div><div class="tname">{{ t.name }}</div><div class="tsub">{{ t.sub }}</div></div>
                  <button class="toggle" :class="toggleStates[t.key as keyof typeof toggleStates] ? 'on' : 'off'" type="button" :aria-pressed="toggleStates[t.key as keyof typeof toggleStates]" @click="(toggleStates[t.key as keyof typeof toggleStates] as boolean) = !toggleStates[t.key as keyof typeof toggleStates]"></button>
                </div>
              </div>
            </div>
            <div class="sbox">
              <div class="sbox-title">PERFORMANCE</div>
              <div class="sbox-desc">Downloads und I/O-Operationen konfigurieren</div>
              <div class="slider-grid">
                <div><div class="slider-head"><span class="slider-label">Simultane Downloads</span><span class="slider-value">5</span></div><div class="slider-track"><div class="slider-fill" style="width:40%"></div><div class="slider-thumb" style="left:38%"></div></div><div class="slider-scale"><span>1</span><span>5</span><span>10</span></div></div>
                <div><div class="slider-head"><span class="slider-label">Concurrent I/O</span><span class="slider-value">10</span></div><div class="slider-track"><div class="slider-fill" style="width:45%"></div><div class="slider-thumb" style="left:43%"></div></div><div class="slider-scale"><span>1</span><span>10</span><span>20</span></div></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>
