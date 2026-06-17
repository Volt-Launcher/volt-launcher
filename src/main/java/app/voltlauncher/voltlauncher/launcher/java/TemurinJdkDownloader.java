package app.voltlauncher.voltlauncher.launcher.java;

import app.voltlauncher.voltlauncher.AppPaths;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import app.voltlauncher.voltlauncher.util.JsonUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Locale;

public final class TemurinJdkDownloader {

    private static final String ADOPTIUM_API =
            "https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=%s&vendor=eclipse";

    private final HttpFetcher http = new HttpFetcher();

    public Path ensureDownloadedRuntime(int majorVersion) throws Exception {
        String os = detectOs();
        String arch = detectArch();
        String execName = isWindows() ? "java.exe" : "java";

        Path runtimeDir = AppPaths.temurinRuntimesDirectory()
                .resolve("jdk-" + majorVersion + "-" + os + "-" + arch);
        Path javaExec = runtimeDir.resolve("bin").resolve(execName);

        if (Files.exists(javaExec)) {
            applyBinPermissions(runtimeDir);
            return javaExec;
        }

        Files.createDirectories(AppPaths.temurinRuntimesDirectory());
        Files.createDirectories(AppPaths.runtimeDownloadsDirectory());

        ReleaseAsset asset = fetchAsset(majorVersion, os, arch);
        Path archivePath = AppPaths.runtimeDownloadsDirectory().resolve(asset.fileName());
        http.download(asset.downloadUrl(), archivePath, asset.sha256());

        Path tempDir = AppPaths.temurinRuntimesDirectory()
                .resolve(runtimeDir.getFileName().toString() + ".tmp");
        recreateDirectory(tempDir);
        try {
            JdkArchiveExtractor.extract(archivePath, tempDir);
            Path extracted = JdkArchiveExtractor.findJavaExecutable(tempDir, execName);
            if (extracted == null) {
                throw new IllegalStateException("Temurin archive did not contain a Java executable");
            }
            recreateDirectory(runtimeDir);
            JdkArchiveExtractor.copyDirectory(extracted.getParent().getParent(), runtimeDir);
            applyBinPermissions(runtimeDir);
            if (!Files.exists(javaExec) || (!isWindows() && !Files.isExecutable(javaExec))) {
                throw new IllegalStateException("Java executable missing after extraction");
            }
        } finally {
            deleteSilently(tempDir);
        }
        return javaExec;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ReleaseAsset fetchAsset(int majorVersion, String os, String arch) throws Exception {
        String url = String.format(ADOPTIUM_API, majorVersion, arch, os);
        JSONArray releases = http.getJsonArray(url);
        if (releases.isEmpty()) {
            throw new IllegalStateException(
                    "No Temurin JDK found for Java " + majorVersion + " on " + os + "/" + arch);
        }
        JSONObject pkg = resolvePackage(releases.getJSONObject(0));
        return new ReleaseAsset(
                JsonUtil.requireString(pkg, "link"),
                JsonUtil.requireString(pkg, "name"),
                pkg.optString("checksum", ""));
    }

    private JSONObject resolvePackage(JSONObject release) {
        if (release.has("binary")) return JsonUtil.requireObject(release, "binary.package");
        JSONArray binaries = release.optJSONArray("binaries");
        if (binaries != null && !binaries.isEmpty()) {
            return binaries.getJSONObject(0).getJSONObject("package");
        }
        throw new IllegalStateException("Unexpected Adoptium API response format");
    }

    private void applyBinPermissions(Path runtimeDir) {
        if (isWindows() || !Files.isDirectory(runtimeDir)) return;
        Path binDir = runtimeDir.resolve("bin");
        if (!Files.isDirectory(binDir)) return;
        try (var walk = Files.walk(binDir, 1)) {
            walk.filter(Files::isRegularFile).forEach(this::markExecutable);
        } catch (IOException ignored) {}
    }

    private void markExecutable(Path path) {
        try {
            var perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {}
    }

    private void recreateDirectory(Path dir) throws IOException {
        deleteSilently(dir);
        Files.createDirectories(dir);
    }

    private void deleteSilently(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private String detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac") || name.contains("darwin")) return "mac";
        return "linux";
    }

    private String detectArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return (arch.contains("aarch64") || arch.contains("arm64")) ? "aarch64" : "x64";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record ReleaseAsset(String downloadUrl, String fileName, String sha256) {}
}
