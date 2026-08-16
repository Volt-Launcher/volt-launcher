<script setup lang="ts">
import { Icon } from "@iconify/vue";
import { useLauncher } from "@/composables/useLauncher";
import SettingsPanel from "./SettingsPanel.vue";

const { t, authData, accounts, isAuthenticating, handleLogin, cancelLogin, switchAccount, removeAccount } =
  useLauncher();
</script>

<template>
  <div class="flex flex-col gap-3">
    <SettingsPanel :title="t('auth.accounts')" :description="t('auth.accountsHint')" icon="lucide:users">
      <div v-if="accounts.length > 0" class="mb-3 flex flex-col gap-2">
        <div
          v-for="account in accounts"
          :key="account.uuid"
          class="flex flex-wrap items-center justify-between gap-3 rounded-[10px] border bg-[var(--surface-input-soft)] p-3"
          :class="account.selected ? 'border-[var(--accent-border)]' : 'border-white/10'"
        >
          <div class="flex min-w-0 items-center gap-2.5">
            <img
              :src="`https://crafatar.com/avatars/${encodeURIComponent(account.uuid)}?size=32&overlay`"
              :alt="account.username"
              class="size-7 shrink-0 rounded-full bg-white/10"
              loading="lazy"
            />
            <div class="min-w-0">
              <div class="truncate text-[length:var(--text-md)] font-semibold text-white">
                {{ account.username }}
              </div>
              <div class="truncate font-mono text-[length:var(--text-2xs)] text-white/35">{{ account.uuid }}</div>
            </div>
            <span
              v-if="account.selected"
              class="shrink-0 rounded-[4px] bg-[var(--accent-bg-soft)] px-1.5 py-0.5 text-[length:var(--text-2xs)] font-bold tracking-[0.1em] text-[var(--primary)]/70 uppercase"
            >
              {{ t("auth.active") }}
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
              {{ t("auth.switch") }}
            </button>
            <button
              type="button"
              :aria-label="t('common.remove')"
              class="inline-flex items-center gap-1 rounded-[6px] border border-white/10 bg-white/5 px-2.5 py-[5px] text-[length:var(--text-sm)] font-semibold text-white/40 transition-all hover:border-[var(--danger-border)] hover:bg-[var(--danger-bg)] hover:text-[var(--danger-text)]"
              @click="removeAccount(account.uuid)"
            >
              <Icon icon="lucide:trash-2" class="size-[11px]" />
            </button>
          </div>
        </div>
      </div>

      <p v-else-if="!authData" class="mb-3 text-[length:var(--text-base)] leading-[1.6] text-white/60">
        {{ t("auth.signInHint") }}
      </p>

      <button
        v-if="!isAuthenticating"
        type="button"
        class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-[var(--accent-border-strong)] bg-[var(--accent-bg-strong)] px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-[var(--primary)] transition-all duration-200 hover:bg-[var(--accent-bg-hover)] hover:shadow-[var(--shadow-accent-md)]"
        @click="handleLogin"
      >
        <Icon icon="lucide:plus" class="size-[13px]" />
        {{ accounts.length > 0 ? t("auth.addAccount") : t("auth.signIn") }}
      </button>
      <button
        v-else
        type="button"
        class="inline-flex w-fit items-center gap-1.5 rounded-[7px] border border-white/10 bg-white/5 px-3.5 py-[7px] text-[length:var(--text-sm)] font-semibold tracking-[0.07em] text-white/50 transition-all duration-200 hover:bg-white/10"
        @click="cancelLogin"
      >
        <Icon icon="lucide:loader-2" class="size-[13px] animate-spin" />
        {{ t("auth.waiting") }}
      </button>
    </SettingsPanel>
  </div>
</template>
