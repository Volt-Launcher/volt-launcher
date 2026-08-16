package app.voltlauncher.game.java;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads Java runtimes in the background so Settings can offer a one-click install without
 * blocking the request that starts it. A JDK download runs to a couple of hundred megabytes.
 */
public final class JavaInstallService {

    /** Major versions offered in the UI: the ones Minecraft has actually shipped against. */
    public static final List<Integer> OFFERED_VERSIONS = List.of(8, 11, 16, 17, 21, 25);

    public enum Phase { DOWNLOADING, DONE, FAILED }

    public record Job(int majorVersion, Phase phase, String message, String javaExecutable) {}

    private final JavaRuntimeResolver resolver;
    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();

    public JavaInstallService(JavaRuntimeResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Starts (or re-attaches to) an installation of the given major version.
     *
     * @return the job's current state
     */
    public Job install(int majorVersion) {
        if (majorVersion <= 0) {
            throw new IllegalArgumentException("Java major version must be positive");
        }

        Job existing = jobs.get(majorVersion);
        if (existing != null && existing.phase() == Phase.DOWNLOADING) {
            return existing;
        }

        Job started = new Job(majorVersion, Phase.DOWNLOADING, "Downloading Temurin JDK " + majorVersion + "…", null);
        jobs.put(majorVersion, started);

        Thread.ofVirtual().name("java-install-" + majorVersion).start(() -> {
            try {
                JavaRuntimeResolver.JavaRuntime runtime = resolver.installRuntime(majorVersion);
                jobs.put(majorVersion, new Job(majorVersion, Phase.DONE,
                        "Java " + majorVersion + " is ready", runtime.javaExecutable().toString()));
            } catch (Exception e) {
                jobs.put(majorVersion, new Job(majorVersion, Phase.FAILED,
                        e.getMessage() == null ? "Download failed" : e.getMessage(), null));
            }
        });
        return started;
    }

    public Job job(int majorVersion) {
        return jobs.get(majorVersion);
    }

    public List<Job> jobs() {
        return List.copyOf(jobs.values());
    }
}
