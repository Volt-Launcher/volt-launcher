package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.launcher.install.AssetInstaller;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceSettings;
import app.voltlauncher.voltlauncher.launcher.java.JavaRuntimeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

public final class LaunchCommandBuilder {

    private final String launcherClientId;

    public LaunchCommandBuilder(String launcherClientId) {
        this.launcherClientId = launcherClientId;
    }

    public List < String> build( MinecraftAccountSession session, AssetInstaller.Installation install,
    JavaRuntimeResolver.JavaRuntime runtime) {

        InstanceSettings settings = install.instance().settings();
        Map < String, String> vars = buildVars(session, install, settings);
        JSONObject meta = install.launchMetadata();
        boolean isLegacy = !meta.has("arguments") && meta.has("minecraftArguments");

        int maxMemoryMb = settings.maxMemoryMb() != null ? settings.maxMemoryMb() : 2048;
        Integer minMemoryMb = settings.minMemoryMb();

        List < String> cmd = new ArrayList <> ();
        cmd.add(runtime.javaExecutable().toString());

        if (isLegacy) {
            cmd.add("-Xmx" + maxMemoryMb + "M");
            cmd.add("-Xms" + (minMemoryMb != null ? minMemoryMb : 512) + "M");
            cmd.add("-Djava.library.path=" + install.nativesDirectory().toAbsolutePath());
            cmd.add("-Dminecraft.launcher.brand=TheLauncherProject");
            cmd.add("-Dminecraft.launcher.version=0.1.0");
            cmd.add("-cp");
            cmd.add(install.classpath());
        } else {
            // Launcher controls heap sizing — drop any -Xmx/-Xms supplied by the version metadata.
            cmd.add("-Xmx" + maxMemoryMb + "M");
            if (minMemoryMb != null) cmd.add("-Xms" + minMemoryMb + "M");
            for (String arg : collectArgs(meta, "jvm")) {
                if (arg.startsWith("-Xmx") || arg.startsWith("-Xms")) continue;
                cmd.add(apply(arg, vars));
            }
        }

        for (String arg : extraJvmArgs(settings)) {
            cmd.add(apply(arg, vars));
        }

        cmd.add(install.mainClass());

        for (String arg : collectArgs(meta, "game")) {
            cmd.add(apply(arg, vars));
        }

        return List.copyOf(cmd);
    }

    private List < String> extraJvmArgs(InstanceSettings settings) {
        List < String> args = new ArrayList <> ();
        String custom = settings.jvmArgs();
        if (custom == null || custom.isBlank()) return args;
        for (String part : custom.trim().split("\\s+")) {
            if (!part.isBlank()) args.add(part);
        }
        return args;
    }

    private Map < String, String> buildVars(MinecraftAccountSession s, AssetInstaller.Installation i, InstanceSettings settings) {
        Map < String, String> v = new HashMap <> ();
        v.put("auth_player_name", s.username());
        v.put("version_name", i.launchVersionId());
        v.put("game_directory", i.instance().gameDirectory().toString());
        v.put("assets_root", i.assetsDirectory().toString());
        v.put("assets_index_name", i.assetIndexId());
        v.put("auth_uuid", s.uuid());
        v.put("auth_access_token", s.minecraftAccessToken());
        v.put("auth_session", s.minecraftAccessToken());
        v.put("clientid", launcherClientId);
        v.put("auth_xuid", s.xuid() == null ? "" : s.xuid());
        v.put("user_type", "msa");
        v.put("version_type", i.versionType());
        v.put("user_properties", "{}");
        v.put("natives_directory", i.nativesDirectory().toString());
        v.put("launcher_name", "VoltLauncher");
        v.put("launcher_version", "0.1.0");
        v.put("classpath", i.classpath());
        v.put("classpath_separator", File.pathSeparator);
        v.put("library_directory", i.librariesDirectory().toString());
        v.put("resolution_width", String.valueOf(settings.resolutionWidth() != null ? settings.resolutionWidth() : 1280));
        v.put("resolution_height", String.valueOf(settings.resolutionHeight() != null ? settings.resolutionHeight() : 720));
        v.put("game_assets", i.assetsDirectory().resolve("virtual").resolve(i.assetIndexId()).toString());
        if (i.loggingConfigPath() != null) {
            v.put("path", i.loggingConfigPath().toString());
        }
        return Map.copyOf(v);
    }

    private List < String> collectArgs(JSONObject meta, String type) {
        List < String> args = new ArrayList <> ();
        if (meta.has("arguments")) {
            JSONArray arr = meta.getJSONObject("arguments").optJSONArray(type);
            if (arr == null) return args;
            for (int i = 0; i < arr.length(); i++) {
                Object entry = arr.get(i);
                if (entry instanceof String s) {
                    args.add(s);
                    continue;
                }
                if (!(entry instanceof JSONObject jo)) { continue; }
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
        if ("game".equals(type)) {
            String legacy = meta.optString("minecraftArguments", "");
            for (String part : legacy.split(" ")) {
                if (!part.isBlank()) { args.add(part.trim()); }
            }
        }
        return args;
    }

    private boolean isAllowedByRules(JSONArray rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (rule.has("features") && !rule.getJSONObject("features").isEmpty()) { continue; }
            if (rule.has("os") && !osMatches(rule.getJSONObject("os"))) continue;
            allowed = "allow".equals(rule.optString("action", "allow"));
        }
        return allowed;
    }

    private boolean osMatches(JSONObject os) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osVersion = System.getProperty("os.version", "");

        String expectedName = os.optString("name", "");
        if (!expectedName.isBlank() && !expectedName.equals(currentOsName(osName))) return false;

        String expectedArch = os.optString("arch", "");
        if (!expectedArch.isBlank() && !osArch.contains(expectedArch.toLowerCase(Locale.ROOT))) return false;

        String expectedVersion = os.optString("version", "");
        if (!expectedVersion.isBlank()) {
            try { if (!osVersion.matches(expectedVersion)) return false; }
            catch (Exception ignored) {}
        }
        return true;
    }

    private String currentOsName(String osName) {
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac") || osName.contains("darwin")) return "osx";
        return "linux";
    }

    private String apply(String input, Map < String, String> vars) {
        String result = input;
        for (Map.Entry < String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}
