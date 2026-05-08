package app.voltlauncher.voltlauncher;

import java.nio.file.Path;

public final class AppPaths {

    private static final String APP_DIRECTORY = ".voltlauncher";

    private AppPaths() {
    }

    public static Path baseDirectory() {
        return Path.of(System.getProperty("user.home"), APP_DIRECTORY);
    }

    public static Path securityDirectory() {
        return baseDirectory().resolve("security");
    }

    public static Path authDirectory() {
        return baseDirectory().resolve("auth");
    }

    public static Path logsDirectory() {
        return baseDirectory().resolve("logs");
    }

    public static Path masterKeyPath() {
        return securityDirectory().resolve("master.key");
    }

    public static Path encryptedSessionPath() {
        return authDirectory().resolve("account-session.enc");
    }

    public static Path instancesDirectory() {
        return baseDirectory().resolve("instances");
    }

    public static Path instancesMetadataPath() {
        return instancesDirectory().resolve("instances.json");
    }

    public static Path instanceGameDirectory(String slug) {
        return instancesDirectory().resolve(slug).resolve("game");
    }

    public static Path runtimesDirectory() {
        return baseDirectory().resolve("runtimes");
    }

    public static Path temurinRuntimesDirectory() {
        return runtimesDirectory().resolve("temurin");
    }

    public static Path runtimeDownloadsDirectory() {
        return runtimesDirectory().resolve("downloads");
    }
}

