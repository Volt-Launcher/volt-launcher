package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.launcher.install.AssetInstaller;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceContentService;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceManager;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceSettings;
import app.voltlauncher.voltlauncher.launcher.instance.RunningInstanceStatus;
import app.voltlauncher.voltlauncher.launcher.java.JavaRuntimeResolver;
import app.voltlauncher.voltlauncher.launcher.platform.IPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.VersionOrdering;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.storage.LauncherInstanceStore;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinecraftLauncherService {

    private final InstanceManager instanceManager;
    private final VanillaVersionResolver versionResolver;
    private final PlatformRegistry platformRegistry;
    private final IPlatform defaultPlatform;
    private final AssetInstaller assetInstaller;
    private final ModrinthPackInstaller modrinthInstaller;
    private final InstanceLauncher instanceLauncher;
    private final InstanceContentService contentService;

    private final ConcurrentHashMap<String, ModrinthInstallJob> modrinthJobs = new ConcurrentHashMap<>();

    public enum ModrinthInstallPhase { FETCHING, INSTALLING, DOWNLOADING_MODS, DONE, FAILED }

    public record ModrinthInstallJob(
            ModrinthInstallPhase phase,
            String message,
            Instance instance) {}

    public MinecraftLauncherService(String launcherClientId) {
        HttpFetcher http = new HttpFetcher();
        this.versionResolver = new VanillaVersionResolver(http);
        this.platformRegistry = PlatformRegistry.withDefaults(versionResolver);
        this.defaultPlatform = platformRegistry.requireDefault();
        JavaRuntimeResolver javaResolver = new JavaRuntimeResolver();
        this.assetInstaller = new AssetInstaller(http, versionResolver, javaResolver);
        this.modrinthInstaller = new ModrinthPackInstaller(http);
        this.instanceManager = new InstanceManager(new LauncherInstanceStore(), versionResolver);
        this.contentService = new InstanceContentService(instanceManager);
        LaunchCommandBuilder commandBuilder = new LaunchCommandBuilder(launcherClientId);
        this.instanceLauncher = new InstanceLauncher(
                instanceManager, versionResolver, platformRegistry, defaultPlatform,
                assetInstaller, commandBuilder, javaResolver);
    }

    // ── Instance queries ──────────────────────────────────────────────────────

    public List<Instance> listInstances() throws Exception {
        return instanceManager.listInstances();
    }

    public Instance createInstance(String name, String versionId) throws Exception {
        IPlatform platform = platformRegistry.resolvePlatformForVersion(versionId, defaultPlatform);
        String baseVersionId = PlatformRegistry.extractBaseMinecraftVersionId(versionId);
        JSONObject baseMeta = versionResolver.resolveMetadata(baseVersionId);
        JSONObject meta = platform.versionResolver().resolveMetadata(versionId);
        String versionType = platform.id().equals(PlatformRegistry.VANILLA_ID)
                ? meta.optString("type", "release") : platform.id();

        Instance instance = instanceManager.createInstance(name, versionId, versionType, meta);
        try {
            assetInstaller.ensureInstallation(instance, baseMeta);
            if (!PlatformRegistry.VANILLA_ID.equals(platform.id())) {
                assetInstaller.ensureInstallation(instance, meta);
            }
            return instance;
        } catch (Exception e) {
            try { instanceManager.removeInstance(instance.name()); } catch (Exception ignored) {}
            throw e;
        }
    }

    public String installModrinthPackAsync(String name, String modrinthVersionId) {
        String jobId = UUID.randomUUID().toString();
        modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.FETCHING, "Downloading modpack metadata...", null));
        Thread.ofVirtual().start(() -> {
            try {
                modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.FETCHING, "Downloading modpack metadata...", null));
                ModrinthPackInstaller.PackInfo info = modrinthInstaller.fetchPackInfo(modrinthVersionId);
                try {
                    modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.INSTALLING, "Installing Minecraft assets...", null));
                    Instance instance = createInstance(name.isBlank() ? info.packName() : name, info.versionId());
                    try {
                        modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.DOWNLOADING_MODS, "Downloading mod files...", instance));
                        modrinthInstaller.applyPackContents(instance, info.mrpackFile());
                        modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.DONE, "Installation complete", instance));
                    } catch (Exception e) {
                        try { instanceManager.removeInstance(instance.name()); } catch (Exception ignored) {}
                        throw e;
                    }
                } catch (Exception e) {
                    java.nio.file.Files.deleteIfExists(info.mrpackFile());
                    throw e;
                }
            } catch (Exception e) {
                modrinthJobs.put(jobId, new ModrinthInstallJob(ModrinthInstallPhase.FAILED, e.getMessage() != null ? e.getMessage() : "Installation failed", null));
            }
        });
        return jobId;
    }

    public ModrinthInstallJob getModrinthInstallJob(String jobId) {
        return modrinthJobs.get(jobId);
    }

    public void clearModrinthInstallJob(String jobId) {
        modrinthJobs.remove(jobId);
    }

    public void deleteInstance(String name) throws Exception {
        if (instanceLauncher.isRunning(name)) {
            throw new IllegalStateException("Instance is currently running. Stop it before deleting.");
        }
        instanceManager.removeInstance(name);
        instanceLauncher.clearState(name);
    }

    public Instance renameInstance(String name, String newName) throws Exception {
        if (instanceLauncher.isRunning(name)) {
            throw new IllegalStateException("Instance is currently running. Stop it before renaming.");
        }
        return instanceManager.renameInstance(name, newName);
    }

    public Path getInstanceFolder(String name) throws Exception {
        return instanceManager.findByName(name).gameDirectory();
    }

    public Instance updateInstanceSettings(String name, InstanceSettings settings) throws Exception {
        return instanceManager.updateSettings(name, settings);
    }

    // ── Content (mods / resourcepacks / shaderpacks / datapacks) ───────────────

    public List<InstanceContentService.ContentEntry> listContent(
            String name, InstanceContentService.ContentType type) throws Exception {
        return contentService.list(name, type);
    }

    public InstanceContentService.ContentEntry addContent(
            String name, InstanceContentService.ContentType type, Path source) throws Exception {
        return contentService.add(name, type, source);
    }

    public void removeContent(
            String name, InstanceContentService.ContentType type, String fileName) throws Exception {
        contentService.remove(name, type, fileName);
    }

    public InstanceContentService.ContentEntry toggleContent(
            String name, InstanceContentService.ContentType type, String fileName) throws Exception {
        return contentService.toggle(name, type, fileName);
    }

    // ── Version queries ───────────────────────────────────────────────────────

    public List<AvailableVersion> listVersions(
            boolean includeSnapshots, boolean includeBetas, boolean includeAlphas) throws Exception {
        List<AvailableVersion> result = defaultPlatform.listVersions(includeSnapshots, includeBetas, includeAlphas);
        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    public List<AvailableVersion> listLoaderVersions(String platformId, String minecraftVersionId) throws Exception {
        IPlatform platform = platformRegistry.require(platformId);
        List<AvailableVersion> result = new ArrayList<>(platform.versionResolver().listLoaderVersions(minecraftVersionId));
        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    // ── Launch delegation ─────────────────────────────────────────────────────

    public void launchInstanceAsync(MinecraftAccountSession session, String instanceName) {
        instanceLauncher.launchAsync(session, instanceName);
    }

    public InstanceLauncher.LaunchState getLaunchState(String instanceName) {
        return instanceLauncher.getLaunchState(instanceName);
    }

    public boolean stopInstance(String instanceName) throws Exception {
        return instanceLauncher.stopInstance(instanceName);
    }

    public void stopAllRunningInstances() {
        instanceLauncher.stopAll();
    }

    public RunningInstanceStatus getRunningInstanceStatus(String instanceName) {
        return instanceLauncher.getRunningStatus(instanceName);
    }

    public List<RunningInstanceStatus> listRunningInstances() {
        return instanceLauncher.listRunning();
    }
}
