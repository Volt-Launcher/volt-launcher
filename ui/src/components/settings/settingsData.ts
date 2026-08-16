import type { SettingsSectionId } from "@/composables/types";

export const settingsNav: Array<{ id: SettingsSectionId; icon: string; labelKey: string }> = [
  { id: "general", icon: "lucide:sun", labelKey: "settings.sections.general" },
  { id: "appearance", icon: "lucide:monitor", labelKey: "settings.sections.appearance" },
  { id: "java", icon: "lucide:coffee", labelKey: "settings.sections.java" },
  { id: "providers", icon: "lucide:blocks", labelKey: "settings.sections.providers" },
  { id: "updates", icon: "lucide:download", labelKey: "settings.sections.updates" },
  { id: "account", icon: "lucide:user", labelKey: "settings.sections.account" },
  { id: "advanced", icon: "lucide:settings-2", labelKey: "settings.sections.advanced" },
];

export const accentColors = [
  "#00b2ff",
  "#6c63ff",
  "#8b5cf6",
  "#ec4899",
  "#10b981",
  "#00ffcc",
  "#f59e0b",
  "#ef4444",
  "#f97316",
  "#64748b",
] as const;
