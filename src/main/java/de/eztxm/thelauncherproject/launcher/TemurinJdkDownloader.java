package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TemurinJdkDownloader {

    private final OkHttpClient httpClient = new OkHttpClient();

    public Path ensureDownloadedRuntime(int majorVersion) throws Exception {
        String os = detectTemurinOs();
        String architecture = detectTemurinArchitecture();
        String executableName = isWindows() ? "java.exe" : "java";

        Path runtimeDirectory = AppPaths.temurinRuntimesDirectory()
                .resolve("jdk-" + majorVersion + "-" + os + "-" + architecture);
        Path javaExecutable = runtimeDirectory.resolve("bin").resolve(executableName);
        if (Files.exists(javaExecutable)) {
            applyRuntimePermissions(runtimeDirectory);
            return javaExecutable;
        }

        Files.createDirectories(AppPaths.temurinRuntimesDirectory());
        Files.createDirectories(AppPaths.runtimeDownloadsDirectory());

        ReleaseAsset asset = fetchLatestAsset(majorVersion, os, architecture);
        Path archivePath = AppPaths.runtimeDownloadsDirectory().resolve(asset.fileName());
        downloadFile(asset.downloadUrl(), archivePath, asset.sha256());

        Path tempExtractDirectory = AppPaths.temurinRuntimesDirectory()
                .resolve(runtimeDirectory.getFileName().toString() + ".tmp");
        recreateDirectory(tempExtractDirectory);
        try {
            extractArchive(archivePath, tempExtractDirectory);

            Path extractedJava = findJavaExecutable(tempExtractDirectory, executableName);
            if (extractedJava == null) {
                throw new IllegalStateException("Downloaded Temurin archive did not contain a Java executable");
            }

            Path extractedRoot = extractedJava.getParent().getParent();
            recreateDirectory(runtimeDirectory);
            copyDirectory(extractedRoot, runtimeDirectory);
            applyRuntimePermissions(runtimeDirectory);

            if (!Files.exists(javaExecutable) || (!isWindows() && !Files.isExecutable(javaExecutable))) {
                throw new IllegalStateException("Temurin runtime was extracted but java executable is missing or not executable");
            }
        } finally {
            deleteDirectoryIfExists(tempExtractDirectory);
        }

        return javaExecutable;
    }

    private ReleaseAsset fetchLatestAsset(int majorVersion, String os, String architecture) throws Exception {
        String url = "https://api.adoptium.net/v3/assets/latest/" + majorVersion
                + "/hotspot?architecture=" + architecture
                + "&heap_size=normal"
                + "&image_type=jdk"
                + "&jvm_impl=hotspot"
                + "&os=" + os
                + "&vendor=eclipse";

        JSONObject release = getFirstRelease(url);
        JSONObject packageJson = getPackageJson(release);
        return new ReleaseAsset(
                packageJson.getString("link"),
                packageJson.getString("name"),
                packageJson.optString("checksum", ""));
    }

    private JSONObject getFirstRelease(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            String content = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to query Adoptium API: HTTP " + response.code());
            }

            JSONArray releases = new JSONArray(content);
            if (releases.isEmpty()) {
                throw new IllegalStateException("No Temurin JDK release found for this platform and Java version");
            }
            return releases.getJSONObject(0);
        }
    }

    private JSONObject getPackageJson(JSONObject release) {
        if (release.has("binary")) {
            return release.getJSONObject("binary").getJSONObject("package");
        }
        if (release.has("binaries")) {
            JSONArray binaries = release.getJSONArray("binaries");
            if (!binaries.isEmpty()) {
                return binaries.getJSONObject(0).getJSONObject("package");
            }
        }
        throw new IllegalStateException("Unexpected Adoptium API response format");
    }

    private void downloadFile(String url, Path target, String expectedSha256) throws Exception {
        if (Files.exists(target) && sha256Matches(target, expectedSha256)) {
            return;
        }

        Files.createDirectories(target.getParent());
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to download Temurin JDK: HTTP " + response.code());
            }

            Path tempFile = Files.createTempFile(target.getParent(), "temurin-", ".tmp");
            try (InputStream inputStream = response.body().byteStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!sha256Matches(tempFile, expectedSha256)) {
                Files.deleteIfExists(tempFile);
                throw new IOException("SHA-256 mismatch for downloaded Temurin JDK archive");
            }

            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean sha256Matches(Path file, String expectedSha256) throws Exception {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return Files.exists(file);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString().equalsIgnoreCase(expectedSha256);
    }

    private void extractArchive(Path archive, Path targetDirectory) throws Exception {
        String fileName = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            extractZip(archive, targetDirectory);
            return;
        }
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            extractTarGz(archive, targetDirectory);
            return;
        }
        throw new IllegalStateException("Unsupported Temurin archive format: " + fileName);
    }

    private void extractZip(Path archive, Path targetDirectory) throws Exception {
        try (InputStream inputStream = Files.newInputStream(archive);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path output = normalizeExtractedPath(targetDirectory, entry.getName());
                if (output == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zipInputStream, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void extractTarGz(Path archive, Path targetDirectory) throws Exception {
        try (InputStream inputStream = Files.newInputStream(archive);
             GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
             TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                Path output = normalizeExtractedPath(targetDirectory, entry.getName());
                if (output == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(tarInputStream, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path normalizeExtractedPath(Path targetDirectory, String originalEntryName) throws IOException {
        String normalized = originalEntryName.replace('\\', '/');
        int firstSlash = normalized.indexOf('/');
        String relativeName = firstSlash >= 0 ? normalized.substring(firstSlash + 1) : normalized;
        if (relativeName.isBlank()) {
            return null;
        }

        Path output = targetDirectory.resolve(relativeName).normalize();
        if (!output.startsWith(targetDirectory)) {
            throw new IOException("Invalid archive entry path: " + originalEntryName);
        }
        return output;
    }

    private Path findJavaExecutable(Path directory, String executableName) throws IOException {
        try (var walk = Files.walk(directory, 6)) {
            return walk.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().equals(executableName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            walk.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
        Files.createDirectories(directory);
    }

    private void applyExecutablePermissions(Path executable) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(executable, permissions);
        } catch (Exception ignored) {
        }
    }

    private void applyRuntimePermissions(Path runtimeDirectory) {
        if (isWindows() || !Files.isDirectory(runtimeDirectory)) {
            return;
        }

        List<Path> executables = List.of(
                runtimeDirectory.resolve("bin").resolve("java"),
                runtimeDirectory.resolve("bin").resolve("keytool"),
                runtimeDirectory.resolve("lib").resolve("jspawnhelper"));

        for (Path executable : executables) {
            if (Files.exists(executable)) {
                applyExecutablePermissions(executable);
            }
        }

        Path binDirectory = runtimeDirectory.resolve("bin");
        if (!Files.isDirectory(binDirectory)) {
            return;
        }

        try (var walk = Files.walk(binDirectory, 1)) {
            walk.filter(Files::isRegularFile).forEach(this::applyExecutablePermissions);
        } catch (IOException ignored) {
        }
    }

    private void deleteDirectoryIfExists(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }

        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private String detectTemurinOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "mac";
        }
        return "linux";
    }

    private String detectTemurinArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }
        return "x64";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record ReleaseAsset(String downloadUrl, String fileName, String sha256) {
    }
}


