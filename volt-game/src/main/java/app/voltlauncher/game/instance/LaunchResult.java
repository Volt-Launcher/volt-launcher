package app.voltlauncher.game.instance;

public record LaunchResult(String instanceName, String versionId, long pid, String launchCommand, String logFile, int javaMajorVersion, String javaExecutable) {}

