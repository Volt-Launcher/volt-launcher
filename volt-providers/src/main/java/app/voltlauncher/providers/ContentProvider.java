package app.voltlauncher.providers;

import app.voltlauncher.providers.model.ContentKind;
import app.voltlauncher.providers.model.ProjectDetail;
import app.voltlauncher.providers.model.ProjectVersion;
import app.voltlauncher.providers.model.ProviderId;
import app.voltlauncher.providers.model.SearchQuery;
import app.voltlauncher.providers.model.SearchResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A source of installable Minecraft content. Implementations normalise their upstream API onto
 * the shared model so the UI and the install pipeline stay provider-agnostic.
 */
public interface ContentProvider {

    ProviderId id();

    /**
     * Whether the provider can currently serve requests. CurseForge, for example, is only
     * available once its bridge service is reachable.
     */
    default boolean isAvailable() {
        return true;
    }

    /** Human-readable explanation shown when {@link #isAvailable()} is false. */
    default String unavailableReason() {
        return "";
    }

    /** Content kinds this provider can serve. */
    default List<ContentKind> supportedKinds() {
        return List.of(ContentKind.values());
    }

    SearchResult search(SearchQuery query) throws Exception;

    ProjectDetail project(String projectIdOrSlug) throws Exception;

    /**
     * Versions of a project, newest first. {@code gameVersion} and {@code loader} are optional
     * filters; either may be {@code null}.
     */
    List<ProjectVersion> versions(String projectIdOrSlug, String gameVersion, String loader) throws Exception;

    /** A single version by id, used when installing a specific pinned release. */
    ProjectVersion version(String versionId) throws Exception;

    /**
     * Identifies local files by their contents, so a jar the launcher did not install itself can
     * still be matched back to the project it belongs to.
     *
     * <p>Each provider hashes the file the way its own lookup expects — Modrinth by SHA-1,
     * CurseForge by its murmur2 fingerprint — which is why this takes paths rather than a hash.
     *
     * @return the files that matched, keyed by the path passed in; unmatched files are absent
     */
    default Map<Path, ProjectVersion> identify(List<Path> files) throws Exception {
        return Map.of();
    }
}
