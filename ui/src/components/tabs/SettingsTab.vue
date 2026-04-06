<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  authData,
  isAuthenticating,
  authUrl,
  activeTab,
  settingsNavItem,
  accentColor,
  toggleStates,
  handleLogin,
  handleLogout,
  setAccentColor,
} = useLauncher();

const settingsItems = [
  { id: 'Allgemein', icon: 'lucide:sun' },
  { id: 'Darstellung', icon: 'lucide:monitor' },
  { id: 'Java & Speicher', icon: 'lucide:layers' },
  { id: 'Updates', icon: 'lucide:download' },
  { id: 'Account', icon: 'lucide:user' },
  { id: 'Erweitert', icon: 'lucide:settings-2' },
] as const;

const accentColors = [
  { value: '#00b2ff', class: 'bg-[#00b2ff]' },
  { value: '#6c63ff', class: 'bg-[#6c63ff]' },
  { value: '#8b5cf6', class: 'bg-[#8b5cf6]' },
  { value: '#ec4899', class: 'bg-[#ec4899]' },
  { value: '#10b981', class: 'bg-[#10b981]' },
  { value: '#00ffcc', class: 'bg-[#00ffcc]' },
  { value: '#f59e0b', class: 'bg-[#f59e0b]' },
  { value: '#ef4444', class: 'bg-[#ef4444]' },
  { value: '#f97316', class: 'bg-[#f97316]' },
  { value: '#64748b', class: 'bg-[#64748b]' },
] as const;
const toggleOptions = [
  { key:'autoUpdate',name:'Auto Updates',sub:'Updates automatisch laden' },
  { key:'discordPresence',name:'Discord Presence',sub:'Status in Discord zeigen' },
  { key:'betaUpdates',name:'Beta Updates',sub:'Pre-Release Builds' },
  { key:'openLogs',name:'Logs öffnen',sub:'Nach Spielstart anzeigen' },
  { key:'hwAccel',name:'Hardware-Beschl.',sub:'GPU-Beschleunigung' },
  { key:'hideLauncher',name:'Launcher verstecken',sub:'Beim Spielstart' },
] as const;

const performanceCards = [
  { label: 'Simultane Downloads', value: '5', fillClass: 'w-[40%]', thumbClass: 'left-[38%]', scale: ['1', '5', '10'] },
  { label: 'Concurrent I/O', value: '10', fillClass: 'w-[45%]', thumbClass: 'left-[43%]', scale: ['1', '10', '20'] },
] as const;
</script>
<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'settings' ? 'flex' : 'hidden'">
    <div class="flex flex-1 flex-col gap-5 overflow-y-auto p-4 md:flex-row md:p-6">
      <div class="flex w-full shrink-0 flex-col gap-[3px] md:w-[175px]">
        <div class="px-[13px] pt-1 pb-2 text-[10px] font-bold tracking-[0.14em] text-white/40">EINSTELLUNGEN</div>
        <button
          v-for="item in settingsItems"
          :key="item.id"
          type="button"
          class="flex items-center gap-2.5 rounded-lg border border-transparent px-[13px] py-[9px] text-left text-[12.5px] font-semibold tracking-[0.06em] text-white/60 transition-all duration-200 hover:bg-white/5 hover:text-white/95"
          :class="settingsNavItem === item.id ? 'border-[rgba(0,178,255,.2)] bg-[rgba(0,178,255,.1)] text-[var(--primary)]' : ''"
          @click="settingsNavItem = item.id"
        >
          <Icon :icon="item.icon" class="size-[13px]" />
          {{ item.id }}
        </button>
        <div class="mt-auto pt-3">
          <button type="button" class="inline-flex w-full items-center justify-center gap-1.5 rounded-[7px] border border-[rgba(0,178,255,.25)] bg-[rgba(0,178,255,.12)] px-3.5 py-[7px] text-[11.5px] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[rgba(0,178,255,.2)] hover:shadow-[0_0_12px_rgba(0,178,255,.2)]">
            <Icon icon="lucide:folder-open" class="size-[11px]" />Verzeichnis
          </button>
        </div>
      </div>

      <div class="flex flex-1 flex-col gap-3 overflow-y-auto">
        <div v-if="settingsNavItem === 'Account'" class="rounded-xl border border-white/10 bg-[rgba(8,18,34,.78)] p-[18px]">
          <div class="mb-1 text-[11px] font-bold tracking-[0.12em] text-white/60">ACCOUNT</div>
          <div class="mb-[14px] text-[12px] leading-[1.55] text-white/60">Microsoft-Session verwalten</div>
          <div v-if="authData" class="flex flex-col gap-2.5">
            <div class="flex items-center justify-between gap-4 rounded-[10px] border border-white/10 bg-[rgba(5,13,26,.6)] p-3">
              <div>
                <div class="mb-1 text-[13px] font-semibold text-white">{{ authData.username }}</div>
                <div class="font-mono text-[10px] text-white/40">{{ authData.uuid }}</div>
              </div>
              <button type="button" class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[11.5px] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10" @click="handleLogout">Abmelden</button>
            </div>
          </div>
          <div v-else class="flex flex-col gap-3">
            <div class="text-[12px] leading-[1.6] text-white/60">Mit Microsoft anmelden um Minecraft starten zu können.</div>
            <button type="button" class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-[rgba(0,178,255,.25)] bg-[rgba(0,178,255,.12)] px-3.5 py-[7px] text-[11.5px] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[rgba(0,178,255,.2)] hover:shadow-[0_0_12px_rgba(0,178,255,.2)] disabled:cursor-not-allowed disabled:opacity-50" :disabled="isAuthenticating" @click="handleLogin">
              {{ isAuthenticating ? 'Warten…' : 'Mit Microsoft anmelden' }}
            </button>
            <div v-if="isAuthenticating" class="text-[11px] text-white/60">
              Login im Popup-Fenster abschließen.
              <a v-if="authUrl" :href="authUrl" target="_blank" rel="noopener" class="mt-1 block break-all text-[var(--primary)] underline">{{ authUrl }}</a>
            </div>
          </div>
        </div>
        <div v-else class="flex flex-col gap-3">
          <div class="rounded-xl border border-white/10 bg-[rgba(8,18,34,.78)] p-[18px]">
            <div class="mb-1 flex items-center gap-[7px] text-[11px] font-bold tracking-[0.12em] text-white/60"><Icon icon="lucide:palette" class="size-[13px]" />AKZENTFARBE</div>
            <div class="mb-[14px] text-[12px] leading-[1.55] text-white/60">Wähle deine bevorzugte Akzentfarbe für den Launcher</div>
            <div class="flex flex-wrap gap-[7px]">
              <button
                v-for="c in accentColors"
                :key="c.value"
                type="button"
                class="inline-block h-[30px] w-[30px] rounded-[7px] border-2 border-white/20 transition-transform duration-200 hover:scale-110"
                :class="[c.class, accentColor === c.value ? '!border-white shadow-[0_0_10px_rgba(0,178,255,.4)]' : '']"
                @click="setAccentColor(c.value)"
              ></button>
            </div>
          </div>

          <div class="rounded-xl border border-white/10 bg-[rgba(8,18,34,.78)] p-[18px]">
            <div class="mb-1 text-[11px] font-bold tracking-[0.12em] text-white/60">OPTIONEN</div>
            <div class="grid grid-cols-1 gap-[9px] md:grid-cols-2">
              <div v-for="t in toggleOptions" :key="t.key" class="flex items-center justify-between rounded-[10px] border border-white/10 bg-[rgba(5,13,26,.62)] px-[15px] py-[13px]">
                <div>
                  <div class="mb-[3px] text-[12.5px] font-semibold text-white">{{ t.name }}</div>
                  <div class="text-[10.5px] leading-[1.45] text-white/60">{{ t.sub }}</div>
                </div>
                <button
                  type="button"
                  class="relative h-[22px] w-[40px] shrink-0 rounded-full transition-colors duration-300"
                  :class="toggleStates[t.key as keyof typeof toggleStates] ? 'bg-[var(--primary)]' : 'bg-white/10'"
                  :aria-pressed="toggleStates[t.key as keyof typeof toggleStates]"
                  @click="(toggleStates[t.key as keyof typeof toggleStates] as boolean) = !toggleStates[t.key as keyof typeof toggleStates]"
                >
                  <span
                    class="absolute top-[3px] h-4 w-4 rounded-full transition-all duration-300"
                    :class="toggleStates[t.key as keyof typeof toggleStates]
                      ? 'left-[21px] bg-white'
                      : 'left-[3px] bg-white/40'"
                  ></span>
                </button>
              </div>
            </div>
          </div>

          <div class="rounded-xl border border-white/10 bg-[rgba(8,18,34,.78)] p-[18px]">
            <div class="mb-1 text-[11px] font-bold tracking-[0.12em] text-white/60">PERFORMANCE</div>
            <div class="mb-[14px] text-[12px] leading-[1.55] text-white/60">Downloads und I/O-Operationen konfigurieren</div>
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div v-for="card in performanceCards" :key="card.label">
                <div class="mb-2 flex justify-between"><span class="text-[11px] font-semibold text-white">{{ card.label }}</span><span class="text-[11px] font-bold text-[var(--primary)]">{{ card.value }}</span></div>
                <div class="relative h-1 rounded-sm bg-white/10">
                  <div class="absolute left-0 top-0 h-full rounded-sm bg-gradient-to-r from-[var(--primary)] to-[var(--secondary)]" :class="card.fillClass"></div>
                  <div class="absolute top-1/2 h-[13px] w-[13px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--primary)] shadow-[0_0_8px_rgba(0,178,255,.5)]" :class="card.thumbClass"></div>
                </div>
                <div class="mt-[5px] flex justify-between text-[9px] text-white/20"><span>{{ card.scale[0] }}</span><span>{{ card.scale[1] }}</span><span>{{ card.scale[2] }}</span></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

