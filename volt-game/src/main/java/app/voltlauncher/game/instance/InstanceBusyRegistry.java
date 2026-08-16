package app.voltlauncher.game.instance;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks instances undergoing a long-running operation.
 *
 * <p>A modpack install creates its profile early and then spends minutes downloading into it. For
 * that whole window the profile is visible but not yet playable, so launching, renaming, deleting
 * or editing it would act on a half-installed directory. Operations consult this registry and
 * refuse rather than corrupting the instance, and the UI uses the same state to disable its
 * controls and show progress.
 */
public final class InstanceBusyRegistry {

    /**
     * @param completed units finished so far; meaningful only when {@code total} is positive
     * @param total     total units of work, or 0 when the operation cannot report a count
     */
    public record Activity(String reason, int completed, int total, long startedAt) {

        public boolean hasProgress() {
            return total > 0;
        }

        /** Completion as a percentage, or -1 when the work is not countable. */
        public int percent() {
            if (!hasProgress()) return -1;
            return Math.min(100, Math.max(0, (int) Math.round((completed * 100.0) / total)));
        }
    }

    private final ConcurrentHashMap<String, Activity> active = new ConcurrentHashMap<>();

    public void begin(String instanceName, String reason) {
        active.put(key(instanceName), new Activity(reason, 0, 0, System.currentTimeMillis()));
    }

    /** Updates the message and counters of an in-flight activity, preserving its start time. */
    public void progress(String instanceName, String reason, int completed, int total) {
        active.compute(key(instanceName), (unused, current) -> new Activity(
                reason, completed, total,
                current == null ? System.currentTimeMillis() : current.startedAt()));
    }

    public void end(String instanceName) {
        active.remove(key(instanceName));
    }

    public Activity get(String instanceName) {
        return instanceName == null ? null : active.get(key(instanceName));
    }

    public boolean isBusy(String instanceName) {
        return get(instanceName) != null;
    }

    /**
     * @throws IllegalStateException if the instance is mid-operation, which the API surfaces as a
     *                               409 rather than letting the caller corrupt the install
     */
    public void requireIdle(String instanceName) {
        Activity activity = get(instanceName);
        if (activity != null) {
            throw new IllegalStateException(
                    "This profile is still being set up (" + activity.reason() + "). Wait for it to finish.");
        }
    }

    private String key(String instanceName) {
        return instanceName.trim().toLowerCase(Locale.ROOT);
    }
}
