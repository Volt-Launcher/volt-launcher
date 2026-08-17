package app.voltlauncher.providers.content;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.MinecraftLauncherService;
import app.voltlauncher.game.instance.ContentSource;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.game.instance.InstanceContentService;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.store.ContentManifestStore;
import app.voltlauncher.providers.ContentProvider;
import app.voltlauncher.providers.ProviderRegistry;
import app.voltlauncher.providers.model.ProjectVersion;
import app.voltlauncher.providers.model.ProviderId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version management for the individual mods, shaders, resource packs and data packs inside a
 * profile: which release is installed, whether a newer one exists, and switching between them.
 *
 * <p>Everything here works off the provenance the content manifest records at install time. A file
 * with no recorded project — dropped in by hand, or shipped inside a pack's overrides — simply has
 * no versions to offer, which the UI shows as "untracked" rather than as an error.
 */
public final class ContentVersionService {

    /** An installed file together with the newest compatible release found for it. */
    public record UpdateCandidate(
            String contentType,
            String fileName,
            String projectId,
            String projectName,
            String currentVersionId,
            String currentVersionNumber,
            String latestVersionId,
            String latestVersionNumber,
            String latestFileName,
            String releaseDate) {}

    private final MinecraftLauncherService launcher;
    private final ProviderRegistry providers;
    private final HttpFetcher http;
    private final Map<String, ProjectDetails> projectCache = new ConcurrentHashMap<>();

    public ContentVersionService(MinecraftLauncherService launcher, ProviderRegistry providers, HttpFetcher http) {
        this.launcher = launcher;
        this.providers = providers;
        this.http = http;
    }

    /**
     * Matches files with no recorded origin against every available provider by their contents, and
     * records what comes back.
     *
     * <p>This is what rescues a jar the user dropped in by hand, or one a modpack shipped inside its
     * overrides: until its project is known the launcher can neither offer updates for it nor name
     * it in an export. Providers are asked in order and the first match wins, so a mod published on
     * both platforms is attributed to whichever answers first rather than being recorded twice.
     *
     * @return how many files were newly identified
     */
    public int identifyUntracked(String instanceName, InstanceContentService.ContentType type) throws Exception {
        Instance instance = launcher.findInstance(instanceName);
        ContentManifestStore manifests = launcher.contentManifests();
        ContentManifestStore.Manifest manifest = manifests.read(instance.slug());

        List<Path> unknown = new ArrayList<>();
        Map<Path, String> nameByPath = new LinkedHashMap<>();
        List<ContentSource> needLabels = new ArrayList<>();

        for (InstanceContentService.ContentEntry entry : launcher.listContent(instanceName, type)) {
            ContentSource existing = manifest.find(type.folder(), entry.fileName());
            if (existing != null && existing.isTracked()) {
                // Known project but no name or artwork yet — a modpack records its files from the
                // pack manifest, which carries neither. One cheap project lookup fills them in.
                if (existing.projectName().isBlank() || existing.iconUrl().isBlank()) {
                    needLabels.add(existing);
                }
                continue;
            }
            Path file = launcher.contentService().resolve(instanceName, type, entry.fileName());
            unknown.add(file);
            nameByPath.put(file, entry.fileName());
        }

        List<ContentSource> identified = new ArrayList<>(enrich(needLabels));
        if (unknown.isEmpty()) {
            manifests.record(instance.slug(), identified);
            return 0;
        }

        List<Path> remaining = new ArrayList<>(unknown);

        for (ContentProvider provider : providers.all()) {
            if (remaining.isEmpty()) break;
            if (!provider.isAvailable()) continue;
            Map<Path, ProjectVersion> matches;
            try {
                matches = provider.identify(remaining);
            } catch (Exception e) {
                // One provider being unreachable must not abandon the others.
                continue;
            }

            for (Map.Entry<Path, ProjectVersion> match : matches.entrySet()) {
                String fileName = nameByPath.get(match.getKey());
                if (fileName == null) continue;
                ProjectVersion version = match.getValue();
                ProjectDetails details = projectDetails(provider, version.projectId());

                ContentSource previous = manifest.find(type.folder(), fileName);
                identified.add(new ContentSource(
                        type.folder(), fileName, provider.id().id(), version.projectId(), version.versionId(),
                        details.title(), version.versionNumber(), version.downloadUrl(),
                        version.sha1() == null ? "" : version.sha1(), sizeOf(match.getKey()),
                        previous == null ? ContentSource.ORIGIN_MANUAL : previous.origin(),
                        details.iconUrl()));
            }
            remaining.removeAll(matches.keySet());
        }

        manifests.record(instance.slug(), identified);
        // Only newly-identified files count; label backfills are invisible housekeeping.
        return identified.size() - needLabels.size();
    }

    /**
     * Fills in the display name and icon for files whose project is known but unlabelled, which is
     * every file a modpack installed — its manifest carries ids, not artwork.
     */
    private List<ContentSource> enrich(List<ContentSource> sources) {
        List<ContentSource> enriched = new ArrayList<>();
        for (ContentSource source : sources) {
            ContentProvider provider;
            try {
                provider = providers.requireAvailable(ProviderId.fromId(source.provider()));
            } catch (Exception e) {
                continue;
            }
            ProjectDetails details = projectDetails(provider, source.projectId());
            if (details.title().isBlank() && details.iconUrl().isBlank()) continue;
            enriched.add(source.withProjectDetails(
                    source.projectName().isBlank() ? details.title() : source.projectName(),
                    details.iconUrl()));
        }
        return enriched;
    }

    /**
     * Every release of the project behind one installed file, newest first, filtered to what this
     * profile can actually run.
     */
    public List<ProjectVersion> versionsFor(String instanceName, InstanceContentService.ContentType type,
                                            String fileName) throws Exception {
        Instance instance = launcher.findInstance(instanceName);
        ContentSource source = requireTracked(instance, type, fileName);

        ContentProvider provider = providers.requireAvailable(ProviderId.fromId(source.provider()));
        return provider.versions(source.projectId(), gameVersionOf(instance), loaderOf(instance));
    }

    /**
     * Checks every tracked file of one kind for a newer compatible release.
     *
     * <p>A project that cannot be reached is skipped rather than failing the sweep — one delisted
     * mod must not hide updates for the other forty.
     */
    public List<UpdateCandidate> checkUpdates(String instanceName, InstanceContentService.ContentType type)
            throws Exception {
        Instance instance = launcher.findInstance(instanceName);
        String gameVersion = gameVersionOf(instance);
        String loader = loaderOf(instance);

        List<UpdateCandidate> candidates = new ArrayList<>();
        for (ContentSource source : launcher.contentManifests().read(instance.slug()).ofType(type.folder())) {
            if (!source.isTracked()) continue;
            try {
                ContentProvider provider = providers.requireAvailable(ProviderId.fromId(source.provider()));
                ProjectVersion latest = VersionOffer.pick(
                        provider.versions(source.projectId(), gameVersion, loader), source.versionId());
                if (latest == null || latest.versionId().equals(source.versionId())) continue;

                candidates.add(new UpdateCandidate(
                        source.contentType(), source.fileName(), source.projectId(),
                        displayName(source), source.versionId(), source.versionNumber(),
                        latest.versionId(), latest.versionNumber(), latest.fileName(),
                        latest.datePublished()));
            } catch (Exception ignored) {
                // Provider offline or project delisted — nothing to offer for this one file.
            }
        }
        return candidates;
    }

    /**
     * Replaces an installed file with another release of the same project.
     *
     * <p>The new file is downloaded before the old one is deleted, so a failed download leaves the
     * profile exactly as it was. A file the user had disabled stays disabled.
     */
    public ContentSource changeVersion(String instanceName, InstanceContentService.ContentType type,
                                       String fileName, String targetVersionId) throws Exception {
        launcher.busyRegistry().requireIdle(instanceName);
        Instance instance = launcher.findInstance(instanceName);
        ContentSource source = requireTracked(instance, type, fileName);

        ContentProvider provider = providers.requireAvailable(ProviderId.fromId(source.provider()));
        ProjectVersion target = provider.version(targetVersionId);
        if (!target.isDownloadable()) {
            throw new IllegalStateException(
                    "This release cannot be downloaded automatically because its author disabled "
                            + "third-party downloads.");
        }
        if (!target.projectId().equals(source.projectId())) {
            throw new IllegalArgumentException("That release belongs to a different project.");
        }

        Path current = launcher.contentService().resolve(instanceName, type, fileName);
        boolean wasDisabled = current.getFileName().toString().endsWith(".disabled");

        String newName = sanitize(target.fileName() == null || target.fileName().isBlank()
                ? target.versionId() + ".jar"
                : target.fileName());
        Path directory = instance.gameDirectory().resolve(type.folder());
        Path destination = directory.resolve(wasDisabled ? newName + ".disabled" : newName);

        launcher.busyRegistry().begin(instance.name(), "changing version");
        try {
            Files.createDirectories(directory);
            http.download(target.downloadUrl(), destination, target.sha1() == null ? "" : target.sha1());

            // Only now is the old jar safe to drop; keeping it would load two copies of the mod.
            if (!destination.equals(current)) {
                Files.deleteIfExists(current);
            }

            // Fill in the project's name and icon if they were never recorded — a file identified
            // by hash, or installed before provenance was tracked, has neither yet.
            ProjectDetails details = source.projectName().isBlank() || source.iconUrl().isBlank()
                    ? projectDetails(provider, target.projectId())
                    : new ProjectDetails(source.projectName(), source.iconUrl());

            ContentSource updated = new ContentSource(
                    type.folder(), newName, source.provider(), target.projectId(), target.versionId(),
                    details.title().isBlank() ? displayName(source) : details.title(),
                    target.versionNumber(), target.downloadUrl(),
                    target.sha1() == null ? "" : target.sha1(), Files.size(destination),
                    source.origin(), details.iconUrl());
            launcher.contentManifests().rename(instance.slug(), type.folder(), fileName, updated);
            return updated;
        } finally {
            launcher.busyRegistry().end(instance.name());
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    /** A project's display name and icon, as shown next to an installed file. */
    record ProjectDetails(String title, String iconUrl) {
        static final ProjectDetails NONE = new ProjectDetails("", "");
    }

    /**
     * Looks up a project's presentation details, cached for the process lifetime. Identifying a
     * folder of eighty mods otherwise means eighty project requests, many for the same project.
     */
    ProjectDetails projectDetails(ContentProvider provider, String projectId) {
        if (projectId == null || projectId.isBlank()) return ProjectDetails.NONE;
        String key = provider.id().id() + ":" + projectId;
        return projectCache.computeIfAbsent(key, unused -> {
            try {
                var summary = provider.project(projectId).summary();
                return new ProjectDetails(summary.title(), summary.iconUrl() == null ? "" : summary.iconUrl());
            } catch (Exception e) {
                return ProjectDetails.NONE;
            }
        });
    }

    private long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (Exception e) {
            return 0L;
        }
    }

    private ContentSource requireTracked(Instance instance, InstanceContentService.ContentType type, String fileName)
            throws Exception {
        ContentSource source = launcher.contentManifests().read(instance.slug()).find(type.folder(), fileName);
        if (source == null || !source.isTracked()) {
            throw new IllegalStateException(
                    "The launcher does not know where '" + fileName + "' came from, so it cannot manage "
                            + "its versions. Re-install it from Discover to track it.");
        }
        return source;
    }

    private String displayName(ContentSource source) {
        return source.projectName().isBlank() ? source.fileName() : source.projectName();
    }

    private String gameVersionOf(Instance instance) {
        return PlatformRegistry.extractBaseMinecraftVersionId(instance.versionId());
    }

    /** The loader a profile runs, or {@code null} for vanilla profiles. */
    private String loaderOf(Instance instance) {
        String versionId = instance.versionId();
        if (versionId == null) return null;
        int separator = versionId.indexOf(':');
        if (separator <= 0) return null;
        String loader = versionId.substring(0, separator).toLowerCase(Locale.ROOT);
        return PlatformRegistry.VANILLA_ID.equals(loader) ? null : loader;
    }

    private String sanitize(String fileName) {
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Refusing to install file with unusable name: " + fileName);
        }
        return name;
    }
}
