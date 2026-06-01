import { computed, ref } from "vue";
import type { LauncherNotification } from "./types";

const NOTIF_KEY = "launcher_notifications";
let notifIdCounter = 0;

const loadStoredNotifications = (): LauncherNotification[] => {
    try {
        const raw = sessionStorage.getItem(NOTIF_KEY);
        if (raw) {
            const parsed = JSON.parse(raw) as LauncherNotification[];
            notifIdCounter = parsed.reduce((max, n) => Math.max(max, n.id), 0);
            return parsed;
        }
    } catch { /* ignore */ }
    return [];
};

const notifications = ref<LauncherNotification[]>(loadStoredNotifications());

const persist = () => {
    try { sessionStorage.setItem(NOTIF_KEY, JSON.stringify(notifications.value)); } catch { /* ignore */ }
};

export const pushNotification = (type: "error" | "info", message: string) => {
    notifications.value.unshift({ id: ++notifIdCounter, type, message, timestamp: Date.now() });
    persist();
};

export const clearNotification = (id: number) => {
    notifications.value = notifications.value.filter(n => n.id !== id);
    persist();
};

export const clearAllNotifications = () => {
    notifications.value = [];
    persist();
};

export const unreadCount = computed(() => notifications.value.length);

export const formatNotifTime = (ts: number) => {
    const d = Date.now() - ts;
    const s = Math.floor(d / 1000);
    const m = Math.floor(d / 60000);
    const h = Math.floor(d / 3600000);
    const dy = Math.floor(d / 86400000);
    if (s < 60) return "just now";
    if (m < 60) return `${m}m ago`;
    if (h < 24) return `${h}h ago`;
    return `${dy}d ago`;
};

export function useNotifications() {
    return { notifications, unreadCount, formatNotifTime, pushNotification, clearNotification, clearAllNotifications };
}
