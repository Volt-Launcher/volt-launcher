package app.voltlauncher.voltlauncher.launcher.install;

import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.java.JavaRuntimeResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.runner.NeoForgeProcessorRunner;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import app.voltlauncher.voltlauncher.util.JsonUtil;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;

public final class AssetInstaller {

    private final HttpFetcher http;
    private final VanillaVersionResolver versionResolver;
    private final JavaRuntimeResolver javaResolver;
    private final AssetDownloader assetDownloader;
    private final LibraryInstaller libraryInstaller;

    public AssetInstaller(HttpFetcher http, VanillaVersionResolver versionResolver, JavaRuntimeResolver javaResolver) {
        this.http = http;
        this.versionResolver = versionResolver;
        this.javaResolver = javaResolver;
        this.assetDownloader = new AssetDownloader(http);
        this.libraryInstaller = new LibraryInstaller(http);
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

        createRequiredDirectories(versionsDir, launchVersionFolder, clientVersionFolder, libsDir, assetsDir, instance, nativesDir);

        Path clientJar = downloadClientJar(clientMeta, versionsDir, clientVersionFolder);
        String assetIndexId = installAssets(meta, assetsDir, instance);
        Path loggingConfigPath = resolveLoggingConfig(meta, assetsDir);

        LinkedHashSet<String> cp = new LinkedHashSet<>();
        libraryInstaller.downloadLibraries(meta, libsDir, nativesDir, cp);
        if (!meta.has("voltInstallProfile")) {
            cp.add(clientJar.toString());
        }

        runInstallProfileIfPresent(meta, instance, libsDir, nativesDir, clientJar);

        return new Installation(
                instance, launchVersionId, meta, libsDir, assetsDir, nativesDir,
                String.join(File.pathSeparator, cp), meta.getString("mainClass"),
                loggingConfigPath, assetIndexId, instance.versionType());
    }

    private void createRequiredDirectories(Path versionsDir, String launchVersionFolder,
            String clientVersionFolder, Path libsDir, Path assetsDir,
            Instance instance, Path nativesDir) throws IOException {
        Files.createDirectories(versionsDir.resolve(launchVersionFolder));
        Files.createDirectories(versionsDir.resolve(clientVersionFolder));
        Files.createDirectories(libsDir);
        Files.createDirectories(assetsDir.resolve("indexes"));
        Files.createDirectories(assetsDir.resolve("objects"));
        Files.createDirectories(instance.gameDirectory());
        recreateDirectory(nativesDir);
    }

    private Path downloadClientJar(JSONObject clientMeta, Path versionsDir, String clientVersionFolder) throws Exception {
        JSONObject clientDownload = JsonUtil.requireObject(clientMeta, "downloads.client");
        Path clientJar = versionsDir.resolve(clientVersionFolder).resolve(clientVersionFolder + ".jar");
        http.download(clientDownload.getString("url"), clientJar, clientDownload.optString("sha1", ""));
        return clientJar;
    }

    private String installAssets(JSONObject meta, Path assetsDir, Instance instance) throws Exception {
        String assetIndexId = meta.optString("assets", "legacy");
        if (!meta.has("assetIndex")) return assetIndexId;

        JSONObject assetIndex = meta.getJSONObject("assetIndex");
        assetIndexId = assetIndex.getString("id");
        Path indexPath = assetsDir.resolve("indexes").resolve(assetIndexId + ".json");
        http.download(assetIndex.getString("url"), indexPath, assetIndex.optString("sha1", ""));

        JSONObject indexJson = JsonUtil.readFile(indexPath);
        assetDownloader.downloadParallel(indexJson, assetsDir.resolve("objects"));

        if (indexJson.optBoolean("virtual", false)) {
            assetDownloader.createVirtualAssets(indexJson, assetsDir.resolve("objects"),
                    assetsDir.resolve("virtual").resolve(assetIndexId));
        }
        if (indexJson.optBoolean("map_to_resources", false)) {
            assetDownloader.createVirtualAssets(indexJson, assetsDir.resolve("objects"),
                    instance.gameDirectory().resolve("resources"));
        }
        return assetIndexId;
    }

    private void runInstallProfileIfPresent(JSONObject meta, Instance instance,
            Path libsDir, Path nativesDir, Path clientJar) throws Exception {
        if (!meta.has("voltInstallProfile")) return;
        JSONObject installProfile = meta.getJSONObject("voltInstallProfile");
        Path installerJar = Path.of(meta.getString("voltInstallerPath"));
        libraryInstaller.downloadInstallProfileLibraries(installProfile, libsDir, nativesDir);
        JavaRuntimeResolver.JavaRuntime javaRuntime = javaResolver.resolveRuntime(instance.javaMajorVersion());
        NeoForgeProcessorRunner processorRunner = new NeoForgeProcessorRunner(
                installProfile, installerJar, libsDir, clientJar, javaRuntime.javaExecutable().toString());
        processorRunner.runIfNeeded();
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

    private String safeVersionFolderName(String versionId) {
        if (versionId == null || versionId.isBlank()) return "unknown-version";
        return versionId.replace(':', '_').replace('/', '_').replace('\\', '_');
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

    public record Installation(
            Instance instance,
            String launchVersionId,
            JSONObject launchMetadata,
            Path librariesDirectory,
            Path assetsDirectory,
            Path nativesDirectory,
            String classpath,
            String mainClass,
            Path loggingConfigPath,
            String assetIndexId,
            String versionType) {}
}
