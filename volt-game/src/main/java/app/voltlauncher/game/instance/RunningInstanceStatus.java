package app.voltlauncher.game.instance;

public record RunningInstanceStatus(String instanceName, String versionId, long pid, long startedAt, boolean alive, int javaMajorVersion, String javaExecutable) {}

