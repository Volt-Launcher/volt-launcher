package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;

import java.nio.file.Path;

public record LauncherInstance(
        String name,
        String slug,
        String versionId,
        String versionType,
        long createdAt,
        long lastPlayedAt,
        int javaMajorVersion,
        String javaComponent) {

    public Path gameDirectory() {
        return AppPaths.instanceGameDirectory(slug);
    }

    public boolean hasJavaRequirement() {
        return javaMajorVersion > 0;
    }
}

