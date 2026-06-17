package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.AppPaths;
import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.launcher.install.AssetInstaller;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceManager;
import app.voltlauncher.voltlauncher.launcher.instance.LaunchResult;
import app.voltlauncher.voltlauncher.launcher.instance.RunningInstanceStatus;
import app.voltlauncher.voltlauncher.launcher.java.JavaRuntimeResolver;
import app.voltlauncher.voltlauncher.launcher.platform.IPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
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

public final class InstanceLauncher {

    private final InstanceManager instanceManager;
    private final VanillaVersionResolver versionResolver;
    private final PlatformRegistry platformRegistry;
    private final IPlatform defaultPlatform;
    private final AssetInstaller assetInstaller;
    private final LaunchCommandBuilder commandBuilder;
    private final JavaRuntimeResolver javaResolver;
    private final ProcessRegistry processRegistry;
    private final NamedLock launchLock = new NamedLock();
    private final ConcurrentHashMap<String, RunningInstance> runningMeta = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LaunchState> launchStates = new ConcurrentHashMap<>();

    public InstanceLauncher(
            InstanceManager instanceManager,
            VanillaVersionResolver versionResolver,
            PlatformRegistry platformRegistry,
            IPlatform defaultPlatform,
            AssetInstaller assetInstaller,
            LaunchCommandBuilder commandBuilder,
            JavaRuntimeResolver javaResolver) {
        this.instanceManager = instanceManager;
        this.versionResolver = versionResolver;
        this.platformRegistry = platformRegistry;
        this.defaultPlatform = defaultPlatform;
        this.assetInstaller = assetInstaller;
        this.commandBuilder = commandBuilder;
        this.javaResolver = javaResolver;
        this.processRegistry = new ProcessRegistry((key, unused) -> onProcessExit(key));
    }

    /**
     * Startet den Launch-Prozess asynchron. Gibt sofort zurück.
     * Status kann über {@link #getLaunchState(String)} abgefragt werden.
     */
    public void launchAsync(MinecraftAccountSession session, String instanceName) {
        String key = instanceKey(instanceName);
        LaunchState current = launchStates.get(key);
        if (current != null && current.phase() == LaunchPhase.INSTALLING) return;
        if (processRegistry.isAlive(key)) return;

        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Assets werden installiert...", null));

        Thread.ofVirtual().start(() -> {
            try {
                launchLock.withLock(key, () -> {
                    try {
                        doLaunch(session, instanceName, key);
                    } catch (Exception e) {
                        launchStates.put(key, new LaunchState(LaunchPhase.FAILED, e.getMessage(), null));
                        throw new RuntimeException(e);
                    }
                    return null;
                });
            } catch (Exception e) {
                launchStates.put(key, new LaunchState(LaunchPhase.FAILED,
                        e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler", null));
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
                if (handle == null) throw new IllegalStateException("This instance is not running");
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

    public void stopAll() {
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri == null) continue;
            try { stopInstance(ri.instance().name()); } catch (Exception ignored) {}
        }
    }

    public boolean isRunning(String instanceName) {
        return processRegistry.isAlive(instanceKey(instanceName));
    }

    public RunningInstanceStatus getRunningStatus(String instanceName) {
        String key = instanceKey(instanceName);
        RunningInstance ri = runningMeta.get(key);
        if (ri == null || !processRegistry.isAlive(key)) return null;
        return toStatus(ri);
    }

    public List<RunningInstanceStatus> listRunning() {
        List<RunningInstanceStatus> result = new ArrayList<>();
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri != null) result.add(toStatus(ri));
        }
        result.sort(Comparator.comparing(RunningInstanceStatus::instanceName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public void clearState(String instanceName) {
        String key = instanceKey(instanceName);
        launchStates.remove(key);
        runningMeta.remove(key);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void doLaunch(MinecraftAccountSession session, String instanceName, String key) throws Exception {
        Instance instance = instanceManager.findByName(instanceName);
        IPlatform platform = platformRegistry.resolvePlatformForVersion(instance.versionId(), defaultPlatform);
        String baseMinecraftVersionId = PlatformRegistry.extractBaseMinecraftVersionId(instance.versionId());
        JSONObject baseMeta = versionResolver.resolveMetadata(baseMinecraftVersionId);
        JSONObject meta = platform.versionResolver().resolveMetadata(instance.versionId());

        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Installing Assets...", null));
        AssetInstaller.Installation install = assetInstaller.ensureInstallation(instance, baseMeta);
        if (!PlatformRegistry.VANILLA_ID.equals(platform.id())) {
            install = assetInstaller.ensureInstallation(instance, meta);
        }

        launchStates.put(key, new LaunchState(LaunchPhase.LAUNCHING, "Starting Minecraft...", null));
        InstanceManager.RequiredJava req = instanceManager.resolveRequiredJava(meta, install.launchVersionId());
        JavaRuntimeResolver.JavaRuntime runtime = resolveRuntimeFor(instance, req.majorVersion());
        List<String> cmd = commandBuilder.build(session, install, runtime);

        Path logFile = AppPaths.logsDirectory().resolve(instance.slug() + "-" + install.launchVersionId() + ".log");
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

        LaunchResult result = new LaunchResult(
                instance.name(), install.launchVersionId(), handle.pid(),
                String.join(" ", cmd), logFile.toString(),
                runtime.majorVersion(), runtime.javaExecutable().toString());

        launchStates.put(key, new LaunchState(LaunchPhase.RUNNING, "Läuft", result));
    }

    private JavaRuntimeResolver.JavaRuntime resolveRuntimeFor(Instance instance, int requiredMajor) throws Exception {
        String override = instance.settings().javaPath();
        if (override != null && !override.isBlank()) {
            Path candidate = Path.of(override.trim());
            if (Files.isDirectory(candidate)) {
                boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                candidate = candidate.resolve("bin").resolve(windows ? "java.exe" : "java");
            }
            if (!Files.isExecutable(candidate)) {
                throw new IllegalStateException("Configured Java path is not executable: " + override);
            }
            return new JavaRuntimeResolver.JavaRuntime(requiredMajor, candidate.toAbsolutePath().normalize(), "user-override");
        }
        return javaResolver.resolveRuntime(requiredMajor);
    }

    private void onProcessExit(String key) {
        RunningInstance ended = runningMeta.remove(key);
        LaunchState previous = launchStates.get(key);
        if (previous == null || previous.result() == null || previous.phase() != LaunchPhase.RUNNING) return;

        long uptimeMs = ended == null ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - ended.startedAt());
        boolean crashedEarly = uptimeMs < 10_000L;
        String message = crashedEarly
                ? "Minecraft wurde kurz nach dem Start beendet. Bitte Log pruefen."
                : "Minecraft wurde beendet";
        LaunchPhase phase = crashedEarly ? LaunchPhase.FAILED : LaunchPhase.IDLE;
        launchStates.put(key, new LaunchState(phase, message, previous.result()));
    }

    private RunningInstanceStatus toStatus(RunningInstance ri) {
        return new RunningInstanceStatus(
                ri.instance().name(), ri.instance().versionId(), ri.handle().pid(),
                ri.startedAt(), ri.handle().isAlive(),
                ri.runtime().majorVersion(), ri.runtime().javaExecutable().toString());
    }

    private void await(ProcessHandle handle, long timeoutSeconds) {
        try { handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private String instanceKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public enum LaunchPhase { IDLE, INSTALLING, LAUNCHING, RUNNING, FAILED }

    public record LaunchState(LaunchPhase phase, String message, LaunchResult result) {}

    private record RunningInstance(
            Instance instance, ProcessHandle handle, long startedAt, JavaRuntimeResolver.JavaRuntime runtime) {}
}
