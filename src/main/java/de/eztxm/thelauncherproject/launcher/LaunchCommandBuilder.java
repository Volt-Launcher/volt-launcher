package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.auth.MinecraftAccountSession;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LaunchCommandBuilder {

    private final String launcherClientId;

    public LaunchCommandBuilder(String launcherClientId) {
        this.launcherClientId = launcherClientId;
    }

    public List<String> build(
            MinecraftAccountSession session,
            AssetInstaller.Installation install,
            JavaRuntimeResolver.JavaRuntime runtime) {

        Map<String, String> vars = buildVars(session, install);
        List<String> cmd = new ArrayList<>();
        cmd.add(runtime.javaExecutable().toString());

        List<String> jvmArgs = collectArgs(install.launchMetadata(), "jvm");
        if (jvmArgs.stream().noneMatch(a -> a.startsWith("-Xmx"))) {
            cmd.add("-Xmx2G");
        }
        for (String arg : jvmArgs) {
            cmd.add(apply(arg, vars));
        }
        cmd.add(install.mainClass());
        for (String arg : collectArgs(install.launchMetadata(), "game")) {
            cmd.add(apply(arg, vars));
        }
        return List.copyOf(cmd);
    }

    private Map<String, String> buildVars(MinecraftAccountSession s, AssetInstaller.Installation i) {
        Map<String, String> v = new HashMap<>();
        v.put("auth_player_name",    s.username());
        v.put("version_name",        i.launchVersionId());
        v.put("game_directory",      i.instance().gameDirectory().toString());
        v.put("assets_root",         i.assetsDirectory().toString());
        v.put("assets_index_name",   i.assetIndexId());
        v.put("auth_uuid",           s.uuid());
        v.put("auth_access_token",   s.minecraftAccessToken());
        v.put("auth_session",        s.minecraftAccessToken());
        v.put("clientid",            launcherClientId);
        v.put("auth_xuid",           s.xuid() == null ? "" : s.xuid());
        v.put("user_type",           "msa");
        v.put("version_type",        i.versionType());
        v.put("user_properties",     "{}");
        v.put("natives_directory",   i.nativesDirectory().toString());
        v.put("launcher_name",       "TheLauncherProject");
        v.put("launcher_version",    "0.1.0");
        v.put("classpath",           i.classpath());
        v.put("classpath_separator", File.pathSeparator);
        v.put("library_directory",   i.librariesDirectory().toString());
        v.put("resolution_width",    "1280");
        v.put("resolution_height",   "720");
        v.put("game_assets",         i.assetIndexId());
        if (i.loggingConfigPath() != null) {
            v.put("path", i.loggingConfigPath().toString());
        }
        return Map.copyOf(v);
    }

    private List<String> collectArgs(JSONObject meta, String type) {
        List<String> args = new ArrayList<>();
        if (meta.has("arguments")) {
            JSONArray arr = meta.getJSONObject("arguments").optJSONArray(type);
            if (arr == null) return args;
            for (int i = 0; i < arr.length(); i++) {
                Object entry = arr.get(i);
                if (entry instanceof String s) {
                    args.add(s);
                    continue;
                }
                if (!(entry instanceof JSONObject jo)) continue;
                if (!isAllowedByRules(jo.optJSONArray("rules"))) continue;
                Object val = jo.get("value");
                if (val instanceof String sv) {
                    args.add(sv);
                    continue;
                }
                if (val instanceof JSONArray va) {
                    for (int j = 0; j < va.length(); j++) {
                        args.add(va.getString(j));
                    }
                }
            }
            return args;
        }
        // Legacy minecraftArguments (pre-1.13)
        String legacy = meta.optString("minecraftArguments", "");
        if (!legacy.isBlank() && "game".equals(type)) {
            for (String part : legacy.split(" ")) {
                if (!part.isBlank()) args.add(part.trim());
            }
        }
        return args;
    }

    private boolean isAllowedByRules(JSONArray rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            // Features (z.B. is_demo_user, has_custom_resolution) überspringen
            if (rule.has("features") && !rule.getJSONObject("features").isEmpty()) continue;
            // OS-Regel prüfen
            if (rule.has("os") && !osMatches(rule.getJSONObject("os"))) continue;
            allowed = "allow".equals(rule.optString("action", "allow"));
        }
        return allowed;
    }

    /**
     * Prüft ob eine OS-Regel auf das aktuelle System zutrifft.
     * Ohne diese Prüfung landen macOS-only Flags wie -XstartOnFirstThread
     * auch auf Linux/Windows im Launch-Command → JVM-Fehler.
     */
    private boolean osMatches(JSONObject os) {
        String osName    = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch    = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osVersion = System.getProperty("os.version", "");

        String expectedName = os.optString("name", "");
        if (!expectedName.isBlank()) {
            String currentOs = currentOsName(osName);
            if (!expectedName.equals(currentOs)) return false;
        }

        String expectedArch = os.optString("arch", "");
        if (!expectedArch.isBlank() && !osArch.contains(expectedArch.toLowerCase(Locale.ROOT))) {
            return false;
        }

        String expectedVersion = os.optString("version", "");
        if (!expectedVersion.isBlank()) {
            try {
                if (!osVersion.matches(expectedVersion)) return false;
            } catch (Exception ignored) {}
        }

        return true;
    }

    private String currentOsName(String osName) {
        if (osName.contains("win"))                          return "windows";
        if (osName.contains("mac") || osName.contains("darwin")) return "osx";
        return "linux";
    }

    private String apply(String input, Map<String, String> vars) {
        String result = input;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}