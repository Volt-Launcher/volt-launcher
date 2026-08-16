package app.voltlauncher.game.install;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.core.util.JsonUtil;
import app.voltlauncher.core.util.async.NamedLock;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.game.java.JavaRuntimeResolver;
import app.voltlauncher.game.platform.version.processor.ForgeProcessorRunner;
import app.voltlauncher.game.platform.version.resolver.VanillaVersionResolver;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssetInstaller {

    /** Matches an install-profile reference to Mojang's obfuscation mappings. */
    private static final Pattern MAPPINGS_COORDINATE =
            Pattern.compile("^\\[net\\.minecraft:(client|server):([^:]+):mappings@txt]$");

    private final HttpFetcher http;
    private final VanillaVersionResolver versionResolver;
    private final JavaRuntimeResolver javaResolver;
    private final AssetDownloader assetDownloader;
    private final LibraryInstaller libraryInstaller;
    /** Serialises installs of the same version so two profiles cannot race the same downloads. */
    private final NamedLock versionLock = new NamedLock();

    public AssetInstaller(HttpFetcher http, VanillaVersionResolver versionResolver, JavaRuntimeResolver javaResolver) {
        this.http = http;
        this.versionResolver = versionResolver;
        this.javaResolver = javaResolver;
        this.assetDownloader = new AssetDownloader(http);
        this.libraryInstaller = new LibraryInstaller(http);
    }

    /**
     * Makes an instance launchable for the given (possibly loader-merged) version metadata:
     * downloads the client jar, libraries, natives, assets and logging config, then runs any
     * mod-loader install processors.
     */
    public Installation ensureInstallation(Instance instance, JSONObject meta) throws Exception {
        String launchVersionId = meta.optString("id", instance.versionId());
        // Everything below is keyed by version, not by profile, so two profiles on the same
        // version share one install and never write the same file concurrently.
        return versionLock.withLock(launchVersionId, () -> install(instance, meta, launchVersionId));
    }

    private Installation install(Instance instance, JSONObject meta, String launchVersionId) throws Exception {
        String clientVersionId = meta.optString("jar", launchVersionId);
        String launchVersionFolder = safeVersionFolderName(launchVersionId);
        String clientVersionFolder = safeVersionFolderName(clientVersionId);
        JSONObject clientMeta = clientVersionId.equals(launchVersionId)
                ? meta
                : versionResolver.resolveMetadata(clientVersionId);

        Path versionsDir = AppPaths.sharedVersionsDirectory();
        Path libsDir = AppPaths.sharedLibrariesDirectory();
        Path assetsDir = AppPaths.sharedAssetsDirectory();
        Path nativesDir = AppPaths.sharedNativesDirectory().resolve(launchVersionFolder);

        createRequiredDirectories(versionsDir, launchVersionFolder, clientVersionFolder,
                libsDir, assetsDir, instance, nativesDir);

        Path clientJar = downloadClientJar(clientMeta, versionsDir, clientVersionFolder);
        String assetIndexId = installAssets(meta, assetsDir, instance);
        Path loggingConfigPath = resolveLoggingConfig(meta, assetsDir);

        LinkedHashSet<String> classpath = new LinkedHashSet<>();
        libraryInstaller.downloadLibraries(meta, libsDir, nativesDir, classpath);

        // The vanilla client jar stays on the classpath for every platform. Forge and NeoForge
        // transform it at runtime rather than replacing it, and FML fails to boot without it.
        classpath.add(clientJar.toString());

        runInstallProfileIfPresent(meta, clientMeta, instance, libsDir, nativesDir, clientJar);

        return new Installation(
                instance, launchVersionId, meta, libsDir, assetsDir, nativesDir,
                String.join(File.pathSeparator, classpath), meta.getString("mainClass"),
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
        Files.createDirectories(nativesDir);
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

    private void runInstallProfileIfPresent(JSONObject meta, JSONObject clientMeta, Instance instance,
                                            Path libsDir, Path nativesDir, Path clientJar) throws Exception {
        if (!meta.has("voltInstallProfile")) return;
        JSONObject installProfile = meta.getJSONObject("voltInstallProfile");
        Path installerJar = Path.of(meta.getString("voltInstallerPath"));

        libraryInstaller.downloadInstallProfileLibraries(installProfile, libsDir, nativesDir);
        ensureMojangMappings(installProfile, clientMeta, libsDir);

        JavaRuntimeResolver.JavaRuntime javaRuntime = javaResolver.resolveRuntime(instance.javaMajorVersion());
        new ForgeProcessorRunner(installProfile, installerJar, libsDir, clientJar,
                javaRuntime.javaExecutable().toString()).runIfNeeded();
    }

    /**
     * Forge's and NeoForge's remapping processors consume Mojang's official mappings, which the
     * install profile references as the Maven coordinate
     * {@code [net.minecraft:client:<version>:mappings@txt]}. Nothing publishes that artifact to a
     * Maven repository — it is only reachable through the version manifest's
     * {@code downloads.client_mappings} entry — so it is placed at the expected path here.
     * Without it every processor run fails and the instance never becomes launchable.
     */
    private void ensureMojangMappings(JSONObject installProfile, JSONObject clientMeta, Path libsDir) throws Exception {
        JSONObject data = installProfile.optJSONObject("data");
        if (data == null) return;

        for (String key : data.keySet()) {
            JSONObject entry = data.optJSONObject(key);
            String coordinate = entry != null ? entry.optString("client", "") : data.optString(key, "");
            Matcher matcher = MAPPINGS_COORDINATE.matcher(coordinate.trim());
            if (!matcher.matches()) continue;

            String side = matcher.group(1);
            String relativePath = LibraryInstaller.mavenRelativePath(
                    "net.minecraft:" + side + ":" + matcher.group(2) + ":mappings@txt");
            if (relativePath == null) continue;

            Path target = libsDir.resolve(relativePath);
            if (Files.exists(target)) continue;

            JSONObject download = clientMeta.optJSONObject("downloads");
            JSONObject mappings = download == null ? null : download.optJSONObject(side + "_mappings");
            if (mappings == null) {
                throw new IllegalStateException(
                        "This Minecraft version does not publish " + side + " mappings, which "
                                + "the mod loader's installer requires. Pick a different loader build.");
            }
            http.download(mappings.getString("url"), target, mappings.optString("sha1", ""));
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

    private String safeVersionFolderName(String versionId) {
        if (versionId == null || versionId.isBlank()) return "unknown-version";
        return versionId.replace(':', '_').replace('/', '_').replace('\\', '_');
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
