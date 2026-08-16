package app.voltlauncher.providers.content;

import app.voltlauncher.core.config.SettingsStore;
import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.MinecraftLauncherService;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.providers.curseforge.CurseForgePackInstaller;
import app.voltlauncher.providers.curseforge.CurseForgeProvider;
import app.voltlauncher.providers.model.ProviderId;
import app.voltlauncher.providers.modrinth.ModrinthPackInstaller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs modpack installations in the background and exposes their progress, so the UI can poll a
 * job id instead of holding a request open for what can be several minutes of downloading.
 */
public final class ModpackInstallService {

    public enum Phase { FETCHING, INSTALLING, DOWNLOADING_CONTENT, DONE, FAILED }

    public record Job(Phase phase, String message, Instance instance) {}

    private final MinecraftLauncherService launcher;
    private final Map<ProviderId, AbstractModpackInstaller> installers = new EnumMap<>(ProviderId.class);
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public ModpackInstallService(MinecraftLauncherService launcher, HttpFetcher http,
                                 CurseForgeProvider curseForge, SettingsStore settings) {
        this.launcher = launcher;
        register(new ModrinthPackInstaller(http));
        register(new CurseForgePackInstaller(http, curseForge, settings));
    }

    private void register(AbstractModpackInstaller installer) {
        installers.put(installer.provider(), installer);
    }

    /**
     * Starts an installation and returns its job id immediately.
     *
     * @param name blank to use the pack's own name
     */
    public String installAsync(ProviderId providerId, String versionId, String name) {
        AbstractModpackInstaller installer = installers.get(providerId);
        if (installer == null) {
            throw new IllegalArgumentException("No modpack support for provider " + providerId.id());
        }

        String jobId = UUID.randomUUID().toString();
        update(jobId, Phase.FETCHING, "Downloading modpack metadata…", null);

        Thread.ofVirtual().name("modpack-install-" + jobId).start(() -> {
            AbstractModpackInstaller.PackInfo info = null;
            Instance instance = null;
            try {
                info = installer.fetchPackInfo(versionId);

                update(jobId, Phase.INSTALLING, "Installing Minecraft and mod loader…", null);
                String instanceName = name == null || name.isBlank() ? info.packName() : name;
                instance = launcher.createInstance(instanceName, info.versionId());

                Instance created = instance;
                update(jobId, Phase.DOWNLOADING_CONTENT, "Downloading pack files…", created);
                installer.applyPackContents(created, info.archiveFile(),
                        message -> update(jobId, Phase.DOWNLOADING_CONTENT, message, created));

                update(jobId, Phase.DONE, "Installation complete", created);
            } catch (Exception e) {
                // Roll back so a failed install never leaves a broken profile in the list.
                if (instance != null) {
                    try { launcher.deleteInstance(instance.name()); } catch (Exception ignored) { /* best effort */ }
                }
                if (info != null) {
                    deleteQuietly(info.archiveFile());
                }
                update(jobId, Phase.FAILED,
                        e.getMessage() != null ? e.getMessage() : "Modpack installation failed", null);
            }
        });
        return jobId;
    }

    public Job job(String jobId) {
        return jobs.get(jobId);
    }

    public void clearJob(String jobId) {
        jobs.remove(jobId);
    }

    private void update(String jobId, Phase phase, String message, Instance instance) {
        jobs.put(jobId, new Job(phase, message, instance));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temp file in the system temp directory; the OS will reclaim it.
        }
    }
}
