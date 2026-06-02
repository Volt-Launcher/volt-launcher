package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.AppPaths;
import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceManager;
import app.voltlauncher.voltlauncher.launcher.instance.LaunchResult;
import app.voltlauncher.voltlauncher.launcher.instance.RunningInstanceStatus;
import app.voltlauncher.voltlauncher.launcher.java.JavaRuntimeResolver;
import app.voltlauncher.voltlauncher.launcher.platform.IPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.VersionOrdering;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.storage.LauncherInstanceStore;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import app.voltlauncher.voltlauncher.util.async.NamedLock;
import app.voltlauncher.voltlauncher.util.async.ProcessRegistry;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MinecraftLauncherService {

    private final InstanceManager instanceManager;
    private final VanillaVersionResolver versionResolver;
    private final PlatformRegistry platformRegistry;
    private final IPlatform defaultPlatform;
    private final AssetInstaller assetInstaller;
    private final ModrinthPackInstaller modrinthInstaller;
    private final LaunchCommandBuilder commandBuilder;
    private final JavaRuntimeResolver javaResolver;
    private final ProcessRegistry processRegistry;
    private final NamedLock launchLock = new NamedLock();
    private final ConcurrentHashMap < String, RunningInstance> runningMeta = new ConcurrentHashMap <> ();
    private final ConcurrentHashMap < String, LaunchState> launchStates = new ConcurrentHashMap <> ();

    public MinecraftLauncherService(String launcherClientId) {
        HttpFetcher http = new HttpFetcher();
        this.versionResolver = new VanillaVersionResolver(http);
        this.platformRegistry = PlatformRegistry.withDefaults(versionResolver);
        this.defaultPlatform = platformRegistry.requireDefault();
        this.javaResolver = new JavaRuntimeResolver();
        this.assetInstaller = new AssetInstaller(http, versionResolver, this.javaResolver);
        this.modrinthInstaller = new ModrinthPackInstaller(http);
        this.instanceManager = new InstanceManager(new LauncherInstanceStore(), versionResolver);
        this.commandBuilder = new LaunchCommandBuilder(launcherClientId);
        this.processRegistry = new ProcessRegistry((key, unused) -> {
            RunningInstance ended = runningMeta.remove(key);
            LaunchState previous = launchStates.get(key);
            if (previous == null || previous.result() == null || previous.phase() != LaunchPhase.RUNNING) {
                return;
            }
            long uptimeMs = ended == null ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - ended.startedAt());
            boolean crashedEarly = uptimeMs < 10_000L;
            String message = crashedEarly
                    ? "Minecraft wurde kurz nach dem Start beendet. Bitte Log pruefen."
                    : "Minecraft wurde beendet";
            LaunchPhase phase = crashedEarly ? LaunchPhase.FAILED : LaunchPhase.IDLE;
            launchStates.put(key, new LaunchState(phase, message, previous.result()));
        });
    }

    public List < Instance> listInstances() throws Exception {
        return instanceManager.listInstances();
    }

    public Instance installModrinthPack(String name, String modrinthVersionId) throws Exception {
        ModrinthPackInstaller.PackInfo info = modrinthInstaller.fetchPackInfo(modrinthVersionId);
        try {
            Instance instance = createInstance(name.isBlank() ? info.packName() : name, info.versionId());
            try {
                modrinthInstaller.applyPackContents(instance, info.mrpackFile());
            } catch (Exception e) {
                try { instanceManager.removeInstance(instance.name()); } catch (Exception ignored) {}
                throw e;
            }
            return instance;
        } catch (Exception e) {
            java.nio.file.Files.deleteIfExists(info.mrpackFile());
            throw e;
        }
    }

    public Instance createInstance(String name, String versionId) throws Exception {
        IPlatform platform = resolvePlatformForVersion(versionId);
        String baseMinecraftVersionId = extractBaseMinecraftVersionId(versionId);
        JSONObject baseMeta = versionResolver.resolveMetadata(baseMinecraftVersionId);
        JSONObject meta = platform.versionResolver().resolveMetadata(versionId);
        String versionType = platform.id().equals(PlatformRegistry.VANILLA_ID) ? meta.optString("type", "release") : platform.id();

        Instance instance = instanceManager.createInstance(name, versionId, versionType, meta);
        try {
            // Always prepare vanilla assets first so platform installation can build on top.
            assetInstaller.ensureInstallation(instance, baseMeta);
            if (!PlatformRegistry.VANILLA_ID.equals(platform.id())) {
                assetInstaller.ensureInstallation(instance, meta);
            }
            return instance;
        } catch (Exception e) {
            try {
                instanceManager.removeInstance(instance.name());
            } catch (Exception ignored) {
                // Keep original installation error as primary failure.
            }
            throw e;
        }
    }

    public List < AvailableVersion> listVersions(
    boolean includeSnapshots, boolean includeBetas, boolean includeAlphas) throws Exception {
        List < AvailableVersion> result = defaultPlatform.listVersions(includeSnapshots, includeBetas, includeAlphas);
        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    public List < AvailableVersion> listLoaderVersions(String platformId, String minecraftVersionId) throws Exception {
        IPlatform platform = platformRegistry.require(platformId);
        List < AvailableVersion> result = new ArrayList <> ( platform.versionResolver().listLoaderVersions(minecraftVersionId));
        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    /**
    * Startet den Launch-Prozess asynchron.
    * Gibt sofort zurück — der tatsächliche Launch (inkl. Asset-Download) läuft im Hintergrund.
    * Status kann über getLaunchState() abgefragt werden.
    */
    public void launchInstanceAsync(MinecraftAccountSession session, String instanceName) {
        String key = instanceKey(instanceName);

        LaunchState current = launchStates.get(key);
        if (current != null && current.phase() == LaunchPhase.INSTALLING) {
            return;
        }
        if (processRegistry.isAlive(key)) {
            return;
        }

        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Assets werden installiert...", null));

        Thread.ofVirtual().start(() -> {
            try {
                launchLock.withLock(key, () -> {
                    try {
                        Instance instance = instanceManager.findByName(instanceName);
                        IPlatform platform = resolvePlatformForVersion(instance.versionId());
                        String baseMinecraftVersionId = extractBaseMinecraftVersionId(instance.versionId());
                        JSONObject baseMeta = versionResolver.resolveMetadata(baseMinecraftVersionId);
                        JSONObject meta = platform.versionResolver().resolveMetadata(instance.versionId());

                        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Installing Assets...", null));
                        AssetInstaller.Installation install = assetInstaller.ensureInstallation(instance, baseMeta);
                        if (!PlatformRegistry.VANILLA_ID.equals(platform.id())) {
                            install = assetInstaller.ensureInstallation(instance, meta);
                        }

                        launchStates.put(key, new LaunchState(LaunchPhase.LAUNCHING, "Starting Minecraft...", null));
                        InstanceManager.RequiredJava req = instanceManager.resolveRequiredJava(meta, install.launchVersionId());
                        JavaRuntimeResolver.JavaRuntime runtime = javaResolver.resolveRuntime(req.majorVersion());
                        List < String> cmd = commandBuilder.build(session, install, runtime);

                        Path logFile = AppPaths.logsDirectory()
                        .resolve(instance.slug() + "-" + install.launchVersionId() + ".log");
                        Files.createDirectories(logFile.getParent());
                        Files.createDirectories(instance.gameDirectory());

                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        pb.directory(instance.gameDirectory().toFile());
                        pb.redirectErrorStream(true);
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

                        ProcessHandle handle = pb.start().toHandle();
                        long startedAt = System.currentTimeMillis();
                        processRegistry.register(key, handle);
                        runningMeta.put(key, new RunningInstance(instance, handle, startedAt, runtime));
                        instanceManager.markPlayed(instance, startedAt);

                        LaunchResult result = new LaunchResult( instance.name(), install.launchVersionId(), handle.pid(), String.join(" ", cmd), logFile.toString(), runtime.majorVersion(), runtime.javaExecutable().toString());

                        launchStates.put(key, new LaunchState(LaunchPhase.RUNNING, "Läuft", result));
                        return null;
                    } catch (Exception e) {
                        launchStates.put(key, new LaunchState(LaunchPhase.FAILED, e.getMessage(), null));
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                launchStates.put(key, new LaunchState(LaunchPhase.FAILED, e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler", null));
                System.err.println("[Launcher] Launch fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    public LaunchState getLaunchState(String instanceName) {
        return launchStates.getOrDefault(instanceKey(instanceName), new LaunchState(LaunchPhase.IDLE, null, null));
    }

    public boolean stopInstance(String instanceName) throws Exception {
        String key = instanceKey(instanceName);
        return launchLock.withLock(key, () -> {
            try {
                ProcessHandle handle = processRegistry.get(key);
                if (handle == null) { throw new IllegalStateException("This instance is not running"); }
                handle.destroy();
                await(handle, 5);
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                    await(handle, 5);
                }
                processRegistry.remove(key);
                runningMeta.remove(key);
                launchStates.remove(key);
                return !handle.isAlive();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void deleteInstance(String name) throws Exception {
        if (processRegistry.isAlive(instanceKey(name))) {
            throw new IllegalStateException("Instance is currently running. Stop it before deleting.");
        }
        instanceManager.removeInstance(name);
        launchStates.remove(instanceKey(name));
        runningMeta.remove(instanceKey(name));
    }

    public Instance renameInstance(String name, String newName) throws Exception {
        if (processRegistry.isAlive(instanceKey(name))) {
            throw new IllegalStateException("Instance is currently running. Stop it before renaming.");
        }
        return instanceManager.renameInstance(name, newName);
    }

    public java.nio.file.Path getInstanceFolder(String name) throws Exception {
        Instance instance = instanceManager.findByName(name);
        return instance.gameDirectory();
    }

    public void stopAllRunningInstances() {
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri == null) { continue; }
            try { stopInstance(ri.instance().name()); } catch (Exception ignored) {}
        }
    }

    public RunningInstanceStatus getRunningInstanceStatus(String instanceName) {
        String key = instanceKey(instanceName);
        RunningInstance ri = runningMeta.get(key);
        if (ri == null || !processRegistry.isAlive(key)) return null;
        return toStatus(ri);
    }

    public List < RunningInstanceStatus> listRunningInstances() {
        List < RunningInstanceStatus> result = new ArrayList <> ();
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri != null) { result.add(toStatus(ri)); }
        }
        result.sort(Comparator.comparing(RunningInstanceStatus::instanceName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private RunningInstanceStatus toStatus(RunningInstance ri) {
        return new RunningInstanceStatus( ri.instance().name(), ri.instance().versionId(), ri.handle().pid(), ri.startedAt(), ri.handle().isAlive(), ri.runtime().majorVersion(), ri.runtime().javaExecutable().toString());
    }

    private void await(ProcessHandle handle, long timeoutSeconds) {
        try { handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private String instanceKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private IPlatform resolvePlatformForVersion(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return defaultPlatform;
        }
        int sep = versionId.indexOf(':');
        if (sep <= 0) {
            return defaultPlatform;
        }
        String candidate = versionId.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        return platformRegistry.get(candidate).orElse(defaultPlatform);
    }

    private String extractBaseMinecraftVersionId(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return versionId;
        }
        String trimmed = versionId.trim();
        int firstSep = trimmed.indexOf(':');
        if (firstSep <= 0) {
            return trimmed;
        }
        String rest = trimmed.substring(firstSep + 1);
        int secondSep = rest.indexOf(':');
        return secondSep >= 0 ? rest.substring(0, secondSep).trim() : rest.trim();
    }

    public enum LaunchPhase { IDLE, INSTALLING, LAUNCHING, RUNNING, FAILED }

    public record LaunchState(LaunchPhase phase, String message, LaunchResult result) {}

    private record RunningInstance( Instance instance, ProcessHandle handle,
    long startedAt, JavaRuntimeResolver.JavaRuntime runtime) {}
}
