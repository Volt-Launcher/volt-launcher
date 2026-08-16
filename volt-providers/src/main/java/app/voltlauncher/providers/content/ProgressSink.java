package app.voltlauncher.providers.content;

/**
 * Receives progress from a long-running install.
 *
 * <p>Carries counts rather than a pre-formatted string so the UI can render a real progress bar
 * and localise the wording, instead of displaying whatever English the installer happened to
 * build.
 */
@FunctionalInterface
public interface ProgressSink {

    /**
     * @param stage     what is happening, as a stable identifier the UI can translate
     * @param completed units finished so far
     * @param total     total units, or 0 when the work cannot be counted yet
     */
    void update(String stage, int completed, int total);

    /** Reports a stage change with no countable work attached. */
    default void stage(String stage) {
        update(stage, 0, 0);
    }

    ProgressSink NOOP = (stage, completed, total) -> { };
}
