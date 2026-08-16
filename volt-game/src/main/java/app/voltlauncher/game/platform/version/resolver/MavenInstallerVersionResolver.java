package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.version.AvailableVersion;
import app.voltlauncher.game.platform.version.IVersionResolver;
import app.voltlauncher.game.platform.version.VersionOrdering;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Resolver for the Forge family, which publishes an installer JAR per release to a Maven
 * repository rather than a ready-made launch profile.
 *
 * <p>The installer carries two documents: {@code version.json} (the launch profile, merged onto
 * vanilla metadata) and {@code install_profile.json} (a list of processors that must run once to
 * produce the patched/remapped client artifacts). The install profile and the installer's own path
 * are attached to the returned metadata under {@code voltInstallProfile} / {@code voltInstallerPath}
 * so the asset installer can execute the processors.
 */
public abstract class MavenInstallerVersionResolver extends AbstractDelegatingPlatformResolver {

    private static final Pattern VERSION_TAG = Pattern.compile("<version>([^<]+)</version>");

    private final String mavenMetadataUrl;

    protected MavenInstallerVersionResolver(String platformId, IVersionResolver vanillaResolver,
                                            HttpFetcher http, String mavenMetadataUrl) {
        super(platformId, vanillaResolver, http);
        this.mavenMetadataUrl = mavenMetadataUrl;
    }

    /** URL of the installer JAR for a resolved (minecraftVersion, loaderVersion) pair. */
    protected abstract String installerUrl(String minecraftVersion, String loaderVersion);

    /**
     * Decides whether a version published in the Maven metadata belongs to the requested
     * Minecraft version and, if so, returns the loader version to expose to the UI.
     */
    protected abstract Optional<String> matchLoaderVersion(String minecraftVersion, String mavenVersion);

    /** Cache key used for the downloaded installer JAR. */
    protected String installerCacheName(String minecraftVersion, String loaderVersion) {
        return platformId() + "-" + minecraftVersion + "-" + loaderVersion;
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String minecraftVersion = minecraftVersionOf(minecraftVersionId);
        String xml = http.getString(mavenMetadataUrl);

        List<AvailableVersion> result = new ArrayList<>();
        Matcher matcher = VERSION_TAG.matcher(xml);
        while (matcher.find()) {
            String mavenVersion = matcher.group(1).trim();
            if (mavenVersion.isBlank()) continue;
            matchLoaderVersion(minecraftVersion, mavenVersion).ifPresent(loaderVersion ->
                    result.add(new AvailableVersion(qualify(minecraftVersion, loaderVersion), "release", "")));
        }

        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "No " + platformId() + " build available for Minecraft " + minecraftVersion);
        }
        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        Selection selection = parseSelection(versionId);
        String minecraftVersion = selection.minecraftVersion();
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required for " + platformId());
        }
        String loaderVersion = selection.loaderVersion().isBlank()
                ? newestLoaderVersion(minecraftVersion)
                : selection.loaderVersion();

        InstallerData installer = loadInstaller(
                installerUrl(minecraftVersion, loaderVersion),
                installerCacheName(minecraftVersion, loaderVersion));

        JSONObject base = super.resolveMetadata(qualify(minecraftVersion));
        mergeProfile(base, installer.versionJson());

        base.put("id", qualify(minecraftVersion, loaderVersion));
        base.put("jar", minecraftVersion);
        base.put("voltPlatform", platformId());

        if (!installer.installProfile().isEmpty()) {
            base.put("voltInstallProfile", installer.installProfile());
            base.put("voltInstallerPath", installer.installerJar().toAbsolutePath().toString());
        }
        return base;
    }

    private String newestLoaderVersion(String minecraftVersion) throws Exception {
        List<AvailableVersion> loaders = listLoaderVersions(qualify(minecraftVersion));
        return parseSelection(loaders.getFirst().id()).loaderVersion();
    }

    /**
     * Downloads (once) and reads the installer JAR. Installers are immutable per release, so the
     * cache lives under the launcher data directory rather than the system temp directory, which
     * avoids re-downloading tens of megabytes after every reboot.
     */
    private InstallerData loadInstaller(String installerUrl, String cacheName) throws Exception {
        Path cacheDir = AppPaths.installerCacheDirectory();
        Files.createDirectories(cacheDir);
        Path installerJar = cacheDir.resolve(cacheName + "-installer.jar");
        http.download(installerUrl, installerJar, "");

        try (ZipFile zip = new ZipFile(installerJar.toFile())) {
            JSONObject versionJson = readEntry(zip, "version.json")
                    .orElseThrow(() -> new IllegalStateException(
                            platformId() + " installer has no version.json: " + installerUrl));
            JSONObject installProfile = readEntry(zip, "install_profile.json").orElseGet(JSONObject::new);
            return new InstallerData(versionJson, installProfile, installerJar);
        }
    }

    private Optional<JSONObject> readEntry(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name);
        if (entry == null) return Optional.empty();
        try (var in = zip.getInputStream(entry)) {
            return Optional.of(new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private record InstallerData(JSONObject versionJson, JSONObject installProfile, Path installerJar) {}
}
