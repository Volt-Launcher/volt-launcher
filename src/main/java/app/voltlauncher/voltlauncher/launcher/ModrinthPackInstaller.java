package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ModrinthPackInstaller {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final int MOD_CONCURRENCY = 8;
    private static final Map<String, String> API_HEADERS = Map.of(
        "User-Agent", "Volt-Launcher/volt-launcher"
    );

    private final HttpFetcher http;

    public ModrinthPackInstaller(HttpFetcher http) {
        this.http = http;
    }

    /** Resolved info needed to create the Instance before installing mods. */
    public record PackInfo(
        String versionId,     // e.g. "fabric:1.20.1:0.14.22"
        String packName,      // human name from modrinth.index.json
        Path mrpackFile       // temp file – caller must delete after use
    ) {}

    /**
     * Downloads a .mrpack file for the given Modrinth version ID and returns the
     * metadata needed to create an Instance. The mrpack temp file is returned so
     * the caller can later call {@link #applyPackContents(Instance, Path)}.
     */
    public PackInfo fetchPackInfo(String modrinthVersionId) throws Exception {
        JSONObject versionMeta = http.getJson(
            MODRINTH_API + "/version/" + modrinthVersionId, API_HEADERS
        );

        String mrpackUrl = null;
        String mrpackSha1 = null;
        JSONArray files = versionMeta.optJSONArray("files");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                if (f.optBoolean("primary", false)
                        || (mrpackUrl == null && f.optString("filename", "").endsWith(".mrpack"))) {
                    mrpackUrl = f.getString("url");
                    mrpackSha1 = f.optJSONObject("hashes") != null
                        ? f.getJSONObject("hashes").optString("sha1", "")
                        : "";
                    if (f.optBoolean("primary", false)) break;
                }
            }
        }

        if (mrpackUrl == null) {
            throw new IllegalStateException("No .mrpack file found for Modrinth version: " + modrinthVersionId);
        }

        Path tmp = Files.createTempFile("voltlauncher-pack-", ".mrpack");
        try {
            http.download(mrpackUrl, tmp, mrpackSha1 != null ? mrpackSha1 : "");
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }

        JSONObject index = readIndexFromMrpack(tmp);
        String versionId = resolveVersionId(index);
        String packName = index.optString("name", "Modpack");

        return new PackInfo(versionId, packName, tmp);
    }

    /**
     * Installs mod files and overrides from a .mrpack into the instance's game directory.
     * The mrpack temp file is deleted when done.
     */
    public void applyPackContents(Instance instance, Path mrpackFile) throws Exception {
        try {
            JSONObject index = readIndexFromMrpack(mrpackFile);
            downloadMods(instance.gameDirectory(), index.optJSONArray("files"));
            applyOverrides(instance.gameDirectory(), mrpackFile);
        } finally {
            Files.deleteIfExists(mrpackFile);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private JSONObject readIndexFromMrpack(Path mrpackFile) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(mrpackFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("modrinth.index.json".equals(entry.getName())) {
                    byte[] bytes = zip.readAllBytes();
                    return new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        }
        throw new IllegalStateException("modrinth.index.json not found in .mrpack");
    }

    private String resolveVersionId(JSONObject index) {
        JSONObject deps = index.optJSONObject("dependencies");
        if (deps == null) {
            throw new IllegalStateException("No dependencies found in modrinth.index.json");
        }

        String mcVersion = deps.optString("minecraft", "");
        if (mcVersion.isBlank()) {
            throw new IllegalStateException("No minecraft dependency in modrinth.index.json");
        }

        // Prefer in order: neoforge, fabric, forge, quilt
        if (deps.has("neoforge")) {
            return "neoforge:" + mcVersion + ":" + deps.getString("neoforge");
        }
        if (deps.has("fabric-loader")) {
            return "fabric:" + mcVersion + ":" + deps.getString("fabric-loader");
        }
        if (deps.has("forge")) {
            return "forge:" + mcVersion + ":" + deps.getString("forge");
        }
        if (deps.has("quilt-loader")) {
            return "quilt:" + mcVersion + ":" + deps.getString("quilt-loader");
        }

        // Vanilla modpack
        return mcVersion;
    }

    private void downloadMods(Path gameDir, JSONArray files) throws Exception {
        if (files == null || files.isEmpty()) return;

        Semaphore sem = new Semaphore(MOD_CONCURRENCY);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String relPath = file.optString("path", "");
                if (relPath.isBlank()) continue;

                JSONObject hashes = file.optJSONObject("hashes");
                String hash = hashes != null
                    ? (hashes.has("sha512") ? hashes.getString("sha512") : hashes.optString("sha1", ""))
                    : "";

                JSONArray downloads = file.optJSONArray("downloads");
                if (downloads == null || downloads.isEmpty()) continue;
                String url = downloads.getString(0);

                // Resolve destination — path in mrpack is relative to game dir
                Path dest = gameDir.resolve(relPath).normalize();
                if (!dest.startsWith(gameDir)) continue; // path traversal guard

                sem.acquire();
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        http.download(url, dest, hash);
                    } catch (Exception e) {
                        synchronized (errors) { errors.add(e); }
                    } finally {
                        sem.release();
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        if (!errors.isEmpty()) {
            throw new Exception("Failed to download " + errors.size() + " mod file(s): " + errors.get(0).getMessage(), errors.get(0));
        }
    }

    private void applyOverrides(Path gameDir, Path mrpackFile) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(mrpackFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                String relative = null;
                if (name.startsWith("overrides/")) {
                    relative = name.substring("overrides/".length());
                } else if (name.startsWith("client-overrides/")) {
                    relative = name.substring("client-overrides/".length());
                }

                if (relative != null && !relative.isBlank() && !entry.isDirectory()) {
                    Path dest = gameDir.resolve(relative).normalize();
                    if (!dest.startsWith(gameDir)) { zip.closeEntry(); continue; } // path traversal guard
                    Files.createDirectories(dest.getParent());
                    Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
