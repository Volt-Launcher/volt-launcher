<script setup lang="ts">
import {Icon} from "@iconify/vue";
import {useLauncher} from "@/composables/useLauncher";

const {
  authData,
  instances,
  isAuthenticating,
  isLaunching,
  activeTab,
  selectedInstance,
  runningInstancesCount,
  playerSkinUrl,
  playerSkinFallback,
  formatRelativeDate,
  handleLogin,
  handleLaunch,
  handleStop,
  handleImgError,
} = useLauncher();

const newsItems = [
  {
    emoji: "🚀",
    title: "Launcher v1.0.0 Release",
    description: "Neues Top-Nav-Design, Discover-Tab und Status Bar jetzt live.",
    date: "05. April 2026",
    badge: "UPDATE",
    badgeClasses:
        "border border-cyan-500/30 bg-cyan-500/20 text-cyan-400",
    backgroundClass: "bg-slate-900",
  },
  {
    emoji: "✨",
    title: "Oster Collection 2026",
    description: "Animierte Cloaks und Easter Skins – nur für kurze Zeit!",
    date: "03. April 2026",
    badge: "SHOP",
    badgeClasses:
        "border border-violet-500/30 bg-violet-500/20 text-violet-400",
    backgroundClass: "bg-violet-950",
  },
  {
    emoji: "🎉",
    title: "Heaven Collection Drop",
    description: "Limitierte Belohnungen bis Ende April.",
    date: "01. April 2026",
    badge: "EVENT",
    badgeClasses:
        "border border-emerald-500/30 bg-emerald-500/20 text-emerald-400",
    backgroundClass: "bg-emerald-950",
  },
] as const;
</script>

<template>
  <div
      class="flex-1 overflow-hidden"
      :class="activeTab === 'home' ? 'flex' : 'hidden'"
  >
    <div
        class="grid h-full min-w-0 grid-cols-5 gap-6 overflow-hidden px-4 py-5 md:px-6"
    >
      <!-- LEFT -->
      <div
          class="relative col-span-4 min-w-0 flex flex-col items-center justify-between overflow-hidden rounded-2xl bg-slate-900 p-6"
      >
        <div
            class="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-slate-950 to-transparent"
        />

        <div
            class="relative z-10 flex flex-1 flex-col items-center justify-center"
        >
          <div
              class="mb-5 text-center text-2xl font-bold tracking-widest text-cyan-400"
          >
            {{ authData ? authData.username.toUpperCase() : "SPIELER" }}
          </div>

          <img
              class="h-64 cursor-pointer object-contain transition-transform duration-300 hover:scale-105"
              :src="playerSkinUrl"
              :data-fallback-src="playerSkinFallback"
              alt="Skin"
              @error="handleImgError"
              @click="activeTab = 'skins'"
          />

          <div class="mt-2 text-xs tracking-widest text-white/40">
            {{ selectedInstance?.running ? selectedInstance.name : "HUGOSMP.NET" }}
          </div>
        </div>

        <div
            class="relative z-10 flex w-full max-w-3xl flex-wrap items-center gap-2 rounded-xl border border-white/10 bg-slate-950/80 px-5 py-3 backdrop-blur-xl"
        >
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-bold text-white">
              {{
                selectedInstance
                    ? selectedInstance.name
                    : authData
                        ? "Kein Profil ausgewählt"
                        : "Bitte anmelden"
              }}
            </div>

            <div class="mt-1 flex flex-wrap gap-3 text-xs text-white/60">
              <span v-if="selectedInstance" class="inline-flex items-center gap-1">
                <Icon icon="lucide:layers" class="size-3"/>
                {{ selectedInstance.versionId }}
              </span>

              <span v-if="selectedInstance" class="inline-flex items-center gap-1">
                <Icon icon="lucide:cpu" class="size-3"/>
                Java {{ selectedInstance.javaMajorVersion }}
              </span>

              <span v-if="selectedInstance" class="inline-flex items-center gap-1">
                <Icon icon="lucide:clock" class="size-3"/>
                {{ formatRelativeDate(selectedInstance.lastPlayedAt) }}
              </span>
            </div>
          </div>

          <button
              v-if="selectedInstance?.running"
              class="rounded-lg bg-cyan-500 px-6 py-3 text-sm font-bold text-white"
              :disabled="isLaunching"
              @click="handleStop"
          >
            STOPP
          </button>

          <button
              v-else-if="authData && selectedInstance"
              class="rounded-lg bg-cyan-500 px-6 py-3 text-sm font-bold text-white"
              :disabled="isLaunching"
              @click="handleLaunch"
          >
            LAUNCH
          </button>

          <button
              v-else-if="!authData"
              class="rounded-lg bg-cyan-500 px-6 py-3 text-sm font-bold text-white"
              :disabled="isAuthenticating"
              @click="handleLogin"
          >
            LOGIN
          </button>
        </div>
      </div>

      <!-- RIGHT SIDEBAR -->
      <div
          class="col-span-1 flex min-w-0 flex-col overflow-hidden rounded-2xl border border-white/10 bg-slate-950/80 backdrop-blur-xl"
      >
        <div class="flex items-center gap-2 border-b border-white/10 px-4 py-4">
          <Icon icon="lucide:newspaper" class="size-4 text-cyan-400"/>
          <h3 class="text-xs font-bold tracking-widest text-white/60">
            NEWS & UPDATES
          </h3>
        </div>

        <div class="flex flex-1 flex-col gap-2 overflow-y-auto p-3">
          <div
              v-for="item in newsItems"
              :key="item.title"
              class="overflow-hidden rounded-xl border border-white/10 bg-slate-900"
          >
            <div
                class="flex h-28 items-center justify-center text-5xl"
                :class="item.backgroundClass"
            >
              {{ item.emoji }}
            </div>

            <div class="p-3">
              <div
                  class="mb-1 inline-flex rounded px-2 py-1 text-xs font-bold"
                  :class="item.badgeClasses"
              >
                {{ item.badge }}
              </div>

              <h4 class="text-sm font-semibold text-white">
                {{ item.title }}
              </h4>

              <p class="mt-1 text-xs text-white/60">
                {{ item.description }}
              </p>

              <div class="mt-2 text-xs text-white/40">
                {{ item.date }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
