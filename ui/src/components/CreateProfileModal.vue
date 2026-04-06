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
<Teleport to="body">
    <div v-if="showCreateModal" class="modal-overlay" @click.self="showCreateModal = false">
      <div class="modal-box fi fi1">
        <div class="modal-header">
          <div style="font-size:14px;font-weight:700;letter-spacing:.1em">NEUES PROFIL ERSTELLEN</div>
          <button class="modal-close" type="button" @click="showCreateModal = false"><Icon icon="lucide:x" class="w-[1em] h-[1em]" /></button>
        </div>
        <div class="modal-body">
          <div class="modal-field">
            <label class="modal-label">PROFILNAME</label>
            <input v-model="newInstanceName" type="text" placeholder="Mein Survival World" class="modal-input" @keyup.enter="handleCreateInstance" />
          </div>
          <div class="modal-field">
            <div class="modal-label">VERSIONSFILTER</div>
            <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:6px">
              <label class="modal-toggle-pill"><input v-model="includeSnapshots" type="checkbox" /><span>Snapshots</span></label>
              <label class="modal-toggle-pill"><input v-model="includeBetas" type="checkbox" /><span>Betas</span></label>
              <label class="modal-toggle-pill"><input v-model="includeAlphas" type="checkbox" /><span>Alphas</span></label>
            </div>
          </div>
          <div class="modal-field">
            <label class="modal-label">MINECRAFT VERSION</label>
            <select v-model="selectedVersionId" class="modal-input" :disabled="isLoadingVersions || !availableVersions.length">
              <option disabled value="">Version wählen</option>
              <option v-for="v in availableVersions" :key="v.id" :value="v.id">{{ v.id }} — {{ formatVersionType(v.type) }} — {{ formatReleaseTime(v.releaseTime) }}</option>
            </select>
            <div style="font-size:10px;color:var(--text-faint);margin-top:6px">{{ isLoadingVersions ? 'Lade Versionen…' : `${availableVersions.length} Versionen verfügbar` }}</div>
            <div v-if="selectedVersion" style="margin-top:8px;padding:10px;background:rgba(0,178,255,.06);border:1px solid rgba(0,178,255,.15);border-radius:8px;font-size:11px;color:var(--text-muted)">
              <strong style="color:var(--primary)">{{ selectedVersion.id }}</strong> · {{ formatVersionType(selectedVersion.type) }} · {{ formatReleaseTime(selectedVersion.releaseTime) }}
            </div>
          </div>
          <div v-if="error" style="padding:10px 12px;background:rgba(255,95,87,.1);border:1px solid rgba(255,95,87,.25);border-radius:8px;font-size:12px;color:#ff9898">{{ error }}</div>
          <div style="display:flex;gap:8px;justify-content:flex-end;margin-top:4px">
            <button class="act-btn act-ghost" type="button" @click="showCreateModal = false">Abbrechen</button>
            <button class="act-btn act-prim" type="button" @click="handleCreateInstance" :disabled="isCreatingInstance || !selectedVersionId || !newInstanceName.trim()">{{ isCreatingInstance ? 'Erstelle…' : 'Profil erstellen' }}</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
