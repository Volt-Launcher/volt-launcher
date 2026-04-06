<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from '@/composables/useLauncher';
const {
  activeTab,
  discoverTabActive,
} = useLauncher();

const discoverTabs = ['MODPACKS', 'MODS', 'RESOURCEPACKS', 'SHADERS', 'DATAPACKS'];
const packs = [
  { e:'🏔️',n:'Dark Fantasy: Nordic',b:'bulochka_s_tiltom',d:'15.9K',bgClass:'bg-[linear-gradient(135deg,#1a0a30,#2d1540)]' },
  { e:'🏰',n:'Dungeons Enhanced',b:'Valarauko9',d:'30.3M',bgClass:'bg-[linear-gradient(135deg,#1a0830,#361050)]' },
  { e:'🔮',n:'Mastering Magic',b:'Lupin',d:'86.7K',bgClass:'bg-[linear-gradient(135deg,#200a3a,#380c5c)]' },
  { e:'⚔️',n:'Legendary Arsenal',b:'Alend_Linker',d:'4.0K',bgClass:'bg-[linear-gradient(135deg,#0a2010,#143020)]' },
  { e:'🧟',n:'Mutant More',b:'alexandersfunandg',d:'1.8M',bgClass:'bg-[linear-gradient(135deg,#3a1a05,#5a2a08)]' },
  { e:'🌌',n:'Cobblemon Official',b:'CobbledStudios',d:'6.7M',bgClass:'bg-[linear-gradient(135deg,#0a1535,#122050)]' },
];
const categories = [
  { e:'📦',n:'Modpacks' },
  { e:'🔧',n:'Mods' },
  { e:'📁',n:'Data Packs' },
  { e:'🎨',n:'Resource Packs' },
  { e:'✨',n:'Shader Packs' },
  { e:'🌍',n:'Welten' },
];
</script>
<template>
  <div class="flex-1 flex-col overflow-hidden" :class="activeTab === 'discover' ? 'flex' : 'hidden'">
    <div class="flex shrink-0 flex-wrap items-center gap-2 px-4 pt-4 md:px-6">
      <button
        v-for="dt in discoverTabs"
        :key="dt"
        type="button"
        class="rounded-md border px-3.5 py-1.5 text-[11.5px] font-semibold tracking-[0.07em] transition-all duration-200"
        :class="discoverTabActive === dt
          ? 'border-[rgba(0,178,255,.28)] bg-[rgba(0,178,255,.12)] text-[var(--primary)]'
          : 'border-white/10 bg-white/[0.03] text-white/60 hover:bg-white/[0.07] hover:text-white/70'"
        @click="discoverTabActive = dt"
      >
        {{ dt }}
      </button>
      <div class="ml-auto flex w-full max-w-[400px] items-center gap-2 rounded-lg border border-white/10 bg-[rgba(8,18,34,.78)] px-3 py-[7px] md:w-auto">
        <Icon icon="lucide:search" class="size-[13px] text-white/30" />
        <input type="text" placeholder="Modpacks, Mods, Shaders suchen…" class="w-full bg-transparent text-[13px] text-white outline-none placeholder:text-white/40" />
      </div>
    </div>
    <div class="flex-1 overflow-y-auto px-4 py-4 pb-6 md:px-6">
      <div class="relative mb-6 h-[180px] overflow-hidden rounded-[14px] border border-white/10 transition-all duration-300 hover:border-[rgba(0,178,255,.25)] hover:shadow-[0_0_40px_rgba(0,178,255,.12)]">
        <div class="absolute inset-0 bg-[linear-gradient(135deg,#0d3a6e,#1a5a4a,#2d4a1a)] transition-transform duration-500 hover:scale-[1.03]"></div>
        <div class="absolute inset-0 bg-[linear-gradient(to_right,rgba(3,9,18,.92)_40%,transparent_80%)]"></div>
        <div class="absolute inset-0 flex flex-col justify-end px-8 py-7">
          <div class="mb-1.5 text-[10px] font-bold tracking-[0.12em] text-[var(--primary)]">✦ FEATURED MODPACK</div>
          <div class="mb-1.5 text-[24px] font-bold leading-[1.2] text-white">A Cozy Mizuno World</div>
          <div class="mb-3.5 max-w-[440px] text-[12px] leading-[1.6] text-white/85">Build your dream cottage with this cozy Fabric modpack, featuring the beloved Mizuno resource packs.</div>
          <div class="flex gap-2">
            <button type="button" class="rounded-[7px] bg-[var(--primary)] px-5 py-[9px] text-[12px] font-bold tracking-[0.08em] text-white">Installieren</button>
            <button type="button" class="rounded-[7px] border border-white/15 bg-white/10 px-5 py-[9px] text-[12px] font-bold tracking-[0.08em] text-white/80">Details</button>
          </div>
        </div>
        <div class="absolute bottom-[14px] right-[18px] flex gap-[5px]">
          <div class="h-1.5 w-[18px] rounded bg-[var(--primary)]"></div>
          <div v-for="n in 4" :key="n" class="size-1.5 rounded-full bg-white/25"></div>
        </div>
      </div>

      <div class="mb-7">
        <div class="mb-3 flex items-center justify-between gap-3">
          <div class="text-[14px] font-bold tracking-[0.06em] text-white">Monthly Theme — Fantasy</div>
          <button type="button" class="text-[11.5px] font-semibold text-[var(--primary)] hover:underline">Alle anzeigen →</button>
        </div>
        <div class="grid grid-cols-[repeat(auto-fill,minmax(152px,1fr))] gap-2.5">
          <div v-for="p in packs" :key="p.n" class="overflow-hidden rounded-[10px] border border-white/10 bg-[rgba(8,18,34,.78)] transition-all duration-200 hover:-translate-y-1 hover:border-[rgba(0,178,255,.25)] hover:shadow-[0_8px_24px_rgba(0,0,0,.3)]">
            <div class="relative flex h-[95px] items-center justify-center overflow-hidden" :class="p.bgClass">
              <div class="absolute inset-0 flex items-center justify-center text-[34px]">{{ p.e }}</div>
              <div class="absolute inset-0 bg-[linear-gradient(to_bottom,transparent_50%,rgba(8,18,34,1))]"></div>
            </div>
            <div class="px-2.5 py-2">
              <div class="truncate text-[12.5px] font-semibold text-white">{{ p.n }}</div>
              <div class="mb-1.5 text-[10.5px] text-white/60">by {{ p.b }}</div>
              <div class="inline-flex items-center gap-1 text-[10px] text-white/40"><Icon icon="lucide:download" class="size-[9px]" />{{ p.d }}</div>
            </div>
          </div>
        </div>
      </div>

      <div>
        <div class="mb-3 flex items-center justify-between gap-3">
          <div class="text-[14px] font-bold tracking-[0.06em] text-white">Beliebte Kategorien</div>
          <button type="button" class="text-[11.5px] font-semibold text-[var(--primary)] hover:underline">Alle anzeigen →</button>
        </div>
        <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
          <div v-for="c in categories" :key="c.n" class="rounded-[10px] border border-white/10 bg-[rgba(8,18,34,.78)] px-4 py-[18px] text-center transition-all duration-200 hover:border-[rgba(0,178,255,.25)] hover:bg-[rgba(0,178,255,.06)]">
            <div class="mb-1.5 text-[26px]">{{ c.e }}</div>
            <div class="text-[12.5px] font-semibold text-white">{{ c.n }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
