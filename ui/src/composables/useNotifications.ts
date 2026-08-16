import { computed, ref } from "vue";
import { t } from "@/i18n";
import type { LauncherNotification } from "./types";

const STORAGE_KEY = "launcher_notifications";
const MAX_NOTIFICATIONS = 100;

let idCounter = 0;

const loadStored = (): LauncherNotification[] => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as LauncherNotification[];
    idCounter = parsed.reduce((max, entry) => Math.max(max, entry.id), 0);
    return parsed;
  } catch {
    return [];
  }
};

const notifications = ref<LauncherNotification[]>(loadStored());

const persist = () => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(notifications.value));
  } catch {
    // Storage can be full or disabled; the in-memory list still works.
  }
};

export const pushNotification = (type: "error" | "info", message: string) => {
  // Collapse repeats so a polling loop that keeps failing does not flood the list.
  const newest = notifications.value[0];
  if (newest && newest.type === type && newest.message === message) return;

  notifications.value.unshift({ id: ++idCounter, type, message, timestamp: Date.now() });
  if (notifications.value.length > MAX_NOTIFICATIONS) {
    notifications.value = notifications.value.slice(0, MAX_NOTIFICATIONS);
  }
  persist();
};

export const clearNotification = (id: number) => {
  notifications.value = notifications.value.filter((entry) => entry.id !== id);
  persist();
};

export const clearAllNotifications = () => {
  notifications.value = [];
  persist();
};

export const unreadCount = computed(() => notifications.value.length);

export const formatNotifTime = (timestamp: number) => {
  const elapsed = Date.now() - timestamp;
  const minutes = Math.floor(elapsed / 60_000);
  if (minutes < 1) return t("time.justNow");
  if (minutes < 60) return t("time.minutesAgo", { n: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t("time.hoursAgo", { n: hours });
  return t("time.daysAgo", { n: Math.floor(hours / 24) });
};

export function useNotifications() {
  return {
    notifications,
    unreadCount,
    formatNotifTime,
    pushNotification,
    clearNotification,
    clearAllNotifications,
  };
}
