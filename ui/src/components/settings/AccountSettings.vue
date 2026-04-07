<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";

const { authData, isAuthenticating, handleLogin, handleLogout } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:user" class="size-[13px]" />MICROSOFT ACCOUNT
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Manage your Microsoft session for Minecraft
      </div>

      <div v-if="authData" class="flex flex-col gap-2.5">
        <div class="flex items-center justify-between gap-4 rounded-[10px] border border-white/10 bg-[var(--surface-input-soft)] p-3">
          <div>
            <div class="mb-1 text-[length:var(--text-md)] font-semibold text-white">{{ authData.username }}</div>
            <div class="font-mono text-[length:var(--text-2xs)] text-white/40">{{ authData.uuid }}</div>
          </div>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/45 transition-all duration-200 hover:bg-white/10"
            @click="handleLogout"
          >
            Sign Out
          </button>
        </div>
      </div>

      <div v-else class="flex flex-col gap-3">
        <div class="text-[length:var(--text-base)] leading-[1.6] text-white/60">
          Sign in with Microsoft to launch Minecraft.
        </div>
        <button
          type="button"
          class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)] disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="isAuthenticating"
          @click="handleLogin"
        >
          {{ isAuthenticating ? "Waiting…" : "Sign in with Microsoft" }}
        </button>
      </div>
    </div>
  </div>
</template>
