package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;
import de.eztxm.thelauncherproject.util.HttpFetcher;
import de.eztxm.thelauncherproject.util.JsonUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class TemurinJdkDownloader {

    private static final String ADOPTIUM_API =
            "https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=%s&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=%s&vendor=eclipse";

    private record ReleaseAsset(String downloadUrl, String fileName, String sha256) {}

    private final HttpFetcher http = new HttpFetcher();

    public Path ensureDownloadedRuntime(int majorVersion) throws Exception {
        String os = detectOs();
        String arch = detectArch();
        String execName = isWindows() ? "java.exe" : "java";

        Path runtimeDir = AppPaths.temurinRuntimesDirectory()
                .resolve("jdk-" + majorVersion + "-" + os + "-" + arch);
        Path javaExec = runtimeDir.resolve("bin").resolve(execName);

        if (Files.exists(javaExec)) {
            applyPermissions(runtimeDir);
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
            extractArchive(archivePath, tempDir);
            Path extracted = findJavaExecutable(tempDir, execName);
            if (extracted == null) {
                throw new IllegalStateException("Temurin archive did not contain a Java executable");
            }
            recreateDirectory(runtimeDir);
            copyDirectory(extracted.getParent().getParent(), runtimeDir);
            applyPermissions(runtimeDir);
            if (!Files.exists(javaExec) || (!isWindows() && !Files.isExecutable(javaExec))) {
                throw new IllegalStateException("Java executable missing after extraction");
            }
        } finally {
            deleteSilently(tempDir);
        }
        return javaExec;
    }

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
        if (release.has("binary")) {
            return JsonUtil.requireObject(release, "binary.package");
        }
        JSONArray binaries = release.optJSONArray("binaries");
        if (binaries != null && !binaries.isEmpty()) {
            return binaries.getJSONObject(0).getJSONObject("package");
        }
        throw new IllegalStateException("Unexpected Adoptium API response format");
    }

    private void extractArchive(Path archive, Path targetDir) throws Exception {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            extractTarGz(archive, targetDir);
            return;
        }
        extractZip(archive, targetDir);
    }

    private void extractTarGz(Path archive, Path targetDir) throws Exception {
        try (InputStream fi = Files.newInputStream(archive);
             GZIPInputStream gi = new GZIPInputStream(fi);
             TarArchiveInputStream tar = new TarArchiveInputStream(gi)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!tar.canReadEntryData(entry)) {
                    continue;
                }
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Invalid tar path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void extractZip(Path archive, Path targetDir) throws Exception {
        try (InputStream fi = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(fi)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Invalid zip path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path findJavaExecutable(Path dir, String execName) throws Exception {
        try (var walk = Files.walk(dir)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(execName))
                    .filter(p -> p.getParent().getFileName().toString().equals("bin"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void copyDirectory(Path src, Path dst) throws IOException {
        try (var walk = Files.walk(src)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path target = dst.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void applyPermissions(Path runtimeDir) {
        if (isWindows() || !Files.isDirectory(runtimeDir)) {
            return;
        }
        Path binDir = runtimeDir.resolve("bin");
        if (!Files.isDirectory(binDir)) {
            return;
        }
        try (var walk = Files.walk(binDir, 1)) {
            walk.filter(Files::isRegularFile).forEach(this::applyExecPermission);
        } catch (IOException _) {}
    }

    private void applyExecPermission(Path path) {
        try {
            var perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException _) {}
    }

    private void recreateDirectory(Path dir) throws IOException {
        deleteSilently(dir);
        Files.createDirectories(dir);
    }

    private void deleteSilently(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException _) {}
            });
        } catch (Exception _) {}
    }

    private String detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) { return "windows"; }
        if (name.contains("mac") || name.contains("darwin")) { return "mac"; }
        return "linux";
    }

    private String detectArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) { return "aarch64"; }
        return "x64";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}