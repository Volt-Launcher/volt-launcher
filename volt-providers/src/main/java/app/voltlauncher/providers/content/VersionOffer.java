package app.voltlauncher.providers.content;

import app.voltlauncher.providers.model.ProjectVersion;

import java.util.List;

/**
 * Chooses which release to offer as "the update" for something already installed.
 *
 * <p>Providers return their versions newest-first, and that ordering is the only reliable way to
 * compare two releases: version strings across Modrinth and CurseForge follow no shared scheme, so
 * parsing them would be guesswork. Two rules fall out of it:
 *
 * <ul>
 *   <li>Never offer something the provider lists as <em>older</em> than what is installed. Picking
 *       "the newest stable" alone is not enough — a project whose latest stable predates the
 *       installed pre-release would otherwise be offered as a downgrade.</li>
 *   <li>Do not push a user from a stable build onto a pre-release. Someone already running a
 *       pre-release does get offered newer ones, and the full version list stays available for
 *       anyone who wants to pick a specific build.</li>
 * </ul>
 */
final class VersionOffer {

    private VersionOffer() {}

    private static final String RELEASE = "release";

    /**
     * @param versions         the provider's list for this project, newest first
     * @param currentVersionId the installed release, or blank when it is unknown
     * @return the version to offer, or {@code null} when nothing better is available
     */
    static ProjectVersion pick(List<ProjectVersion> versions, String currentVersionId) {
        int current = indexOf(versions, currentVersionId);

        // The installed build is not in this list — it may target another game version, so treat
        // the newest stable as a genuine upgrade path rather than assuming it is a downgrade.
        int limit = current < 0 ? versions.size() : current;
        boolean currentIsPrerelease = current >= 0 && !isRelease(versions.get(current));

        ProjectVersion newestPrerelease = null;
        for (int i = 0; i < limit; i++) {
            ProjectVersion candidate = versions.get(i);
            if (!candidate.isDownloadable()) continue;
            if (isRelease(candidate)) return candidate;
            if (newestPrerelease == null) newestPrerelease = candidate;
        }
        return currentIsPrerelease ? newestPrerelease : null;
    }

    private static int indexOf(List<ProjectVersion> versions, String versionId) {
        if (versionId == null || versionId.isBlank()) return -1;
        for (int i = 0; i < versions.size(); i++) {
            if (versionId.equals(versions.get(i).versionId())) return i;
        }
        return -1;
    }

    private static boolean isRelease(ProjectVersion version) {
        return RELEASE.equalsIgnoreCase(version.releaseType());
    }
}
