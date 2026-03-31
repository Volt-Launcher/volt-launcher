package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;
import de.eztxm.thelauncherproject.auth.MinecraftAccountSession;
import de.eztxm.thelauncherproject.storage.LauncherInstanceStore;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MinecraftLauncherService {

    public static final String DEFAULT_VERSION = "1.21.1";

    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    public record LaunchResult(
            String instanceName,
            String version,
            long pid,
            String command,
            String logFile,
            int javaMajorVersion,
            String javaExecutable) {
    }

    public record AvailableVersion(String id, String type, String releaseTime) {
    }

    public record RunningInstanceStatus(
            String instanceName,
            String versionId,
            long pid,
            long startedAt,
            boolean running,
            int javaMajorVersion,
            String javaExecutable) {
    }

    private record VersionManifestEntry(String id, String type, String url, String releaseTime) {
    }

    private record OsDetails(String name, String archBits, String javaExecutableName) {
    }

    private record RequiredJava(int majorVersion, String component) {
    }

    private record Installation(
            LauncherInstance instance,
            String launchVersionId,
            JSONObject launchMetadata,
            Path librariesDirectory,
            Path assetsDirectory,
            Path nativesDirectory,
            String classpath,
            String mainClass,
            Path loggingConfigPath,
            String assetIndexId,
            String versionType) {
    }

    private record RunningInstance(
            LauncherInstance instance,
            ProcessHandle handle,
            long startedAt,
            JavaRuntimeResolver.JavaRuntime runtime) {
    }

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ConcurrentHashMap<String, RunningInstance> runningInstances = new ConcurrentHashMap<>();
    private final LauncherInstanceStore instanceStore = new LauncherInstanceStore();
    private final JavaRuntimeResolver javaRuntimeResolver = new JavaRuntimeResolver();
    private final String launcherClientId;

    public MinecraftLauncherService(String launcherClientId) {
        this.launcherClientId = launcherClientId;
    }

    public List<LauncherInstance> listInstances() throws Exception {
        return upgradeInstancesWithJavaRequirements(instanceStore.listInstances());
    }

    public LauncherInstance createInstance(String name, String versionId) throws Exception {
        String normalizedName = normalizeInstanceName(name);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Instance name is required");
        }
        if (normalizedName.length() > 64) {
            throw new IllegalArgumentException("Instance name must be 64 characters or shorter");
        }
        if (containsUnsupportedInstanceNameCharacter(normalizedName)) {
            throw new IllegalArgumentException(
                    "Instance name contains unsupported characters: / \\ : * ? \" < > | or control characters");
        }

        VersionManifestEntry version = findManifestEntry(versionId);
        JSONObject versionMetadata = resolveVersionMetadata(version.id());
        RequiredJava requiredJava = resolveRequiredJava(versionMetadata, version.id());

        List<LauncherInstance> instances = listInstances();
        if (instances.stream().anyMatch(instance -> instance.name().equalsIgnoreCase(normalizedName))) {
            throw new IllegalStateException("An instance with this name already exists");
        }

        String slug = createUniqueSlug(normalizedName, instances);
        long now = System.currentTimeMillis();
        LauncherInstance instance = new LauncherInstance(
                normalizedName,
                slug,
                version.id(),
                version.type(),
                now,
                0L,
                requiredJava.majorVersion(),
                requiredJava.component());

        Files.createDirectories(instance.gameDirectory());
        instances.add(instance);
        instanceStore.saveInstances(instances);
        return instance;
    }

    public List<AvailableVersion> listVersions(boolean includeSnapshots, boolean includeBetas, boolean includeAlphas)
            throws Exception {
        List<AvailableVersion> result = new ArrayList<>();
        for (VersionManifestEntry entry : loadManifestEntries()) {
            if (!shouldIncludeVersionType(entry.type(), includeSnapshots, includeBetas, includeAlphas)) {
                continue;
            }
            result.add(new AvailableVersion(entry.id(), entry.type(), entry.releaseTime()));
        }
        return result;
    }

    public LaunchResult launchInstance(MinecraftAccountSession session, String instanceName) throws Exception {
        cleanupExitedProcesses();

        LauncherInstance instance = findInstanceByName(instanceName);
        String key = instanceKey(instance.name());
        RunningInstance alreadyRunning = runningInstances.get(key);
        if (alreadyRunning != null && alreadyRunning.handle().isAlive()) {
            throw new IllegalStateException("This instance is already running");
        }

        JSONObject versionMetadata = resolveVersionMetadata(instance.versionId());
        Installation installation = ensureInstallation(instance, versionMetadata);
        RequiredJava requiredJava = resolveRequiredJava(versionMetadata, installation.launchVersionId());
        JavaRuntimeResolver.JavaRuntime runtime = javaRuntimeResolver.resolveRuntime(requiredJava.majorVersion());
        List<String> command = buildCommand(session, installation, runtime);

        Path logFile = AppPaths.logsDirectory().resolve(instance.slug() + "-" + installation.launchVersionId() + ".log");
        Files.createDirectories(logFile.getParent());
        Files.createDirectories(instance.gameDirectory());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(instance.gameDirectory().toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        ProcessHandle handle = processBuilder.start().toHandle();
        long startedAt = System.currentTimeMillis();
        RunningInstance runningInstance = new RunningInstance(instance, handle, startedAt, runtime);
        runningInstances.put(key, runningInstance);
        handle.onExit().thenRun(() -> runningInstances.remove(key, runningInstance));
        markInstancePlayed(instance, startedAt);

        return new LaunchResult(
                instance.name(),
                installation.launchVersionId(),
                handle.pid(),
                String.join(" ", command),
                logFile.toString(),
                runtime.majorVersion(),
                runtime.javaExecutable().toString());
    }

    public RunningInstanceStatus getRunningInstanceStatus(String instanceName) {
        cleanupExitedProcesses();
        RunningInstance runningInstance = runningInstances.get(instanceKey(instanceName));
        if (runningInstance == null) {
            return null;
        }
        return toRunningStatus(runningInstance);
    }

    public boolean stopInstance(String instanceName) throws Exception {
        cleanupExitedProcesses();
        String key = instanceKey(instanceName);
        RunningInstance runningInstance = runningInstances.get(key);
        if (runningInstance == null || !runningInstance.handle().isAlive()) {
            runningInstances.remove(key);
            throw new IllegalStateException("This instance is not running");
        }

        runningInstance.handle().destroy();
        waitForExit(runningInstance.handle(), 5);
        if (runningInstance.handle().isAlive()) {
            runningInstance.handle().destroyForcibly();
            waitForExit(runningInstance.handle(), 5);
        }

        runningInstances.remove(key, runningInstance);
        return !runningInstance.handle().isAlive();
    }

    public List<RunningInstanceStatus> listRunningInstances() {
        cleanupExitedProcesses();
        List<RunningInstanceStatus> statuses = new ArrayList<>();
        for (RunningInstance runningInstance : runningInstances.values()) {
            statuses.add(toRunningStatus(runningInstance));
        }
        statuses.sort(Comparator.comparing(RunningInstanceStatus::instanceName, String.CASE_INSENSITIVE_ORDER));
        return statuses;
    }

    public void stopAllRunningInstances() {
        cleanupExitedProcesses();
        List<String> instanceNames = new ArrayList<>(runningInstances.keySet());
        for (String key : instanceNames) {
            RunningInstance runningInstance = runningInstances.get(key);
            if (runningInstance == null) {
                continue;
            }

            try {
                stopInstance(runningInstance.instance().name());
            } catch (Exception ignored) {
            }
        }
    }

    private Installation ensureInstallation(LauncherInstance instance, JSONObject versionMetadata) throws Exception {
        String launchVersionId = instance.versionId();
        String clientVersionId = versionMetadata.optString("jar", launchVersionId);
        JSONObject clientMetadata = clientVersionId.equals(launchVersionId)
                ? versionMetadata
                : resolveVersionMetadata(clientVersionId);

        Path sharedMinecraftDirectory = AppPaths.minecraftDirectory();
        Path versionsDirectory = sharedMinecraftDirectory.resolve("versions");
        Path librariesDirectory = sharedMinecraftDirectory.resolve("libraries");
        Path assetsDirectory = sharedMinecraftDirectory.resolve("assets");
        Path nativesDirectory = sharedMinecraftDirectory.resolve("natives").resolve(instance.slug()).resolve(launchVersionId);

        Files.createDirectories(versionsDirectory.resolve(launchVersionId));
        Files.createDirectories(versionsDirectory.resolve(clientVersionId));
        Files.createDirectories(librariesDirectory);
        Files.createDirectories(assetsDirectory.resolve("indexes"));
        Files.createDirectories(assetsDirectory.resolve("objects"));
        Files.createDirectories(instance.gameDirectory());
        recreateDirectory(nativesDirectory);

        JSONObject clientDownload = clientMetadata.getJSONObject("downloads").getJSONObject("client");
        Path clientJar = versionsDirectory.resolve(clientVersionId).resolve(clientVersionId + ".jar");
        downloadFileIfNeeded(clientDownload.getString("url"), clientJar, clientDownload.optString("sha1", ""));

        String assetIndexId = versionMetadata.optString("assets", "legacy");
        if (versionMetadata.has("assetIndex")) {
            JSONObject assetIndex = versionMetadata.getJSONObject("assetIndex");
            assetIndexId = assetIndex.getString("id");
            Path assetIndexPath = assetsDirectory.resolve("indexes").resolve(assetIndexId + ".json");
            downloadFileIfNeeded(assetIndex.getString("url"), assetIndexPath, assetIndex.optString("sha1", ""));
            downloadAssets(assetIndexPath, assetsDirectory.resolve("objects"));
        }

        Path loggingConfigPath = null;
        if (versionMetadata.has("logging")) {
            JSONObject logging = versionMetadata.getJSONObject("logging");
            if (logging.has("client")) {
                JSONObject clientLogging = logging.getJSONObject("client");
                JSONObject file = clientLogging.getJSONObject("file");
                loggingConfigPath = assetsDirectory.resolve("log_configs").resolve(file.getString("id"));
                downloadFileIfNeeded(file.getString("url"), loggingConfigPath, file.optString("sha1", ""));
            }
        }

        LinkedHashSet<String> classpathEntries = new LinkedHashSet<>();
        JSONArray libraries = versionMetadata.optJSONArray("libraries");
        if (libraries != null) {
            for (int i = 0; i < libraries.length(); i++) {
                JSONObject library = libraries.getJSONObject(i);
                if (!isAllowedByRules(library.optJSONArray("rules"))) {
                    continue;
                }

                JSONObject downloadsJson = library.optJSONObject("downloads");
                if (downloadsJson == null) {
                    continue;
                }

                JSONObject artifact = downloadsJson.optJSONObject("artifact");
                if (artifact != null) {
                    Path artifactPath = librariesDirectory.resolve(artifact.getString("path"));
                    downloadFileIfNeeded(artifact.getString("url"), artifactPath, artifact.optString("sha1", ""));
                    classpathEntries.add(artifactPath.toString());
                }

                JSONObject classifiers = downloadsJson.optJSONObject("classifiers");
                if (classifiers != null && library.has("natives")) {
                    String classifierName = resolveNativeClassifier(library.getJSONObject("natives"));
                    if (classifierName != null && classifiers.has(classifierName)) {
                        JSONObject nativeDownload = classifiers.getJSONObject(classifierName);
                        Path nativeArchive = librariesDirectory.resolve(nativeDownload.getString("path"));
                        downloadFileIfNeeded(
                                nativeDownload.getString("url"),
                                nativeArchive,
                                nativeDownload.optString("sha1", ""));
                        extractNative(nativeArchive, nativesDirectory, library.optJSONObject("extract"));
                    }
                }
            }
        }

        classpathEntries.add(clientJar.toString());

        return new Installation(
                instance,
                launchVersionId,
                versionMetadata,
                librariesDirectory,
                assetsDirectory,
                nativesDirectory,
                String.join(File.pathSeparator, classpathEntries),
                versionMetadata.getString("mainClass"),
                loggingConfigPath,
                assetIndexId,
                instance.versionType());
    }

    private List<String> buildCommand(
            MinecraftAccountSession session,
            Installation installation,
            JavaRuntimeResolver.JavaRuntime runtime) {
        JSONObject versionMetadata = installation.launchMetadata();
        Map<String, String> variables = new HashMap<>();
        variables.put("auth_player_name", session.username());
        variables.put("version_name", installation.launchVersionId());
        variables.put("game_directory", installation.instance().gameDirectory().toString());
        variables.put("assets_root", installation.assetsDirectory().toString());
        variables.put("assets_index_name", installation.assetIndexId());
        variables.put("auth_uuid", session.uuid());
        variables.put("auth_access_token", session.minecraftAccessToken());
        variables.put("auth_session", session.minecraftAccessToken());
        variables.put("clientid", launcherClientId);
        variables.put("auth_xuid", session.xuid() == null ? "" : session.xuid());
        variables.put("user_type", "msa");
        variables.put("version_type", installation.versionType());
        variables.put("user_properties", "{}");
        variables.put("natives_directory", installation.nativesDirectory().toString());
        variables.put("launcher_name", "TheLauncherProject");
        variables.put("launcher_version", "0.1.0");
        variables.put("classpath", installation.classpath());
        variables.put("classpath_separator", File.pathSeparator);
        variables.put("library_directory", installation.librariesDirectory().toString());
        variables.put("resolution_width", "1280");
        variables.put("resolution_height", "720");
        variables.put("game_assets", installation.assetIndexId());

        if (installation.loggingConfigPath() != null) {
            variables.put("path", installation.loggingConfigPath().toString());
        }

        List<String> command = new ArrayList<>();
        command.add(runtime.javaExecutable().toString());

        List<String> jvmArgs = collectArguments(versionMetadata, "jvm");
        if (jvmArgs.stream().noneMatch(arg -> arg.startsWith("-Xmx"))) {
            command.add("-Xmx2G");
        }
        for (String jvmArg : jvmArgs) {
            command.add(applyVariables(jvmArg, variables));
        }

        command.add(installation.mainClass());
        for (String gameArg : collectArguments(versionMetadata, "game")) {
            command.add(applyVariables(gameArg, variables));
        }

        return command;
    }

    private List<String> collectArguments(JSONObject versionMetadata, String type) {
        List<String> arguments = new ArrayList<>();

        if (versionMetadata.has("arguments")) {
            JSONArray array = versionMetadata.getJSONObject("arguments").optJSONArray(type);
            if (array == null) {
                return arguments;
            }

            for (int i = 0; i < array.length(); i++) {
                Object entry = array.get(i);
                if (entry instanceof String value) {
                    arguments.add(value);
                    continue;
                }

                if (entry instanceof JSONObject jsonObject && isAllowedByRules(jsonObject.optJSONArray("rules"))) {
                    Object value = jsonObject.get("value");
                    if (value instanceof String stringValue) {
                        arguments.add(stringValue);
                    } else if (value instanceof JSONArray valuesArray) {
                        for (int j = 0; j < valuesArray.length(); j++) {
                            arguments.add(valuesArray.getString(j));
                        }
                    }
                }
            }

            return arguments;
        }

        String legacyArgs = versionMetadata.optString("minecraftArguments", "");
        if (!legacyArgs.isBlank() && "game".equals(type)) {
            for (String arg : legacyArgs.split(" ")) {
                if (!arg.isBlank()) {
                    arguments.add(arg.trim());
                }
            }
        }
        return arguments;
    }

    private LauncherInstance findInstanceByName(String name) throws Exception {
        return listInstances().stream()
                .filter(instance -> instance.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Instance not found: " + name));
    }

    private void markInstancePlayed(LauncherInstance launchedInstance, long startedAt) throws Exception {
        List<LauncherInstance> instances = listInstances();
        List<LauncherInstance> updated = new ArrayList<>(instances.size());
        for (LauncherInstance instance : instances) {
            if (instance.name().equalsIgnoreCase(launchedInstance.name())) {
                updated.add(new LauncherInstance(
                        instance.name(),
                        instance.slug(),
                        instance.versionId(),
                        instance.versionType(),
                        instance.createdAt(),
                        startedAt,
                        instance.javaMajorVersion(),
                        instance.javaComponent()));
            } else {
                updated.add(instance);
            }
        }
        instanceStore.saveInstances(updated);
    }

    private List<LauncherInstance> upgradeInstancesWithJavaRequirements(List<LauncherInstance> instances) throws Exception {
        boolean changed = false;
        List<LauncherInstance> upgraded = new ArrayList<>(instances.size());
        for (LauncherInstance instance : instances) {
            LauncherInstance upgradedInstance = ensureJavaRequirement(instance);
            upgraded.add(upgradedInstance);
            if (!upgradedInstance.equals(instance)) {
                changed = true;
            }
        }

        if (changed) {
            instanceStore.saveInstances(upgraded);
        }
        return upgraded;
    }

    private LauncherInstance ensureJavaRequirement(LauncherInstance instance) throws Exception {
        if (instance.hasJavaRequirement()) {
            return instance;
        }

        JSONObject versionMetadata = resolveVersionMetadata(instance.versionId());
        RequiredJava requiredJava = resolveRequiredJava(versionMetadata, instance.versionId());
        return new LauncherInstance(
                instance.name(),
                instance.slug(),
                instance.versionId(),
                instance.versionType(),
                instance.createdAt(),
                instance.lastPlayedAt(),
                requiredJava.majorVersion(),
                requiredJava.component());
    }

    private RequiredJava resolveRequiredJava(JSONObject versionMetadata, String versionId) {
        JSONObject javaVersion = versionMetadata.optJSONObject("javaVersion");
        if (javaVersion != null) {
            int majorVersion = javaVersion.optInt("majorVersion", 0);
            if (majorVersion > 0) {
                return new RequiredJava(majorVersion, javaVersion.optString("component", ""));
            }
        }

        return new RequiredJava(guessLegacyJavaMajor(versionId), "legacy-runtime");
    }

    private int guessLegacyJavaMajor(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return 8;
        }

        if (versionId.startsWith("1.21.6")
                || versionId.startsWith("1.21.7")
                || versionId.startsWith("1.21.8")
                || versionId.startsWith("1.21.9")
                || versionId.startsWith("1.21.10")
                || versionId.startsWith("1.21.11")
                || versionId.startsWith("1.21.5")
                || versionId.startsWith("25w")
                || versionId.startsWith("26.")) {
            return 21;
        }
        if (versionId.startsWith("1.18")
                || versionId.startsWith("1.19")
                || versionId.startsWith("1.20")
                || versionId.startsWith("24w")) {
            return 17;
        }
        if (versionId.startsWith("1.17") || versionId.startsWith("21w")) {
            return 16;
        }
        return 8;
    }

    private RunningInstanceStatus toRunningStatus(RunningInstance runningInstance) {
        return new RunningInstanceStatus(
                runningInstance.instance().name(),
                runningInstance.instance().versionId(),
                runningInstance.handle().pid(),
                runningInstance.startedAt(),
                runningInstance.handle().isAlive(),
                runningInstance.runtime().majorVersion(),
                runningInstance.runtime().javaExecutable().toString());
    }

    private void cleanupExitedProcesses() {
        runningInstances.entrySet().removeIf(entry -> !entry.getValue().handle().isAlive());
    }

    private void waitForExit(ProcessHandle handle, long timeoutSeconds) {
        try {
            handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private boolean shouldIncludeVersionType(
            String type,
            boolean includeSnapshots,
            boolean includeBetas,
            boolean includeAlphas) {
        return switch (type) {
            case "release" -> true;
            case "snapshot" -> includeSnapshots;
            case "old_beta" -> includeBetas;
            case "old_alpha" -> includeAlphas;
            default -> false;
        };
    }

    private List<VersionManifestEntry> loadManifestEntries() throws Exception {
        JSONObject manifest = getJson(VERSION_MANIFEST_URL);
        JSONArray versions = manifest.getJSONArray("versions");
        List<VersionManifestEntry> result = new ArrayList<>(versions.length());
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            result.add(new VersionManifestEntry(
                    version.getString("id"),
                    version.getString("type"),
                    version.getString("url"),
                    version.optString("releaseTime", version.optString("time", ""))));
        }
        return result;
    }

    private VersionManifestEntry findManifestEntry(String versionId) throws Exception {
        return loadManifestEntries().stream()
                .filter(entry -> entry.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Minecraft version not found: " + versionId));
    }

    private JSONObject resolveVersionMetadata(String versionId) throws Exception {
        return resolveVersionMetadata(versionId, new HashSet<>());
    }

    private JSONObject resolveVersionMetadata(String versionId, Set<String> visited) throws Exception {
        if (!visited.add(versionId)) {
            throw new IllegalStateException("Circular version inheritance detected for " + versionId);
        }

        JSONObject raw = getJson(findManifestEntry(versionId).url());
        if (!raw.has("inheritsFrom")) {
            return raw;
        }

        JSONObject parent = resolveVersionMetadata(raw.getString("inheritsFrom"), visited);
        return mergeVersionMetadata(parent, raw);
    }

    private JSONObject mergeVersionMetadata(JSONObject parent, JSONObject child) {
        JSONObject merged = new JSONObject(parent.toString());

        if (parent.has("libraries") || child.has("libraries")) {
            JSONArray libraries = new JSONArray();
            appendArray(libraries, parent.optJSONArray("libraries"));
            appendArray(libraries, child.optJSONArray("libraries"));
            merged.put("libraries", libraries);
        }

        if (parent.has("arguments") || child.has("arguments")) {
            JSONObject arguments = new JSONObject();
            mergeArgumentArrays(arguments, parent.optJSONObject("arguments"), child.optJSONObject("arguments"), "game");
            mergeArgumentArrays(arguments, parent.optJSONObject("arguments"), child.optJSONObject("arguments"), "jvm");
            if (!arguments.isEmpty()) {
                merged.put("arguments", arguments);
            }
        }

        for (String key : child.keySet()) {
            if (Objects.equals(key, "inheritsFrom")
                    || Objects.equals(key, "libraries")
                    || Objects.equals(key, "arguments")) {
                continue;
            }

            Object childValue = child.get(key);
            if (childValue instanceof JSONObject childObject && merged.opt(key) instanceof JSONObject parentObject) {
                merged.put(key, mergeJsonObjects(parentObject, childObject));
            } else {
                merged.put(key, deepCopyValue(childValue));
            }
        }

        merged.put("id", child.getString("id"));
        return merged;
    }

    private void mergeArgumentArrays(
            JSONObject target,
            JSONObject parentArguments,
            JSONObject childArguments,
            String key) {
        JSONArray mergedArray = new JSONArray();
        appendArray(mergedArray, parentArguments != null ? parentArguments.optJSONArray(key) : null);
        appendArray(mergedArray, childArguments != null ? childArguments.optJSONArray(key) : null);
        if (!mergedArray.isEmpty()) {
            target.put(key, mergedArray);
        }
    }

    private JSONObject mergeJsonObjects(JSONObject parent, JSONObject child) {
        JSONObject merged = new JSONObject(parent.toString());
        for (String key : child.keySet()) {
            Object childValue = child.get(key);
            if (childValue instanceof JSONObject childObject && merged.opt(key) instanceof JSONObject parentObject) {
                merged.put(key, mergeJsonObjects(parentObject, childObject));
            } else {
                merged.put(key, deepCopyValue(childValue));
            }
        }
        return merged;
    }

    private void appendArray(JSONArray target, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            target.put(deepCopyValue(source.get(i)));
        }
    }

    private Object deepCopyValue(Object value) {
        if (value instanceof JSONObject jsonObject) {
            return new JSONObject(jsonObject.toString());
        }
        if (value instanceof JSONArray jsonArray) {
            return new JSONArray(jsonArray.toString());
        }
        return value;
    }

    private boolean isAllowedByRules(JSONArray rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }

        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (!ruleMatches(rule)) {
                continue;
            }
            allowed = Objects.equals(rule.optString("action", "allow"), "allow");
        }
        return allowed;
    }

    private boolean ruleMatches(JSONObject rule) {
        if (rule.has("features") && !rule.getJSONObject("features").isEmpty()) {
            return false;
        }

        if (!rule.has("os")) {
            return true;
        }

        OsDetails osDetails = getCurrentOsDetails();
        JSONObject os = rule.getJSONObject("os");
        String expectedName = os.optString("name", "");
        if (!expectedName.isBlank() && !expectedName.equals(osDetails.name())) {
            return false;
        }

        String expectedArch = os.optString("arch", "");
        return expectedArch.isBlank()
                || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains(expectedArch.toLowerCase(Locale.ROOT));
    }

    private String resolveNativeClassifier(JSONObject natives) {
        OsDetails osDetails = getCurrentOsDetails();
        String classifier = natives.optString(osDetails.name(), "");
        if (classifier.isBlank()) {
            return null;
        }
        return classifier.replace("${arch}", osDetails.archBits());
    }

    private void downloadAssets(Path assetIndexPath, Path objectsDirectory) throws Exception {
        JSONObject assetIndex = new JSONObject(Files.readString(assetIndexPath, StandardCharsets.UTF_8));
        JSONObject objects = assetIndex.getJSONObject("objects");

        for (String assetName : objects.keySet()) {
            JSONObject asset = objects.getJSONObject(assetName);
            String hash = asset.getString("hash");
            String prefix = hash.substring(0, 2);
            Path target = objectsDirectory.resolve(prefix).resolve(hash);
            String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            downloadFileIfNeeded(url, target, hash);
        }
    }

    private JSONObject getJson(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String content = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download JSON from " + url + ": HTTP " + response.code());
            }
            return new JSONObject(content);
        }
    }

    private void downloadFileIfNeeded(String url, Path target, String expectedSha1) throws Exception {
        if (Files.exists(target) && sha1Matches(target, expectedSha1)) {
            return;
        }

        Files.createDirectories(target.getParent());
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download " + url + ": HTTP " + response.code());
            }

            Path tempFile = Files.createTempFile(target.getParent(), "download-", ".tmp");
            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!sha1Matches(tempFile, expectedSha1)) {
                Files.deleteIfExists(tempFile);
                throw new IOException("SHA-1 mismatch for downloaded file: " + target);
            }

            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean sha1Matches(Path path, String expectedSha1) throws Exception {
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return Files.exists(path);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString().equalsIgnoreCase(expectedSha1);
    }

    private void extractNative(Path archive, Path targetDirectory, JSONObject extractConfig) throws Exception {
        List<String> excludes = new ArrayList<>();
        if (extractConfig != null) {
            JSONArray excludeArray = extractConfig.optJSONArray("exclude");
            if (excludeArray != null) {
                for (int i = 0; i < excludeArray.length(); i++) {
                    excludes.add(excludeArray.getString(i));
                }
            }
        }

        try (InputStream inputStream = Files.newInputStream(archive);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory() || shouldExclude(entryName, excludes)) {
                    continue;
                }

                Path output = targetDirectory.resolve(entryName).normalize();
                if (!output.startsWith(targetDirectory)) {
                    throw new IOException("Invalid native entry path: " + entryName);
                }
                Files.createDirectories(output.getParent());
                Files.copy(zipInputStream, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean shouldExclude(String entryName, List<String> excludes) {
        for (String exclude : excludes) {
            if (entryName.startsWith(exclude)) {
                return true;
            }
        }
        return false;
    }

    private void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(directory))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
        Files.createDirectories(directory);
    }

    private OsDetails getCurrentOsDetails() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").contains("64") ? "64" : "32";

        if (osName.contains("win")) {
            return new OsDetails("windows", arch, "java.exe");
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return new OsDetails("osx", arch, "java");
        }
        return new OsDetails("linux", arch, "java");
    }

    private String applyVariables(String input, Map<String, String> variables) {
        String result = input;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String normalizeInstanceName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private String createUniqueSlug(String name, List<LauncherInstance> existingInstances) {
        String baseSlug = slugify(name);
        if (baseSlug.isBlank()) {
            baseSlug = "instance";
        }

        Set<String> usedSlugs = new HashSet<>();
        for (LauncherInstance instance : existingInstances) {
            usedSlugs.add(instance.slug().toLowerCase(Locale.ROOT));
        }

        String slug = baseSlug;
        int counter = 2;
        while (usedSlugs.contains(slug.toLowerCase(Locale.ROOT))) {
            slug = baseSlug + "-" + counter;
            counter++;
        }
        return slug;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized;
    }

    private boolean containsUnsupportedInstanceNameCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '/'
                    || current == '\\'
                    || current == ':'
                    || current == '*'
                    || current == '?'
                    || current == '"'
                    || current == '<'
                    || current == '>'
                    || current == '|'
                    || Character.isISOControl(current)) {
                return true;
            }
        }
        return false;
    }

    private String instanceKey(String instanceName) {
        return instanceName.toLowerCase(Locale.ROOT);
    }
}
