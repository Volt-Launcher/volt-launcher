package app.voltlauncher.game.instance;

import app.voltlauncher.core.AppPaths;

import java.nio.file.Path;

/**
 * A launcher profile. The {@code slug} is the stable on-disk identity and never changes once
 * created, so renaming a profile leaves its game directory (and the user's worlds, mods and
 * configs) untouched.
 */
public record Instance(
        String name,
        String slug,
        String versionId,
        String versionType,
        long createdAt,
        long lastPlayedAt,
        int javaMajorVersion,
        String javaComponent,
        InstanceSettings settings) {

    public Instance {
        if (settings == null) settings = InstanceSettings.defaults();
    }

    public Path directory() {
        return AppPaths.instanceDirectory(slug);
    }

    public Path gameDirectory() {
        return AppPaths.instanceGameDirectory(slug);
    }

    public boolean hasJavaRequirement() {
        return javaMajorVersion > 0;
    }

    public Instance withSettings(InstanceSettings newSettings) {
        return new Instance(name, slug, versionId, versionType, createdAt, lastPlayedAt,
                javaMajorVersion, javaComponent, newSettings);
    }

    public Instance withName(String newName) {
        return new Instance(newName, slug, versionId, versionType, createdAt, lastPlayedAt,
                javaMajorVersion, javaComponent, settings);
    }

    public Instance withLastPlayedAt(long timestamp) {
        return new Instance(name, slug, versionId, versionType, createdAt, timestamp,
                javaMajorVersion, javaComponent, settings);
    }

    /** Re-points a profile at another game version, as a modpack update across versions does. */
    public Instance withVersion(String newVersionId, String newVersionType, int majorVersion, String component) {
        return new Instance(name, slug, newVersionId, newVersionType, createdAt, lastPlayedAt,
                majorVersion, component, settings);
    }

    public Instance withJavaRequirement(int majorVersion, String component) {
        return new Instance(name, slug, versionId, versionType, createdAt, lastPlayedAt,
                majorVersion, component, settings);
    }
}
