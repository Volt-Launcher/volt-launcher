package app.voltlauncher.core.config;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Launcher-wide preferences. Every field has a usable default so a missing or partially written
 * settings file never leaves the launcher unusable.
 */
public record LauncherSettings(
        String language,
        String accentColor,
        String uiScale,
        boolean animationsEnabled,
        boolean showFps,
        boolean discordPresence,
        boolean hideLauncherOnLaunch,
        boolean openLogsOnLaunch,
        boolean autoUpdate,
        boolean betaUpdates,
        int defaultMaxMemoryMb,
        int defaultMinMemoryMb,
        String defaultJvmArgs,
        int maxConcurrentDownloads,
        String curseForgeBridgeUrl,
        List<JavaRuntimeEntry> javaRuntimes) {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final String DEFAULT_ACCENT = "#00b2ff";
    public static final String DEFAULT_UI_SCALE = "default";
    public static final String DEFAULT_JVM_ARGS = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled";
    public static final String DEFAULT_BRIDGE_URL = "http://localhost:8787";

    private static final List<String> SUPPORTED_LANGUAGES = List.of("en", "de");
    private static final List<String> SUPPORTED_SCALES = List.of("compact", "default", "comfortable");

    /** A user-supplied Java installation, pinned to the major version it provides. */
    public record JavaRuntimeEntry(int majorVersion, String path) {
        public JSONObject toJson() {
            return new JSONObject()
                    .put("majorVersion", majorVersion)
                    .put("path", path == null ? "" : path);
        }

        public static JavaRuntimeEntry fromJson(JSONObject json) {
            return new JavaRuntimeEntry(json.optInt("majorVersion", 0), json.optString("path", ""));
        }
    }

    /** Normalises out-of-range values rather than rejecting them, so a hand-edited file still loads. */
    public LauncherSettings {
        language = SUPPORTED_LANGUAGES.contains(lower(language)) ? lower(language) : DEFAULT_LANGUAGE;
        accentColor = isHexColor(accentColor) ? accentColor : DEFAULT_ACCENT;
        uiScale = SUPPORTED_SCALES.contains(lower(uiScale)) ? lower(uiScale) : DEFAULT_UI_SCALE;
        defaultMaxMemoryMb = clamp(defaultMaxMemoryMb, 512, 65_536, 4096);
        defaultMinMemoryMb = clamp(defaultMinMemoryMb, 256, defaultMaxMemoryMb, Math.min(1024, defaultMaxMemoryMb));
        defaultJvmArgs = defaultJvmArgs == null ? DEFAULT_JVM_ARGS : defaultJvmArgs;
        maxConcurrentDownloads = clamp(maxConcurrentDownloads, 1, 32, 8);
        curseForgeBridgeUrl = blankTo(curseForgeBridgeUrl, DEFAULT_BRIDGE_URL);
        javaRuntimes = javaRuntimes == null ? List.of() : List.copyOf(javaRuntimes);
    }

    public static LauncherSettings defaults() {
        return new LauncherSettings(
                DEFAULT_LANGUAGE, DEFAULT_ACCENT, DEFAULT_UI_SCALE,
                true, false, true, false, false, true, false,
                4096, 1024, DEFAULT_JVM_ARGS, 8, DEFAULT_BRIDGE_URL, List.of());
    }

    public static LauncherSettings fromJson(JSONObject json) {
        if (json == null) return defaults();
        LauncherSettings fallback = defaults();

        List<JavaRuntimeEntry> runtimes = new ArrayList<>();
        JSONArray runtimeArray = json.optJSONArray("javaRuntimes");
        if (runtimeArray != null) {
            for (int i = 0; i < runtimeArray.length(); i++) {
                JSONObject entry = runtimeArray.optJSONObject(i);
                if (entry != null) runtimes.add(JavaRuntimeEntry.fromJson(entry));
            }
        }

        return new LauncherSettings(
                json.optString("language", fallback.language()),
                json.optString("accentColor", fallback.accentColor()),
                json.optString("uiScale", fallback.uiScale()),
                json.optBoolean("animationsEnabled", fallback.animationsEnabled()),
                json.optBoolean("showFps", fallback.showFps()),
                json.optBoolean("discordPresence", fallback.discordPresence()),
                json.optBoolean("hideLauncherOnLaunch", fallback.hideLauncherOnLaunch()),
                json.optBoolean("openLogsOnLaunch", fallback.openLogsOnLaunch()),
                json.optBoolean("autoUpdate", fallback.autoUpdate()),
                json.optBoolean("betaUpdates", fallback.betaUpdates()),
                json.optInt("defaultMaxMemoryMb", fallback.defaultMaxMemoryMb()),
                json.optInt("defaultMinMemoryMb", fallback.defaultMinMemoryMb()),
                json.optString("defaultJvmArgs", fallback.defaultJvmArgs()),
                json.optInt("maxConcurrentDownloads", fallback.maxConcurrentDownloads()),
                json.optString("curseForgeBridgeUrl", fallback.curseForgeBridgeUrl()),
                runtimes);
    }

    public JSONObject toJson() {
        JSONArray runtimeArray = new JSONArray();
        javaRuntimes.forEach(entry -> runtimeArray.put(entry.toJson()));

        return new JSONObject()
                .put("language", language)
                .put("accentColor", accentColor)
                .put("uiScale", uiScale)
                .put("animationsEnabled", animationsEnabled)
                .put("showFps", showFps)
                .put("discordPresence", discordPresence)
                .put("hideLauncherOnLaunch", hideLauncherOnLaunch)
                .put("openLogsOnLaunch", openLogsOnLaunch)
                .put("autoUpdate", autoUpdate)
                .put("betaUpdates", betaUpdates)
                .put("defaultMaxMemoryMb", defaultMaxMemoryMb)
                .put("defaultMinMemoryMb", defaultMinMemoryMb)
                .put("defaultJvmArgs", defaultJvmArgs)
                .put("maxConcurrentDownloads", maxConcurrentDownloads)
                .put("curseForgeBridgeUrl", curseForgeBridgeUrl)
                .put("javaRuntimes", runtimeArray);
    }

    /** Returns the configured path for a Java major version, or {@code null} to auto-detect. */
    public String javaPathFor(int majorVersion) {
        return javaRuntimes.stream()
                .filter(entry -> entry.majorVersion() == majorVersion)
                .map(JavaRuntimeEntry::path)
                .filter(path -> path != null && !path.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean isHexColor(String value) {
        return value != null && value.matches("#[0-9a-fA-F]{6}");
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) return Math.min(Math.max(fallback, min), max);
        return value;
    }
}
