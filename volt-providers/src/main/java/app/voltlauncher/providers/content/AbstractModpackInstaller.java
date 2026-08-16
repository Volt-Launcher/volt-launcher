package app.voltlauncher.providers.content;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.providers.model.ProviderId;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared mechanics for installing a modpack archive: reading its manifest, downloading the files
 * it references and unpacking its override tree over the instance's game directory.
 */
public abstract class AbstractModpackInstaller {

    private static final int DOWNLOAD_CONCURRENCY = 8;

    protected final HttpFetcher http;

    protected AbstractModpackInstaller(HttpFetcher http) {
        this.http = http;
    }

    /** Metadata read from a downloaded pack, before an instance exists. */
    public record PackInfo(String versionId, String packName, Path archiveFile) {}

    /**
     * One file the pack pulls from the network. Public because Java only lets a subclass invoke a
     * protected constructor through {@code super()}, never with {@code new}.
     */
    public record RemoteFile(String relativePath, String url, String hash) {}

    public abstract ProviderId provider();

    /** Downloads the pack archive and reads the launcher version id and display name from it. */
    public abstract PackInfo fetchPackInfo(String versionId) throws Exception;

    /**
     * Installs the pack's contents into the instance. The archive is deleted afterwards.
     *
     * @param progress receives human-readable progress messages
     */
    public abstract void applyPackContents(Instance instance, Path archive, Consumer<String> progress) throws Exception;

    // ── shared helpers ────────────────────────────────────────────────────────

    /** Reads a JSON document from inside the pack archive. */
    protected JSONObject readJsonEntry(Path archive, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new JSONObject(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        throw new IllegalStateException(entryName + " not found in the modpack archive");
    }

    /**
     * Downloads every file in parallel. A failure is collected rather than thrown immediately so
     * the user gets one summary instead of whichever error happened to surface first.
     */
    protected void downloadFiles(Path gameDir, List<RemoteFile> files, Consumer<String> progress) throws Exception {
        if (files.isEmpty()) return;

        Semaphore permits = new Semaphore(DOWNLOAD_CONCURRENCY);
        List<Exception> errors = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        int total = files.size();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>(total);
            for (RemoteFile file : files) {
                Path destination = gameDir.resolve(file.relativePath()).normalize();
                if (!destination.startsWith(gameDir)) {
                    synchronized (errors) {
                        errors.add(new IllegalStateException(
                                "Modpack tried to write outside the instance: " + file.relativePath()));
                    }
                    continue;
                }

                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        permits.acquire();
                        try {
                            http.download(file.url(), destination, file.hash());
                        } finally {
                            permits.release();
                        }
                        int done = completed.incrementAndGet();
                        if (done % 10 == 0 || done == total) {
                            progress.accept("Downloading pack files (" + done + "/" + total + ")…");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        synchronized (errors) { errors.add(e); }
                    } catch (Exception e) {
                        synchronized (errors) { errors.add(e); }
                    }
                }, executor));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }

        synchronized (errors) {
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                        "Failed to download " + errors.size() + " of " + total + " pack files. First error: "
                                + errors.getFirst().getMessage(), errors.getFirst());
            }
        }
    }

    /**
     * Copies the pack's override trees over the game directory. Entries escaping the game
     * directory are refused rather than silently skipped.
     */
    protected void applyOverrides(Path gameDir, Path archive, List<String> prefixes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String relative = stripPrefix(entry.getName(), prefixes);
                if (relative == null || relative.isBlank() || entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }

                Path destination = gameDir.resolve(relative).normalize();
                if (!destination.startsWith(gameDir)) {
                    throw new IllegalStateException(
                            "Modpack override tried to write outside the instance: " + entry.getName());
                }
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                zip.closeEntry();
            }
        }
    }

    private String stripPrefix(String entryName, List<String> prefixes) {
        for (String prefix : prefixes) {
            String withSlash = prefix.endsWith("/") ? prefix : prefix + "/";
            if (entryName.startsWith(withSlash)) {
                return entryName.substring(withSlash.length());
            }
        }
        return null;
    }
}
