package app.voltlauncher.voltlauncher.launcher.instance;

import app.voltlauncher.voltlauncher.AppPaths;

import java.nio.file.Path;

public record Instance( String name, String slug, String versionId, String versionType, long createdAt, long lastPlayedAt, int javaMajorVersion,
String javaComponent, InstanceSettings settings) {

    public Instance {
        if (settings == null) settings = InstanceSettings.defaults();
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
}
