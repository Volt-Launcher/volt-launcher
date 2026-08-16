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
 *
 * <p>The profile is created early — the loader has to be installed before its mods can land — so
 * it is registered as busy for the rest of the install. That is what stops the UI (and the API)
 * from launching or deleting a profile that is only half populated.
 */
public final class ModpackInstallService {

    public enum Phase { FETCHING, INSTALLING, DOWNLOADING_CONTENT, DONE, FAILED }

    /**
     * @param stage    identifier of the current step, for the UI to translate; may be null
     * @param completed units done, meaningful only when {@code total} is positive
     */
    public record Job(
            Phase phase,
            String stage,
            int completed,
            int total,
            String error,
            Instance instance) {

        public boolean hasProgress() {
            return total > 0;
        }

        public int percent() {
            if (!hasProgress()) return -1;
            return Math.min(100, Math.max(0, (int) Math.round((completed * 100.0) / total)));
        }
    }

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
        jobs.put(jobId, new Job(Phase.FETCHING, "fetching", 0, 0, null, null));

        Thread.ofVirtual().name("modpack-install-" + jobId).start(() -> {
            AbstractModpackInstaller.PackInfo info = null;
            Instance instance = null;
            try {
                info = installer.fetchPackInfo(versionId);

                jobs.put(jobId, new Job(Phase.INSTALLING, "installing", 0, 0, null, null));
                String instanceName = name == null || name.isBlank() ? info.packName() : name;
                instance = launcher.createInstance(instanceName, info.versionId());

                Instance created = instance;
                launcher.busyRegistry().begin(created.name(), "installing modpack");
                jobs.put(jobId, new Job(Phase.DOWNLOADING_CONTENT, "downloading", 0, 0, null, created));

                installer.applyPackContents(created, info.archiveFile(), (stage, completed, total) -> {
                    jobs.put(jobId, new Job(Phase.DOWNLOADING_CONTENT, stage, completed, total, null, created));
                    launcher.busyRegistry().progress(created.name(), stage, completed, total);
                });

                launcher.busyRegistry().end(created.name());
                jobs.put(jobId, new Job(Phase.DONE, "done", 0, 0, null, created));
            } catch (Exception e) {
                // Roll back so a failed install never leaves a broken profile in the list. The
                // busy marker is cleared first, otherwise the delete would refuse itself.
                if (instance != null) {
                    launcher.busyRegistry().end(instance.name());
                    try { launcher.deleteInstance(instance.name()); } catch (Exception ignored) { /* best effort */ }
                }
                if (info != null) {
                    deleteQuietly(info.archiveFile());
                }
                jobs.put(jobId, new Job(Phase.FAILED, "failed", 0, 0,
                        e.getMessage() != null ? e.getMessage() : "Modpack installation failed", null));
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

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temp file in the system temp directory; the OS will reclaim it.
        }
    }
}
