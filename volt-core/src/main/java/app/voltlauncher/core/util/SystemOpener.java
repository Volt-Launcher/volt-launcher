package app.voltlauncher.core.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Hands a path or a URL to the desktop environment.
 *
 * <p>Deliberately avoids {@code java.awt.Desktop}. The launcher ships as a GraalVM native image,
 * where AWT is absent and those calls fail with a linkage {@link Error} rather than an exception —
 * so the previous implementation could not even be caught by a {@code catch (Exception)} and the
 * API answered with an empty HTTP 500. Spawning the platform's opener works in both the native
 * image and a plain JVM.
 */
public final class SystemOpener {

    /** How long to wait for an opener to fail. Successful ones detach and stay running. */
    private static final long FAILURE_WINDOW_MS = 1200;

    private SystemOpener() {}

    /** Opens a directory (creating it first) or a file in the desktop file manager. */
    public static void openPath(Path path) throws IOException {
        // A profile that has never been launched may not have its directory yet; creating it is
        // friendlier than refusing to show the user where their files will go.
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        run(commandsFor(path.toAbsolutePath().toString()), path.toString());
    }

    /** Opens a URL in the user's browser. */
    public static void openUrl(String url) throws IOException {
        if (url == null || url.isBlank()) throw new IOException("No URL to open");
        run(commandsFor(url), url);
    }

    private static List<List<String>> commandsFor(String target) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // "" is the (empty) window title start consumes before the target.
            return List.of(List.of("cmd", "/c", "start", "", target));
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of(List.of("open", target));
        }
        // A desktop without xdg-open on PATH is common enough to be worth falling through.
        List<List<String>> candidates = new ArrayList<>();
        candidates.add(List.of("xdg-open", target));
        candidates.add(List.of("gio", "open", target));
        candidates.add(List.of("kde-open", target));
        candidates.add(List.of("dolphin", target));
        candidates.add(List.of("nautilus", target));
        candidates.add(List.of("thunar", target));
        return candidates;
    }

    private static void run(List<List<String>> candidates, String target) throws IOException {
        IOException lastFailure = null;
        for (List<String> command : candidates) {
            try {
                Process process = new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // Openers detach immediately, so a process still running is the success case; one
                // that exits non-zero within the window refused, and the next candidate is tried.
                if (process.waitFor(FAILURE_WINDOW_MS, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
                    lastFailure = new IOException(command.getFirst() + " exited with " + process.exitValue());
                    continue;
                }
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while opening " + target, e);
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw new IOException("Could not open " + target
                + (lastFailure == null ? "" : ": " + lastFailure.getMessage()), lastFailure);
    }
}
