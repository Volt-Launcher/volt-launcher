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
<div class="view discover-view" :class="{ on: activeTab === 'discover' }">
      <div class="disc-nav">
        <div v-for="dt in ['MODPACKS','MODS','RESOURCEPACKS','SHADERS','DATAPACKS']" :key="dt" class="disc-tab" :class="{ on: discoverTabActive === dt }" @click="discoverTabActive = dt">{{ dt }}</div>
        <div class="disc-search"><Icon icon="lucide:search" class="w-[1em] h-[1em]" /><input type="text" placeholder="Modpacks, Mods, Shaders suchen…" /></div>
      </div>
      <div class="disc-content">
        <div class="hero-banner fi fi1">
          <div class="hero-bg" style="background:linear-gradient(135deg,#0d3a6e,#1a5a4a,#2d4a1a)"></div>
          <div class="hero-ov"></div>
          <div class="hero-cnt">
            <div class="hero-tag">✦ FEATURED MODPACK</div>
            <div class="hero-title">A Cozy Mizuno World</div>
            <div class="hero-desc">Build your dream cottage with this cozy Fabric modpack, featuring the beloved Mizuno resource packs.</div>
            <div class="hero-acts"><div class="hbtn-p">Installieren</div><div class="hbtn-s">Details</div></div>
          </div>
          <div class="hero-dots"><div class="hd on"></div><div class="hd"></div><div class="hd"></div><div class="hd"></div><div class="hd"></div></div>
        </div>
        <div class="disc-sec fi fi2">
          <div class="disc-sec-hdr"><div class="disc-sec-title">Monthly Theme — Fantasy</div><div class="see-all">Alle anzeigen →</div></div>
          <div class="mpack-grid">
            <div class="mpack" v-for="p in [{e:'🏔️',n:'Dark Fantasy: Nordic',b:'bulochka_s_tiltom',d:'15.9K',bg:'linear-gradient(135deg,#1a0a30,#2d1540)'},{e:'🏰',n:'Dungeons Enhanced',b:'Valarauko9',d:'30.3M',bg:'linear-gradient(135deg,#1a0830,#361050)'},{e:'🔮',n:'Mastering Magic',b:'Lupin',d:'86.7K',bg:'linear-gradient(135deg,#200a3a,#380c5c)'},{e:'⚔️',n:'Legendary Arsenal',b:'Alend_Linker',d:'4.0K',bg:'linear-gradient(135deg,#0a2010,#143020)'},{e:'🧟',n:'Mutant More',b:'alexandersfunandg',d:'1.8M',bg:'linear-gradient(135deg,#3a1a05,#5a2a08)'},{e:'🌌',n:'Cobblemon Official',b:'CobbledStudios',d:'6.7M',bg:'linear-gradient(135deg,#0a1535,#122050)'}]" :key="p.n">
              <div class="mpack-img" :style="p.bg"><div class="mpack-img-bg" style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:34px">{{ p.e }}</div><div class="mpack-img-fade"></div></div>
              <div class="mpack-body"><div class="mpack-name">{{ p.n }}</div><div class="mpack-by">by {{ p.b }}</div><div class="mpack-dl"><Icon icon="lucide:download" class="w-[1em] h-[1em]" />{{ p.d }}</div></div>
            </div>
          </div>
        </div>
        <div class="disc-sec fi fi3">
          <div class="disc-sec-hdr"><div class="disc-sec-title">Beliebte Kategorien</div><div class="see-all">Alle anzeigen →</div></div>
          <div class="cat-grid">
            <div class="cat-card" v-for="c in [{e:'📦',n:'Modpacks'},{e:'🔧',n:'Mods'},{e:'📁',n:'Data Packs'},{e:'🎨',n:'Resource Packs'},{e:'✨',n:'Shader Packs'},{e:'🌍',n:'Welten'}]" :key="c.n">
              <div class="cat-icon">{{ c.e }}</div><div class="cat-name">{{ c.n }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>
