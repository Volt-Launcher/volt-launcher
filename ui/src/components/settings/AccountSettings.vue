<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";

const { authData, accounts, isAuthenticating, handleLogin, handleLogout, cancelLogin, switchAccount, removeAccount } = useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="rounded-xl border border-white/10 bg-[var(--surface-panel)] p-[18px]">
      <div class="mb-1 flex items-center gap-[7px] text-[length:var(--text-xs)] font-bold tracking-[0.12em] text-white/60">
        <Icon icon="lucide:users" class="size-[13px]" />MICROSOFT ACCOUNTS
      </div>
      <div class="mb-[14px] text-[length:var(--text-base)] leading-[1.55] text-white/60">
        Manage Microsoft accounts for Minecraft
      </div>

      <!-- Account list -->
      <div v-if="accounts.length > 0" class="flex flex-col gap-2 mb-3">
        <div
          v-for="account in accounts"
          :key="account.uuid"
          class="flex items-center justify-between gap-3 rounded-[10px] border bg-[var(--surface-input-soft)] p-3"
          :class="account.selected ? 'border-[var(--accent-border)]' : 'border-white/10'"
        >
          <div class="flex items-center gap-2.5 min-w-0">
            <div
              class="flex size-7 shrink-0 items-center justify-center rounded-full text-[length:var(--text-xs)] font-bold"
              :class="account.selected ? 'bg-[var(--accent-bg-strong)] text-[var(--primary)]' : 'bg-white/10 text-white/50'"
            >
              {{ account.username.charAt(0).toUpperCase() }}
            </div>
            <div class="min-w-0">
              <div class="text-[length:var(--text-md)] font-semibold text-white truncate">{{ account.username }}</div>
              <div class="font-mono text-[length:var(--text-2xs)] text-white/35 truncate">{{ account.uuid }}</div>
            </div>
            <span
              v-if="account.selected"
              class="shrink-0 rounded-[4px] bg-[var(--accent-bg-soft)] px-1.5 py-0.5 text-[length:var(--text-2xs)] font-bold tracking-[0.1em] text-[var(--primary)]/70"
            >
              ACTIVE
            </span>
          </div>

          <div class="flex shrink-0 items-center gap-1.5">
            <button
              v-if="!account.selected"
              type="button"
              class="inline-flex items-center gap-1 rounded-[6px] border border-[var(--accent-border)] bg-[var(--accent-bg-soft)] px-2.5 py-[5px] text-[length:var(--text-sm)] font-semibold text-[var(--primary)]/70 transition-all hover:bg-[var(--accent-bg)] hover:text-[var(--primary)]"
              @click="switchAccount(account.uuid)"
            >
              <Icon icon="lucide:log-in" class="size-[11px]" />
              Switch
            </button>
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-[6px] border border-white/10 bg-white/5 px-2.5 py-[5px] text-[length:var(--text-sm)] font-semibold text-white/40 transition-all hover:border-[var(--danger-border)] hover:bg-[var(--danger-bg)] hover:text-[var(--danger-text)]"
              @click="removeAccount(account.uuid)"
            >
              <Icon icon="lucide:trash-2" class="size-[11px]" />
            </button>
          </div>
        </div>
      </div>

      <!-- No accounts -->
      <div v-else-if="!authData" class="mb-3 text-[length:var(--text-base)] leading-[1.6] text-white/60">
        Sign in with Microsoft to launch Minecraft.
      </div>

      <!-- Add account / cancel button -->
      <button
        v-if="!isAuthenticating"
        type="button"
        class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
        @click="handleLogin"
      >
        <Icon icon="lucide:plus" class="size-[13px]" />
        {{ accounts.length > 0 ? "Add Account" : "Sign in with Microsoft" }}
      </button>
      <button
        v-else
        type="button"
        class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/50 transition-all duration-200 hover:bg-white/10"
        @click="cancelLogin"
      >
        <Icon icon="lucide:loader-2" class="size-[13px] animate-spin" />
        Waiting… (click to cancel)
      </button>
    </div>
  </div>
</template>
