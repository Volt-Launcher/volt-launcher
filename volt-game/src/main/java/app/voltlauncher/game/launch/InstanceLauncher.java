package app.voltlauncher.game.launch;

import app.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.core.AppPaths;
import app.voltlauncher.core.util.async.NamedLock;
import app.voltlauncher.core.util.async.ProcessRegistry;
import app.voltlauncher.game.install.AssetInstaller;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.game.instance.InstanceManager;
import app.voltlauncher.game.instance.LaunchResult;
import app.voltlauncher.game.instance.RunningInstanceStatus;
import app.voltlauncher.game.java.JavaRuntimeResolver;
import app.voltlauncher.game.platform.IPlatform;
import app.voltlauncher.game.platform.PlatformRegistry;
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

    /** A game that dies sooner than this almost certainly crashed rather than being quit. */
    private static final long EARLY_CRASH_THRESHOLD_MS = 10_000L;
    private static final long STOP_GRACE_SECONDS = 5;

    private final InstanceManager instanceManager;
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
            PlatformRegistry platformRegistry,
            IPlatform defaultPlatform,
            AssetInstaller assetInstaller,
            LaunchCommandBuilder commandBuilder,
            JavaRuntimeResolver javaResolver) {
        this.instanceManager = instanceManager;
        this.platformRegistry = platformRegistry;
        this.defaultPlatform = defaultPlatform;
        this.assetInstaller = assetInstaller;
        this.commandBuilder = commandBuilder;
        this.javaResolver = javaResolver;
        this.processRegistry = new ProcessRegistry((key, unused) -> onProcessExit(key));
    }

    /** Starts the launch pipeline and returns immediately; progress is polled via {@link #getLaunchState}. */
    public void launchAsync(MinecraftAccountSession session, String instanceName) {
        String key = instanceKey(instanceName);
        LaunchState current = launchStates.get(key);
        if (current != null && current.phase() == LaunchPhase.INSTALLING) return;
        if (processRegistry.isAlive(key)) return;

        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Preparing installation…", null));

        Thread.ofVirtual().name("launch-" + key).start(() -> {
            try {
                launchLock.withLock(key, () -> doLaunch(session, instanceName, key));
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                launchStates.put(key, new LaunchState(LaunchPhase.FAILED, message, null));
                System.err.println("[Launcher] Launch of '" + instanceName + "' failed: " + message);
            }
        });
    }

    public LaunchState getLaunchState(String instanceName) {
        return launchStates.getOrDefault(instanceKey(instanceName), new LaunchState(LaunchPhase.IDLE, null, null));
    }

    public boolean stopInstance(String instanceName) throws Exception {
        String key = instanceKey(instanceName);
        return launchLock.withLock(key, () -> {
            ProcessHandle handle = processRegistry.get(key);
            if (handle == null) throw new IllegalStateException("This instance is not running");

            handle.destroy();
            awaitExit(handle, STOP_GRACE_SECONDS);
            if (handle.isAlive()) {
                handle.destroyForcibly();
                awaitExit(handle, STOP_GRACE_SECONDS);
            }
            processRegistry.remove(key);
            runningMeta.remove(key);
            launchStates.remove(key);
            return !handle.isAlive();
        });
    }

    public void stopAll() {
        for (String key : processRegistry.liveKeys()) {
            RunningInstance running = runningMeta.get(key);
            if (running == null) continue;
            try {
                stopInstance(running.instance().name());
            } catch (Exception ignored) {
                // Shutdown path: a process that refuses to die must not block the others.
            }
        }
    }

    public boolean isRunning(String instanceName) {
        return processRegistry.isAlive(instanceKey(instanceName));
    }

    public RunningInstanceStatus getRunningStatus(String instanceName) {
        String key = instanceKey(instanceName);
        RunningInstance running = runningMeta.get(key);
        if (running == null || !processRegistry.isAlive(key)) return null;
        return toStatus(running);
    }

    public List<RunningInstanceStatus> listRunning() {
        List<RunningInstanceStatus> result = new ArrayList<>();
        for (String key : processRegistry.liveKeys()) {
            RunningInstance running = runningMeta.get(key);
            if (running != null) result.add(toStatus(running));
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

    private Void doLaunch(MinecraftAccountSession session, String instanceName, String key) throws Exception {
        Instance instance = instanceManager.findByName(instanceName);
        IPlatform platform = platformRegistry.resolvePlatformForVersion(instance.versionId(), defaultPlatform);
        JSONObject meta = platform.versionResolver().resolveMetadata(instance.versionId());

        launchStates.put(key, new LaunchState(LaunchPhase.INSTALLING, "Installing game files…", null));
        AssetInstaller.Installation install = assetInstaller.ensureInstallation(instance, meta);

        launchStates.put(key, new LaunchState(LaunchPhase.LAUNCHING, "Starting Minecraft…", null));
        InstanceManager.RequiredJava required = instanceManager.resolveRequiredJava(meta, install.launchVersionId());
        JavaRuntimeResolver.JavaRuntime runtime = resolveRuntimeFor(instance, required.majorVersion());
        List<String> command = commandBuilder.build(session, install, runtime);

        Path logFile = AppPaths.logsDirectory().resolve(instance.slug() + ".log");
        Files.createDirectories(logFile.getParent());
        Files.createDirectories(instance.gameDirectory());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(instance.gameDirectory().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        ProcessHandle handle = builder.start().toHandle();
        long startedAt = System.currentTimeMillis();
        processRegistry.register(key, handle);
        runningMeta.put(key, new RunningInstance(instance, handle, startedAt, runtime));
        instanceManager.markPlayed(instance, startedAt);

        LaunchResult result = new LaunchResult(
                instance.name(), install.launchVersionId(), handle.pid(),
                String.join(" ", command), logFile.toString(),
                runtime.majorVersion(), runtime.javaExecutable().toString());

        launchStates.put(key, new LaunchState(LaunchPhase.RUNNING, "Running", result));
        return null;
    }

    /** A per-instance Java override wins over auto-detection; anything else resolves by major version. */
    private JavaRuntimeResolver.JavaRuntime resolveRuntimeFor(Instance instance, int requiredMajor) throws Exception {
        String override = instance.settings().javaPath();
        if (override == null || override.isBlank()) {
            return javaResolver.resolveRuntime(requiredMajor);
        }

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

    private void onProcessExit(String key) {
        RunningInstance ended = runningMeta.remove(key);
        LaunchState previous = launchStates.get(key);
        if (previous == null || previous.result() == null || previous.phase() != LaunchPhase.RUNNING) return;

        long uptimeMs = ended == null ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - ended.startedAt());
        boolean crashedEarly = uptimeMs < EARLY_CRASH_THRESHOLD_MS;
        String message = crashedEarly
                ? "Minecraft closed right after starting. Check the log: " + previous.result().logFile()
                : "Minecraft closed";
        launchStates.put(key, new LaunchState(
                crashedEarly ? LaunchPhase.FAILED : LaunchPhase.IDLE, message, previous.result()));
    }

    private RunningInstanceStatus toStatus(RunningInstance running) {
        return new RunningInstanceStatus(
                running.instance().name(), running.instance().versionId(), running.handle().pid(),
                running.startedAt(), running.handle().isAlive(),
                running.runtime().majorVersion(), running.runtime().javaExecutable().toString());
    }

    private void awaitExit(ProcessHandle handle, long timeoutSeconds) {
        try {
            handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Timed out or already reaped — the caller re-checks isAlive().
        }
    }

    private String instanceKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public enum LaunchPhase { IDLE, INSTALLING, LAUNCHING, RUNNING, FAILED }

    public record LaunchState(LaunchPhase phase, String message, LaunchResult result) {}

    private record RunningInstance(
            Instance instance, ProcessHandle handle, long startedAt, JavaRuntimeResolver.JavaRuntime runtime) {}
}
