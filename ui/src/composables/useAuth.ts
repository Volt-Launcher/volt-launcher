import { computed, ref } from "vue";
import { apiFetch, APP_API_BASE } from "./api";
import { error, launcherMessage } from "./state";
import type { AccountEntry, AuthData } from "./types";

const authData = ref<AuthData | null>(null);
const accounts = ref<AccountEntry[]>([]);
const isAuthenticating = ref(false);

let authState = "";
let authPollInterval: ReturnType<typeof window.setInterval> | null = null;

const playerName = computed(() => authData.value?.username ?? "");

const playerSkinUrl = computed(() =>
    `https://crafatar.com/renders/body/${encodeURIComponent(playerName.value || "MHF_Steve")}?overlay&scale=10`
);
const playerSkinFallback = computed(() =>
    `https://mc-heads.net/body/${encodeURIComponent(playerName.value || "MHF_Steve")}/300`
);
const playerSkinTextureUrl = computed(() =>
    authData.value?.uuid
        ? `https://mineskin.eu/skin/${encodeURIComponent(authData.value.uuid)}`
        : "https://mineskin.eu/skin/MHF_Steve"
);
const playerAvatarUrl = computed(() =>
    `https://crafatar.com/avatars/${encodeURIComponent(playerName.value || "MHF_Steve")}?size=24&overlay`
);
const playerAvatarFallback = computed(() =>
    `https://mc-heads.net/avatar/${encodeURIComponent(playerName.value || "MHF_Steve")}/24`
);

export const stopAuth = () => {
    if (authPollInterval !== null) { window.clearInterval(authPollInterval); authPollInterval = null; }
    authState = "";
};

const cancelLogin = () => {
    stopAuth();
    isAuthenticating.value = false;
};

const pollAuthStatus = async () => {
    if (!authState) return;
    try {
        const d = await apiFetch<{
            success: boolean; status: string;
            uuid?: string; username?: string; error?: string;
        }>(`/api/auth/status?state=${encodeURIComponent(authState)}`);

        if (d.status === "pending") return;
        if (d.status === "success" && d.uuid && d.username) {
            authData.value = { uuid: d.uuid, username: d.username };
            error.value = null;
            await loadAllAccounts();
        } else {
            error.value = d.error ?? "Authentifizierung fehlgeschlagen";
        }
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Authentifizierungsstatus konnte nicht geprüft werden";
    }
    isAuthenticating.value = false;
    stopAuth();
};

const handleLogin = async () => {
    try {
        isAuthenticating.value = true;
        error.value = null;
        const d = await apiFetch<{ success: boolean; state?: string; url?: string; error?: string }>("/api/auth/login");
        if (!d.success || !d.state || !d.url) {
            error.value = d.error ?? "Authentifizierung fehlgeschlagen";
            isAuthenticating.value = false;
            return;
        }
        authState = d.state;
        window.open(d.url, "MicrosoftAuth", "width=520,height=760");
        authPollInterval = window.setInterval(() => { void pollAuthStatus(); }, 1500);
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Unbekannter Fehler";
        isAuthenticating.value = false;
        stopAuth();
    }
};

const handleLogout = async () => {
    try { await fetch(`${APP_API_BASE}/api/auth/logout`, { method: "POST" }); } catch { /* ignore */ }
    authData.value = null;
    accounts.value = [];
    error.value = null;
    launcherMessage.value = null;
};

const loadSession = async () => {
    try {
        const d = await apiFetch<{
            success: boolean; authenticated: boolean;
            uuid?: string; username?: string; error?: string;
        }>("/api/session");
        if (d.authenticated && d.uuid && d.username) {
            authData.value = { uuid: d.uuid, username: d.username };
            await loadAllAccounts();
            return;
        }
        authData.value = null;
        if (!d.success) error.value = d.error ?? "Session konnte nicht wiederhergestellt werden";
    } catch (e) {
        authData.value = null;
        error.value = e instanceof Error ? e.message : "Session konnte nicht wiederhergestellt werden";
    }
};

const loadAllAccounts = async () => {
    try {
        const d = await apiFetch<{
            success: boolean;
            accounts?: AccountEntry[];
            error?: string;
        }>("/api/accounts");
        if (d.success && d.accounts) {
            accounts.value = d.accounts;
            const selected = d.accounts.find(a => a.selected);
            if (selected) authData.value = { uuid: selected.uuid, username: selected.username };
        }
    } catch { /* ignore */ }
};

const switchAccount = async (uuid: string) => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/accounts/${encodeURIComponent(uuid)}/select`,
            { method: "POST" }
        );
        if (!d.success) { error.value = d.error ?? "Konto konnte nicht gewechselt werden"; return; }
        await loadAllAccounts();
        launcherMessage.value = null;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Konto konnte nicht gewechselt werden";
    }
};

const removeAccount = async (uuid: string) => {
    try {
        const d = await apiFetch<{ success: boolean; error?: string }>(
            `/api/accounts/${encodeURIComponent(uuid)}`,
            { method: "DELETE" }
        );
        if (!d.success) { error.value = d.error ?? "Konto konnte nicht entfernt werden"; return; }
        await loadAllAccounts();
        const selected = accounts.value.find(a => a.selected);
        if (!selected) { authData.value = null; }
        launcherMessage.value = null;
    } catch (e) {
        error.value = e instanceof Error ? e.message : "Konto konnte nicht entfernt werden";
    }
};

export function useAuth() {
    return {
        authData, accounts, isAuthenticating,
        playerName, playerSkinUrl, playerSkinFallback, playerSkinTextureUrl,
        playerAvatarUrl, playerAvatarFallback,
        handleLogin, handleLogout, loadSession, stopAuth, cancelLogin,
        loadAllAccounts, switchAccount, removeAccount,
    };
}
