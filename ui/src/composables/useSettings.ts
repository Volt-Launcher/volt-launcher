import { ref, watch } from "vue";
import type { JavaRuntime, MainTab } from "./types";

const activeTab = ref<MainTab>("home");
const discoverTabActive = ref("MODPACKS");
const discoverPlatformActive = ref("MODRINTH");
const settingsNavItem = ref("General");
const accentColor = ref("#00b2ff");
const toggleStates = ref({
    autoUpdate: true,
    discordPresence: true,
    betaUpdates: false,
    openLogs: true,
    hwAccel: true,
    hideLauncher: false,
});
const uiScale = ref<"compact" | "default" | "comfortable">("default");
const animationsEnabled = ref(true);
const showFps = ref(false);
const javaRuntimes = ref<JavaRuntime[]>([
    { version: 8, path: "" },
    { version: 17, path: "" },
    { version: 21, path: "" },
]);
const jvmArgs = ref("-XX:+UseG1GC -XX:+ParallelRefProcEnabled");
const minMemory = ref(2);
const maxMemory = ref(4);

const uiScaleFactors: Record<string, number> = { compact: 0.88, default: 1, comfortable: 1.14 };
const baseTextSizes: Record<string, number> = {
    "--text-2xs": 12, "--text-2xs-plus": 12.5, "--text-xs": 13, "--text-sm": 13.5,
    "--text-base": 14, "--text-base-plus": 14.5, "--text-md": 15, "--text-md-plus": 15.5,
    "--text-lg": 16, "--text-xl": 18,
};

const applyUiScale = (scale: string) => {
    const f = uiScaleFactors[scale] ?? 1;
    const el = document.documentElement;
    for (const [token, base] of Object.entries(baseTextSizes))
        el.style.setProperty(token, `${+(base * f).toFixed(1)}px`);
};

const applyAnimations = (enabled: boolean) => {
    document.documentElement.toggleAttribute("data-no-animations", !enabled);
};

const setAccentColor = (c: string) => { accentColor.value = c; };

watch(uiScale, applyUiScale, { immediate: true });
watch(animationsEnabled, applyAnimations, { immediate: true });

export function useSettings() {
    return {
        activeTab, discoverTabActive, discoverPlatformActive, settingsNavItem,
        accentColor, toggleStates, uiScale, animationsEnabled, showFps,
        javaRuntimes, jvmArgs, minMemory, maxMemory,
        setAccentColor,
    };
}
