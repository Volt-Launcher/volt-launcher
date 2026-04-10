package app.voltlauncher.voltlauncher.launcher.platform;

import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

import java.util.ArrayList;
import java.util.List;

public interface IPlatform {

    String id();

    String displayName();

    IVersionResolver versionResolver();

    default List < AvailableVersion> listVersions( boolean includeSnapshots, boolean includeBetas,
    boolean includeAlphas) throws Exception {
        List < AvailableVersion> filtered = new ArrayList <> ();
        for (AvailableVersion version : versionResolver().listAvailableVersions()) {
            if (shouldInclude(version.type(), includeSnapshots, includeBetas, includeAlphas)) {
                filtered.add(version);
            }
        }
        return filtered;
    }

    private boolean shouldInclude(String type, boolean includeSnapshots, boolean includeBetas, boolean includeAlphas) {
        return switch (type) {
            case "release" -> true;
            case "snapshot" -> includeSnapshots;
            case "old_beta" -> includeBetas;
            case "old_alpha" -> includeAlphas;
            default -> false;
        };
    }
}

