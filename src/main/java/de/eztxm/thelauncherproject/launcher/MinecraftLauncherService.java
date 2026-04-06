package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;
import de.eztxm.thelauncherproject.auth.MinecraftAccountSession;
import de.eztxm.thelauncherproject.storage.LauncherInstanceStore;
import de.eztxm.thelauncherproject.util.HttpFetcher;
import de.eztxm.thelauncherproject.util.async.NamedLock;
import de.eztxm.thelauncherproject.util.async.ProcessRegistry;
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

    private record RunningInstance(LauncherInstance instance, ProcessHandle handle, long startedAt, JavaRuntimeResolver.JavaRuntime runtime) {}

    private final InstanceManager instanceManager;
    private final VersionResolver versionResolver;
    private final AssetInstaller assetInstaller;
    private final LaunchCommandBuilder commandBuilder;
    private final JavaRuntimeResolver javaResolver;
    private final ProcessRegistry processRegistry;
    private final NamedLock launchLock = new NamedLock();
    private final ConcurrentHashMap<String, RunningInstance> runningMeta = new ConcurrentHashMap<>();

    public MinecraftLauncherService(String launcherClientId) {
        HttpFetcher http = new HttpFetcher();
        this.versionResolver = new VersionResolver(http);
        this.assetInstaller = new AssetInstaller(http, versionResolver);
        this.instanceManager = new InstanceManager(new LauncherInstanceStore(), versionResolver);
        this.commandBuilder = new LaunchCommandBuilder(launcherClientId);
        this.javaResolver = new JavaRuntimeResolver();
        this.processRegistry = new ProcessRegistry((key, _) -> runningMeta.remove(key));
    }

    public List<LauncherInstance> listInstances() throws Exception {
        return instanceManager.listInstances();
    }

    public LauncherInstance createInstance(String name, String versionId) throws Exception {
        return instanceManager.createInstance(name, versionId);
    }

    public List<AvailableVersion> listVersions(
            boolean includeSnapshots, boolean includeBetas, boolean includeAlphas) throws Exception {
        List<AvailableVersion> result = new ArrayList<>();
        for (VersionResolver.ManifestEntry e : versionResolver.loadEntries()) {
            if (!shouldInclude(e.type(), includeSnapshots, includeBetas, includeAlphas)) {
                continue;
            }
            result.add(new AvailableVersion(e.id(), e.type(), e.releaseTime()));
        }
        return result;
    }

    public LaunchResult launchInstance(MinecraftAccountSession session, String instanceName) throws Exception {
        String key = instanceKey(instanceName);
        return launchLock.withLock(key, () -> {
            try {
                if (processRegistry.isAlive(key)) {
                    throw new IllegalStateException("This instance is already running");
                }
                LauncherInstance instance = instanceManager.findByName(instanceName);
                JSONObject meta = versionResolver.resolveMetadata(instance.versionId());
                AssetInstaller.Installation install = assetInstaller.ensureInstallation(instance, meta);
                InstanceManager.RequiredJava req = instanceManager.resolveRequiredJava(
                        meta, install.launchVersionId());
                JavaRuntimeResolver.JavaRuntime runtime = javaResolver.resolveRuntime(req.majorVersion());
                List<String> cmd = commandBuilder.build(session, install, runtime);

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

                return new LaunchResult(
                        instance.name(), install.launchVersionId(), handle.pid(),
                        String.join(" ", cmd), logFile.toString(),
                        runtime.majorVersion(), runtime.javaExecutable().toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean stopInstance(String instanceName) throws Exception {
        String key = instanceKey(instanceName);
        return launchLock.withLock(key, () -> {
            try {
                ProcessHandle handle = processRegistry.get(key);
                if (handle == null) {
                    throw new IllegalStateException("This instance is not running");
                }
                handle.destroy();
                await(handle, 5);
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                    await(handle, 5);
                }
                processRegistry.remove(key);
                runningMeta.remove(key);
                return !handle.isAlive();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void stopAllRunningInstances() {
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri == null) {
                continue;
            }
            try {
                stopInstance(ri.instance().name());
            } catch (Exception _) {}
        }
    }

    public RunningInstanceStatus getRunningInstanceStatus(String instanceName) {
        String key = instanceKey(instanceName);
        RunningInstance ri = runningMeta.get(key);
        if (ri == null || !processRegistry.isAlive(key)) {
            return null;
        }
        return toStatus(ri);
    }

    public List<RunningInstanceStatus> listRunningInstances() {
        List<RunningInstanceStatus> result = new ArrayList<>();
        for (String key : processRegistry.liveKeys()) {
            RunningInstance ri = runningMeta.get(key);
            if (ri != null) {
                result.add(toStatus(ri));
            }
        }
        result.sort(Comparator.comparing(
                RunningInstanceStatus::instanceName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private RunningInstanceStatus toStatus(RunningInstance ri) {
        return new RunningInstanceStatus(
                ri.instance().name(), ri.instance().versionId(),
                ri.handle().pid(), ri.startedAt(), ri.handle().isAlive(),
                ri.runtime().majorVersion(), ri.runtime().javaExecutable().toString());
    }

    private boolean shouldInclude(String type, boolean snap, boolean beta, boolean alpha) {
        return switch (type) {
            case "release" -> true;
            case "snapshot" -> snap;
            case "old_beta" -> beta;
            case "old_alpha" -> alpha;
            default -> false;
        };
    }

    private void await(ProcessHandle handle, long timeoutSeconds) {
        try {
            handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception _) {}
    }

    private String instanceKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}