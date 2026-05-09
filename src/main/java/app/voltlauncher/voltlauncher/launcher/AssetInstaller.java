package app.voltlauncher.voltlauncher.launcher;

import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.runner.NeoForgeProcessorRunner;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import app.voltlauncher.voltlauncher.util.JsonUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class AssetInstaller {

    private static final int ASSET_CONCURRENCY = 16;
    private final HttpFetcher http;
    private final VanillaVersionResolver versionResolver;

    public AssetInstaller(HttpFetcher http, VanillaVersionResolver versionResolver) {
        this.http = http;
        this.versionResolver = versionResolver;
    }

    public Installation ensureInstallation(Instance instance, JSONObject meta) throws Exception {
        String launchVersionId = instance.versionId();
        String clientVersionId = meta.optString("jar", launchVersionId);
        String launchVersionFolder = safeVersionFolderName(launchVersionId);
        String clientVersionFolder = safeVersionFolderName(clientVersionId);
        JSONObject clientMeta = clientVersionId.equals(launchVersionId) ? meta : versionResolver.resolveMetadata(clientVersionId);

        Path mcDir = instance.gameDirectory().resolve(".minecraft");
        Path versionsDir = mcDir.resolve("versions");
        Path libsDir = mcDir.resolve("libraries");
        Path assetsDir = mcDir.resolve("assets");
        Path nativesDir = mcDir.resolve("natives").resolve(launchVersionFolder);

        Files.createDirectories(versionsDir.resolve(launchVersionFolder));
        Files.createDirectories(versionsDir.resolve(clientVersionFolder));
        Files.createDirectories(libsDir);
        Files.createDirectories(assetsDir.resolve("indexes"));
        Files.createDirectories(assetsDir.resolve("objects"));
        Files.createDirectories(instance.gameDirectory());
        recreateDirectory(nativesDir);

        JSONObject clientDownload = JsonUtil.requireObject(clientMeta, "downloads.client");
        Path clientJar = versionsDir.resolve(clientVersionFolder).resolve(clientVersionFolder + ".jar");
        http.download(clientDownload.getString("url"), clientJar, clientDownload.optString("sha1", ""));

        String assetIndexId = meta.optString("assets", "legacy");
        if (meta.has("assetIndex")) {
            JSONObject assetIndex = meta.getJSONObject("assetIndex");
            assetIndexId = assetIndex.getString("id");
            Path indexPath = assetsDir.resolve("indexes").resolve(assetIndexId + ".json");
            http.download(assetIndex.getString("url"), indexPath, assetIndex.optString("sha1", ""));

            JSONObject indexJson = JsonUtil.readFile(indexPath);
            downloadAssetsParallel(indexJson, assetsDir.resolve("objects"));

            boolean isVirtual = indexJson.optBoolean("virtual", false);
            boolean mapResources = indexJson.optBoolean("map_to_resources", false);
            if (isVirtual) {
                createVirtualAssets(indexJson, assetsDir.resolve("objects"), assetsDir.resolve("virtual").resolve(assetIndexId));
            }
            if (mapResources) {
                createVirtualAssets(indexJson, assetsDir.resolve("objects"), instance.gameDirectory().resolve("resources"));
            }
        }

        Path loggingConfigPath = resolveLoggingConfig(meta, assetsDir);
        LinkedHashSet < String> cp = new LinkedHashSet <> ();
        downloadLibraries(meta, libsDir, nativesDir, cp);
        if (!meta.has("voltInstallProfile")) {
            cp.add(clientJar.toString());
        }

        if (meta.has("voltInstallProfile")) {
            JSONObject installProfile = meta.getJSONObject("voltInstallProfile");
            Path installerJar = Path.of(meta.getString("voltInstallerPath"));
            downloadInstallProfileLibraries(installProfile, libsDir, nativesDir);
            String javaExec = ProcessHandle.current().info().command().orElse("java");
            NeoForgeProcessorRunner processorRunner = new NeoForgeProcessorRunner(installProfile, installerJar, libsDir, clientJar, javaExec);
            processorRunner.runIfNeeded();
        }

        return new Installation( instance, launchVersionId, meta, libsDir, assetsDir, nativesDir, String.join(File.pathSeparator, cp), meta.getString("mainClass"), loggingConfigPath, assetIndexId, instance.versionType());
    }

    private void createVirtualAssets(JSONObject indexJson, Path objectsDir, Path virtualDir) throws Exception {
        JSONObject objects = indexJson.getJSONObject("objects");
        Files.createDirectories(virtualDir);
        for (String assetName : objects.keySet()) {
            String hash = objects.getJSONObject(assetName).getString("hash");
            String prefix = hash.substring(0, 2);
            Path src = objectsDir.resolve(prefix).resolve(hash);
            Path dest = virtualDir.resolve(assetName.replace("/", File.separator));
            if (Files.exists(dest)) { continue; }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadAssetsParallel(JSONObject indexJson, Path objectsDir) throws Exception {
        JSONObject objects = indexJson.getJSONObject("objects");
        Semaphore sem = new Semaphore(ASSET_CONCURRENCY);
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List < CompletableFuture < Void>> tasks = new ArrayList <> (objects.length());
            for (String name : objects.keySet()) {
                String hash = objects.getJSONObject(name).getString("hash");
                String prefix = hash.substring(0, 2);
                Path target = objectsDir.resolve(prefix).resolve(hash);
                String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        sem.acquire();
                        try {
                            http.download(url, target, hash);
                        } finally {
                            sem.release();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, exec));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }
    }

    private Path resolveLoggingConfig(JSONObject meta, Path assetsDir) throws Exception {
        if (!meta.has("logging")) return null;
        JSONObject client = meta.getJSONObject("logging").optJSONObject("client");
        if (client == null) return null;
        JSONObject file = client.getJSONObject("file");
        Path path = assetsDir.resolve("log_configs").resolve(file.getString("id"));
        http.download(file.getString("url"), path, file.optString("sha1", ""));
        return path;
    }

    private void downloadLibraries(
            JSONObject meta, Path libsDir, Path nativesDir, LinkedHashSet < String> cp) throws Exception {
        JSONArray libraries = meta.optJSONArray("libraries");
        if (libraries == null) return;
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject lib = libraries.getJSONObject(i);
            if (!isAllowedByRules(lib.optJSONArray("rules"))) continue;
            JSONObject downloads = lib.optJSONObject("downloads");
            if (downloads == null) {
                if (downloadMavenLibrary(lib, libsDir, cp)) {
                    continue;
                }
                continue;
            }
            JSONObject artifact = downloads.optJSONObject("artifact");
            if (artifact != null) {
                Path p = libsDir.resolve(artifact.getString("path"));
                http.download(artifact.getString("url"), p, artifact.optString("sha1", ""));
                cp.add(p.toString());
            }
            JSONObject classifiers = downloads.optJSONObject("classifiers");
            if (classifiers != null && lib.has("natives")) {
                String classifier = resolveNativeClassifier(lib.getJSONObject("natives"));
                if (classifier != null && classifiers.has(classifier)) {
                    JSONObject nd = classifiers.getJSONObject(classifier);
                    Path archive = libsDir.resolve(nd.getString("path"));
                    http.download(nd.getString("url"), archive, nd.optString("sha1", ""));
                    extractNative(archive, nativesDir, lib.optJSONObject("extract"));
                }
            }
        }
    }

    private boolean downloadMavenLibrary(JSONObject lib, Path libsDir, LinkedHashSet < String> cp) throws Exception {
        String gav = lib.optString("name", "").trim();
        if (gav.isBlank()) return false;

        String[] parts = gav.split(":");
        if (parts.length < 3) return false;

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? parts[3] : "";

        String extension = "jar";
        int versionAt = version.indexOf('@');
        if (versionAt >= 0) {
            String extFromVersion = version.substring(versionAt + 1).trim();
            if (!extFromVersion.isBlank()) {
                extension = extFromVersion;
            }
            version = version.substring(0, versionAt);
        }
        int at = classifier.indexOf('@');
        if (at >= 0) {
            String extFromClassifier = classifier.substring(at + 1).trim();
            if (!extFromClassifier.isBlank()) {
                extension = extFromClassifier;
            }
            classifier = classifier.substring(0, at);
        }

        String baseRepo = lib.optString("url", "https://libraries.minecraft.net/").trim();
        if (!baseRepo.endsWith("/")) {
            baseRepo = baseRepo + "/";
        }

        String rel = group.replace('.', '/') + "/" + artifact + "/" + version + "/";
        String fileName = artifact + "-" + version + (classifier.isBlank() ? "" : "-" + classifier) + "." + extension;
        Path target = libsDir.resolve(rel).resolve(fileName);
        http.download(baseRepo + rel + fileName, target, "");
        cp.add(target.toString());
        return true;
    }

    private void downloadInstallProfileLibraries(JSONObject installProfile, Path libsDir, Path nativesDir) throws Exception {
        JSONArray installLibraries = installProfile.optJSONArray("libraries");
        if (installLibraries == null || installLibraries.isEmpty()) {
            return;
        }
        JSONObject pseudoMeta = new JSONObject();
        pseudoMeta.put("libraries", new JSONArray(installLibraries.toString()));
        // Processor dependencies must exist locally, but they are not part of the game runtime classpath.
        downloadLibraries(pseudoMeta, libsDir, nativesDir, new LinkedHashSet<>());
    }

    private String safeVersionFolderName(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return "unknown-version";
        }
        return versionId.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private void extractNative(Path archive, Path targetDir, JSONObject extractConfig) throws Exception {
        List < String> excludes = new ArrayList <> ();
        if (extractConfig != null) {
            JSONArray arr = extractConfig.optJSONArray("exclude");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) { excludes.add(arr.getString(i)); }
            }
        }
        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || excludes.stream().anyMatch(name::startsWith)) { continue; }
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) { throw new IOException("Invalid native path: " + name); }
                Files.createDirectories(out.getParent());
                Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isAllowedByRules(JSONArray rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            if (!ruleMatches(rule)) { continue; }
            allowed = Objects.equals(rule.optString("action", "allow"), "allow");
        }
        return allowed;
    }

    private boolean ruleMatches(JSONObject rule) {
        if (rule.has("features") && !rule.getJSONObject("features").isEmpty()) return false;
        if (!rule.has("os")) return true;
        OsDetails os = currentOs();
        JSONObject osJson = rule.getJSONObject("os");
        String expectedName = osJson.optString("name", "");
        if (!expectedName.isBlank() && !expectedName.equals(os.name())) return false;
        String expectedArch = osJson.optString("arch", "");
        return expectedArch.isBlank() || System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)
                .contains(expectedArch.toLowerCase(Locale.ROOT));
    }

    private String resolveNativeClassifier(JSONObject natives) {
        OsDetails os = currentOs();
        String classifier = natives.optString(os.name(), "");
        if (classifier.isBlank()) return null;
        return classifier.replace("${arch}", os.archBits());
    }

    private OsDetails currentOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String bits = System.getProperty("os.arch", "").contains("64") ? "64" : "32";
        if (name.contains("win")) return new OsDetails("windows", bits);
        if (name.contains("mac") || name.contains("darwin")) return new OsDetails("osx", bits);
        return new OsDetails("linux", bits);
    }

    private void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(p -> !p.equals(dir))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException e) { throw new RuntimeException(e); }
                        });
            }
        }
        Files.createDirectories(dir);
    }

    public record Installation(Instance instance, String launchVersionId, JSONObject launchMetadata, Path librariesDirectory, Path assetsDirectory, Path nativesDirectory, String classpath, String mainClass, Path loggingConfigPath, String assetIndexId, String versionType) {}

    private record OsDetails(String name, String archBits) {}
}
