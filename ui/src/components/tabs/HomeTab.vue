<script setup lang="ts">
import { computed } from "vue";
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import SkinModel from "@/components/SkinModel.vue";
import InstallProgress from "@/components/discover/InstallProgress.vue";

const {
  t,
  authData,
  isAuthenticating,
  isLaunching,
  activeTab,
  selectedInstance,
  playerSkinTextureUrl,
  formatRelativeDate,
  formatLoaderId,
  handleLogin,
  handleLaunch,
  handleStop,
} = useLauncher();

const newsItems = computed(() => [
  {
    emoji: "🚀",
    title: t("home.news.releaseTitle"),
    description: t("home.news.releaseBody"),
    date: "2026-04-05",
    badge: "UPDATE",
    badgeClass: "border-[var(--accent-border)] bg-[var(--accent-bg)] text-[var(--primary)]",
  },
  {
    emoji: "🔥",
    title: t("home.news.providersTitle"),
    description: t("home.news.providersBody"),
    date: "2026-04-03",
    badge: "DISCOVER",
    badgeClass: "border-[var(--accent-border-soft)] bg-[rgba(139,92,246,0.15)] text-[var(--violet)]",
  },
  {
    emoji: "☕",
    title: t("home.news.javaTitle"),
    description: t("home.news.javaBody"),
    date: "2026-04-01",
    badge: "SETTINGS",
    badgeClass: "border-[var(--success-border)] bg-[var(--success-bg)] text-[var(--accent)]",
  },
]);
</script>

<template>
  <div class="flex-1 overflow-hidden" :class="activeTab === 'home' ? 'flex' : 'hidden'">
    <div class="flex h-full w-full gap-4 overflow-hidden px-4 py-4 md:px-6">

      <!-- LEFT: skin hero -->
      <div
        class="relative col-span-4 flex w-full min-w-0 flex-col overflow-hidden rounded-2xl border border-white/8 bg-[var(--surface-panel)]">

        <!-- ambient glow at bottom -->
        <div
          class="pointer-events-none absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-[var(--app-bg)] via-[rgba(3,9,18,.55)] to-transparent" />
        <div
          class="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-[radial-gradient(ellipse_80%_100%_at_50%_100%,rgba(var(--primary-rgb),.07),transparent)]" />

        <!-- skin model + username -->
        <div class="relative z-10 flex flex-1 flex-col items-center justify-center overflow-hidden">
          <div class="mb-1 text-[length:var(--text-xl)] font-bold tracking-[0.14em] text-white">
            {{ authData ? authData.username : t("home.player") }}
          </div>

          <div class="relative flex items-center justify-center">
            <div class="pointer-events-none absolute inset-x-6 top-10 bottom-12 rounded-[42%] bg-[radial-gradient(circle_at_58%_38%,rgba(255,255,255,0.08),rgba(7,16,30,0.24)_34%,rgba(3,9,18,0.02)_72%,transparent_100%)] blur-3xl" />
            <div class="pointer-events-none absolute bottom-5 right-8 h-28 w-64 rounded-full bg-[radial-gradient(ellipse_72%_62%_at_56%_56%,rgba(0,0,0,0.42),rgba(3,9,18,0.22)_58%,transparent_76%)] blur-2xl" />
            <div class="pointer-events-none absolute bottom-8 right-12 h-20 w-44 rounded-full bg-[radial-gradient(ellipse_72%_72%_at_50%_50%,var(--accent-bg),transparent_78%)] blur-xl" />
            <SkinModel :skin="playerSkinTextureUrl" :width="340" :height="420" animation="chill" :interactive="false" />
          </div>
        </div>

        <!-- launch bar -->
        <div
          class="absolute left-1/2 transform -translate-x-1/2 bottom-4 z-10 mb-16 flex flex-col items-center gap-2">

          <button v-if="!authData" type="button"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-5 py-2.5 text-[length:var(--text-sm)] font-bold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)] disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="isAuthenticating" @click="handleLogin">
            <Icon icon="lucide:log-in" class="size-[11px]" />{{ isAuthenticating ? t("home.waiting") : t("home.login") }}
          </button>

          <button v-else-if="selectedInstance?.running" type="button"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-[var(--danger-border)] bg-[var(--danger-bg)] px-5 py-2.5 text-[length:var(--text-sm)] font-bold tracking-[0.07em] text-[var(--danger-text)] transition-all duration-200 hover:shadow-[var(--shadow-danger-xs)] disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="isLaunching" @click="handleStop">
            <Icon icon="lucide:square" class="size-[11px]" />{{ t("home.stop") }}
          </button>

          <button v-else-if="selectedInstance" type="button"
            class="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-[var(--accent-border-strong)] bg-[var(--primary)] px-5 py-2.5 text-[length:var(--text-sm)] font-bold tracking-[0.07em] text-white shadow-[var(--shadow-accent-md)] transition-all duration-200 hover:shadow-[var(--shadow-accent-lg)] disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="isLaunching || !selectedInstance || selectedInstance.busy" @click="handleLaunch">
            <Icon icon="lucide:play" class="size-[11px]" />{{ !selectedInstance ? t("home.noProfileSelected") : isLaunching ? t("home.starting") : t("home.launch") }}
          </button>

          <div v-if="selectedInstance?.busy"
            class="mt-0.5 w-full max-w-[380px] rounded-xl border border-[var(--accent-border)] bg-[var(--surface-panel-strong)] px-4 py-3 backdrop-blur-xl">
            <InstallProgress :stage="selectedInstance.busyStage ?? null"
              :completed="selectedInstance.busyCompleted ?? 0" :total="selectedInstance.busyTotal ?? 0"
              :percent="selectedInstance.busyPercent ?? -1" />
          </div>

          <div v-if="selectedInstance" class="mt-0.5 flex flex-wrap gap-3 text-[length:var(--text-xs)] text-white/50  rounded-xl border border-white/8 bg-[var(--surface-panel-strong)] px-4 py-3 backdrop-blur-xl">
            <span v-if="selectedInstance" class="inline-flex items-center gap-1">
              <Icon icon="lucide:layers" class="size-[11px]" />{{ selectedInstance ? formatLoaderId(selectedInstance.versionId) : t("home.unknownVersion") }}
            </span>
            <span v-if="selectedInstance" class="inline-flex items-center gap-1">
              <Icon icon="lucide:cpu" class="size-[11px]" />Java {{ selectedInstance?.javaMajorVersion || "N/A" }}
            </span>
            <span v-if="selectedInstance" class="inline-flex items-center gap-1">
              <Icon icon="lucide:clock" class="size-[11px]" />{{ formatRelativeDate(selectedInstance?.lastPlayedAt) || "N/A" }}
            </span>
          </div>

        </div>
      </div>

      <!-- RIGHT: news sidebar -->
      <div
        class="w-150 flex-col overflow-hidden rounded-2xl border border-white/8 bg-[var(--surface-panel)]">
        <div class="flex shrink-0 items-center gap-2 border-b border-white/7 px-4 py-3">
          <Icon icon="lucide:newspaper" class="size-[13px] text-[var(--primary)]" />
          <span class="text-[length:var(--text-2xs)] font-bold tracking-[0.14em] text-white/50 uppercase">{{ t("home.newsTitle") }}</span>
        </div>

        <div class="flex flex-1 flex-col gap-2 overflow-y-scroll p-2.5">
          <div v-for="item in newsItems" :key="item.title"
            class="overflow-hidden rounded-xl border border-white/8 bg-[var(--surface-panel-strong)] transition-all duration-200 hover:border-white/15">
            <div class="flex h-20 items-center justify-center text-4xl bg-[var(--surface-panel-muted)]">
              {{ item.emoji }}
            </div>
            <div class="p-2.5">
              <div class="mb-1.5 inline-flex rounded-[5px] border px-1.5 py-0.5 text-[length:var(--text-2xs)] font-bold"
                :class="item.badgeClass">
                {{ item.badge }}
              </div>
              <div class="text-[length:var(--text-base-plus)] font-semibold text-white">{{ item.title }}</div>
              <p class="mt-0.5 text-[length:var(--text-xs)] leading-[1.5] text-white/55">{{ item.description }}</p>
              <div class="mt-1.5 text-[length:var(--text-2xs)] text-white/30">{{ item.date }}</div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>
