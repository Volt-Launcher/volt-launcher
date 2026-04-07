export const settingsNav = [
  { id: "General", icon: "lucide:sun" },
  { id: "Appearance", icon: "lucide:monitor" },
  { id: "Java & Memory", icon: "lucide:layers" },
  { id: "Updates", icon: "lucide:download" },
  { id: "Account", icon: "lucide:user" },
  { id: "Advanced", icon: "lucide:settings-2" },
] as const;

export type SettingsNavId = (typeof settingsNav)[number]["id"];

export const accentColors = [
  { value: "#00b2ff", class: "bg-[#00b2ff]" },
  { value: "#6c63ff", class: "bg-[#6c63ff]" },
  { value: "#8b5cf6", class: "bg-[#8b5cf6]" },
  { value: "#ec4899", class: "bg-[#ec4899]" },
  { value: "#10b981", class: "bg-[#10b981]" },
  { value: "#00ffcc", class: "bg-[#00ffcc]" },
  { value: "#f59e0b", class: "bg-[#f59e0b]" },
  { value: "#ef4444", class: "bg-[#ef4444]" },
  { value: "#f97316", class: "bg-[#f97316]" },
  { value: "#64748b", class: "bg-[#64748b]" },
] as const;

export const generalToggles = [
  { key: "autoUpdate", name: "Auto Updates", sub: "Download updates automatically" },
  { key: "discordPresence", name: "Discord Presence", sub: "Show status in Discord" },
  { key: "hideLauncher", name: "Hide Launcher", sub: "Hide when game starts" },
] as const;

export const advancedToggles = [
  { key: "betaUpdates", name: "Beta Updates", sub: "Receive pre-release builds" },
  { key: "openLogs", name: "Open Logs", sub: "Show logs after game launch" },
  { key: "hwAccel", name: "Hardware Accel.", sub: "GPU acceleration" },
] as const;
