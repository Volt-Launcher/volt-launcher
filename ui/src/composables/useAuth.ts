import { computed, ref } from "vue";
import { apiGet, apiSend, errorMessage } from "./api";
import { error, launcherMessage } from "./state";
import { t } from "@/i18n";
import type { AccountEntry, AuthData } from "./types";

const authData = ref<AuthData | null>(null);
const accounts = ref<AccountEntry[]>([]);
const isAuthenticating = ref(false);

let authState = "";
let authPollInterval: ReturnType<typeof window.setInterval> | null = null;

const playerName = computed(() => authData.value?.username ?? "");
const playerUuid = computed(() => authData.value?.uuid ?? "");

// Two skin services are used so a rendering outage on one does not leave the UI without an avatar.
const playerSkinUrl = computed(
  () => `https://crafatar.com/renders/body/${encodeURIComponent(playerUuid.value || "MHF_Steve")}?overlay&scale=10`,
);
const playerSkinFallback = computed(
  () => `https://mc-heads.net/body/${encodeURIComponent(playerName.value || "MHF_Steve")}/300`,
);
const playerSkinTextureUrl = computed(
  () => `https://mineskin.eu/skin/${encodeURIComponent(playerName.value || "MHF_Steve")}`,
);
const playerAvatarUrl = computed(
  () => `https://crafatar.com/avatars/${encodeURIComponent(playerUuid.value || "MHF_Steve")}?size=24&overlay`,
);
const playerAvatarFallback = computed(
  () => `https://mc-heads.net/avatar/${encodeURIComponent(playerName.value || "MHF_Steve")}/24`,
);

export const stopAuth = () => {
  if (authPollInterval !== null) {
    window.clearInterval(authPollInterval);
    authPollInterval = null;
  }
  authState = "";
};

const cancelLogin = () => {
  stopAuth();
  isAuthenticating.value = false;
};

const pollAuthStatus = async () => {
  if (!authState) return;
  try {
    const status = await apiGet<{
      success: boolean;
      status: "pending" | "success" | "error" | "expired";
      uuid?: string;
      username?: string;
      error?: string;
    }>(`/api/auth/status?state=${encodeURIComponent(authState)}`);

    if (status.status === "pending") return;

    if (status.status === "success" && status.uuid && status.username) {
      authData.value = { uuid: status.uuid, username: status.username };
      error.value = null;
      await loadAllAccounts();
    } else {
      error.value = status.error ?? t("auth.failed");
    }
  } catch (e) {
    error.value = errorMessage(e, t("auth.failed"));
  }
  isAuthenticating.value = false;
  stopAuth();
};

const handleLogin = async () => {
  try {
    isAuthenticating.value = true;
    error.value = null;

    const response = await apiGet<{ success: boolean; state: string; url: string }>("/api/auth/login");
    authState = response.state;
    window.open(response.url, "MicrosoftAuth", "width=520,height=760");
    authPollInterval = window.setInterval(() => void pollAuthStatus(), 1500);
  } catch (e) {
    error.value = errorMessage(e, t("auth.failed"));
    isAuthenticating.value = false;
    stopAuth();
  }
};

const handleLogout = async () => {
  try {
    await apiSend("POST", "/api/auth/logout");
  } catch {
    // Local state is cleared regardless so the UI cannot get stuck signed in.
  }
  authData.value = null;
  accounts.value = [];
  error.value = null;
  launcherMessage.value = null;
};

const loadSession = async () => {
  try {
    const session = await apiGet<{
      success: boolean;
      authenticated: boolean;
      uuid?: string;
      username?: string;
    }>("/api/session");

    if (session.authenticated && session.uuid && session.username) {
      authData.value = { uuid: session.uuid, username: session.username };
      await loadAllAccounts();
      return;
    }
    authData.value = null;
  } catch (e) {
    authData.value = null;
    error.value = errorMessage(e, t("auth.sessionFailed"));
  }
};

const loadAllAccounts = async () => {
  try {
    const response = await apiGet<{ success: boolean; accounts: AccountEntry[] }>("/api/accounts");
    accounts.value = response.accounts;
    const selected = response.accounts.find((account) => account.selected);
    authData.value = selected ? { uuid: selected.uuid, username: selected.username } : null;
  } catch {
    // The account list is refreshed on the next poll; no need to surface a transient failure.
  }
};

const switchAccount = async (uuid: string) => {
  try {
    await apiSend("POST", `/api/accounts/${encodeURIComponent(uuid)}/select`);
    await loadAllAccounts();
  } catch (e) {
    error.value = errorMessage(e, t("auth.switchFailed"));
  }
};

const removeAccount = async (uuid: string) => {
  try {
    await apiSend("DELETE", `/api/accounts/${encodeURIComponent(uuid)}`);
    await loadAllAccounts();
  } catch (e) {
    error.value = errorMessage(e, t("auth.removeFailed"));
  }
};

export function useAuth() {
  return {
    authData,
    accounts,
    isAuthenticating,
    playerName,
    playerUuid,
    playerSkinUrl,
    playerSkinFallback,
    playerSkinTextureUrl,
    playerAvatarUrl,
    playerAvatarFallback,
    handleLogin,
    handleLogout,
    loadSession,
    stopAuth,
    cancelLogin,
    loadAllAccounts,
    switchAccount,
    removeAccount,
  };
}
