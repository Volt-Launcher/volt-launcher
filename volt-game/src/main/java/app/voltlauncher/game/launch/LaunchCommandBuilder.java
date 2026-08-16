package app.voltlauncher.game.launch;

import app.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.game.install.AssetInstaller;
import app.voltlauncher.game.install.OsRules;
import app.voltlauncher.game.instance.InstanceSettings;
import app.voltlauncher.game.java.JavaRuntimeResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LaunchCommandBuilder {

    private static final int DEFAULT_MAX_MEMORY_MB = 2048;
    private static final int DEFAULT_MIN_MEMORY_MB = 512;
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;
    private static final String LAUNCHER_NAME = "VoltLauncher";

    private final String launcherClientId;
    private final String launcherVersion;

    public LaunchCommandBuilder(String launcherClientId) {
        this(launcherClientId, defaultVersion());
    }

    public LaunchCommandBuilder(String launcherClientId, String launcherVersion) {
        this.launcherClientId = launcherClientId;
        this.launcherVersion = launcherVersion;
    }

    public List<String> build(MinecraftAccountSession session, AssetInstaller.Installation install,
                              JavaRuntimeResolver.JavaRuntime runtime) {

        InstanceSettings settings = install.instance().settings();
        Map<String, String> vars = buildVars(session, install, settings);
        Map<String, Boolean> features = buildFeatures(settings);
        JSONObject meta = install.launchMetadata();
        boolean legacy = !meta.has("arguments") && meta.has("minecraftArguments");

        int maxMemoryMb = settings.maxMemoryMb() != null ? settings.maxMemoryMb() : DEFAULT_MAX_MEMORY_MB;
        int minMemoryMb = settings.minMemoryMb() != null ? settings.minMemoryMb() : DEFAULT_MIN_MEMORY_MB;

        List<String> command = new ArrayList<>();
        command.add(runtime.javaExecutable().toString());

        // Heap sizing is owned by the launcher, so any -Xmx/-Xms coming from version metadata
        // or the user's extra arguments is dropped in favour of the instance settings.
        command.add("-Xmx" + maxMemoryMb + "M");
        command.add("-Xms" + Math.min(minMemoryMb, maxMemoryMb) + "M");

        if (legacy) {
            command.add("-Djava.library.path=" + install.nativesDirectory().toAbsolutePath());
            command.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME);
            command.add("-Dminecraft.launcher.version=" + launcherVersion);
            command.add("-cp");
            command.add(install.classpath());
        } else {
            for (String arg : collectArgs(meta, "jvm", features)) {
                if (isHeapArgument(arg)) continue;
                command.add(apply(arg, vars));
            }
        }

        for (String arg : extraJvmArgs(settings)) {
            if (isHeapArgument(arg)) continue;
            command.add(apply(arg, vars));
        }

        command.add(install.mainClass());

        for (String arg : collectArgs(meta, "game", features)) {
            command.add(apply(arg, vars));
        }

        return List.copyOf(command);
    }

    private static boolean isHeapArgument(String arg) {
        return arg.startsWith("-Xmx") || arg.startsWith("-Xms");
    }

    /**
     * Launcher features the version metadata can gate arguments on. Declaring a custom
     * resolution is what makes Mojang's metadata emit {@code --width}/{@code --height}.
     */
    private Map<String, Boolean> buildFeatures(InstanceSettings settings) {
        boolean customResolution = settings.resolutionWidth() != null && settings.resolutionHeight() != null;
        return Map.of(
                "has_custom_resolution", customResolution,
                "is_demo_user", false,
                "has_quick_plays_support", false,
                "is_quick_play_singleplayer", false,
                "is_quick_play_multiplayer", false,
                "is_quick_play_realms", false);
    }

    private List<String> extraJvmArgs(InstanceSettings settings) {
        List<String> args = new ArrayList<>();
        String custom = settings.jvmArgs();
        if (custom == null || custom.isBlank()) return args;
        for (String part : custom.trim().split("\\s+")) {
            if (!part.isBlank()) args.add(part);
        }
        return args;
    }

    private Map<String, String> buildVars(MinecraftAccountSession session, AssetInstaller.Installation install,
                                          InstanceSettings settings) {
        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", session.username());
        vars.put("version_name", install.launchVersionId());
        vars.put("game_directory", install.instance().gameDirectory().toString());
        vars.put("assets_root", install.assetsDirectory().toString());
        vars.put("assets_index_name", install.assetIndexId());
        vars.put("auth_uuid", session.uuid());
        vars.put("auth_access_token", session.minecraftAccessToken());
        vars.put("auth_session", "token:" + session.minecraftAccessToken() + ":" + session.uuid());
        vars.put("clientid", launcherClientId);
        vars.put("auth_xuid", session.xuid() == null ? "" : session.xuid());
        vars.put("user_type", "msa");
        vars.put("version_type", install.versionType());
        vars.put("user_properties", "{}");
        vars.put("natives_directory", install.nativesDirectory().toString());
        vars.put("launcher_name", LAUNCHER_NAME);
        vars.put("launcher_version", launcherVersion);
        vars.put("classpath", install.classpath());
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", install.librariesDirectory().toString());
        vars.put("resolution_width", String.valueOf(
                settings.resolutionWidth() != null ? settings.resolutionWidth() : DEFAULT_WIDTH));
        vars.put("resolution_height", String.valueOf(
                settings.resolutionHeight() != null ? settings.resolutionHeight() : DEFAULT_HEIGHT));
        vars.put("game_assets", install.assetsDirectory().resolve("virtual")
                .resolve(install.assetIndexId()).toString());
        if (install.loggingConfigPath() != null) {
            vars.put("path", install.loggingConfigPath().toString());
        }
        return Map.copyOf(vars);
    }

    private List<String> collectArgs(JSONObject meta, String type, Map<String, Boolean> features) {
        List<String> args = new ArrayList<>();
        if (meta.has("arguments")) {
            JSONArray arr = meta.getJSONObject("arguments").optJSONArray(type);
            if (arr == null) return args;
            for (int i = 0; i < arr.length(); i++) {
                Object entry = arr.get(i);
                if (entry instanceof String value) {
                    args.add(value);
                    continue;
                }
                if (!(entry instanceof JSONObject conditional)) continue;
                if (!OsRules.isAllowedByRules(conditional.optJSONArray("rules"), features)) continue;

                Object value = conditional.opt("value");
                if (value instanceof String single) {
                    args.add(single);
                } else if (value instanceof JSONArray multiple) {
                    for (int j = 0; j < multiple.length(); j++) {
                        args.add(multiple.getString(j));
                    }
                }
            }
            return args;
        }

        if ("game".equals(type)) {
            for (String part : meta.optString("minecraftArguments", "").split(" ")) {
                if (!part.isBlank()) args.add(part.trim());
            }
        }
        return args;
    }

    /** Substitutes {@code ${placeholder}} tokens in a single pass so values cannot re-expand. */
    private String apply(String input, Map<String, String> vars) {
        if (input.indexOf('$') < 0) return input;

        StringBuilder out = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            int start = input.indexOf("${", index);
            if (start < 0) {
                out.append(input, index, input.length());
                break;
            }
            int end = input.indexOf('}', start + 2);
            if (end < 0) {
                out.append(input, index, input.length());
                break;
            }
            out.append(input, index, start);
            String key = input.substring(start + 2, end);
            String replacement = vars.get(key);
            out.append(replacement != null ? replacement : input.substring(start, end + 1));
            index = end + 1;
        }
        return out.toString();
    }

    private static String defaultVersion() {
        String implementation = LaunchCommandBuilder.class.getPackage().getImplementationVersion();
        return implementation != null ? implementation : "0.2.0";
    }
}
