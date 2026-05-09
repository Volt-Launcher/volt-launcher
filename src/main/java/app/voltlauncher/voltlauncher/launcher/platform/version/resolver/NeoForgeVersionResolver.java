package app.voltlauncher.voltlauncher.launcher.platform.version.resolver;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.VersionOrdering;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public final class NeoForgeVersionResolver extends AbstractDelegatingPlatformResolver {

    private static final String NEOFORGE_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String NEOFORGE_INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar";
    private static final Pattern VERSION_TAG = Pattern.compile("<version>([^<]+)</version>");

    private final HttpFetcher http = new HttpFetcher();

    public NeoForgeVersionResolver(IVersionResolver vanillaResolver) {
        super(PlatformRegistry.NEOFORGE_ID, vanillaResolver);
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String base = extractMinecraftVersion(minecraftVersionId);
        String mappedPrefix = mapMinecraftToNeoForgePrefix(base);
        String xml = http.getString(NEOFORGE_METADATA_URL);

        List<AvailableVersion> result = new ArrayList<>();
        Matcher matcher = VERSION_TAG.matcher(xml);
        while (matcher.find()) {
            String loader = matcher.group(1).trim();
            if (loader.isBlank()) {
                continue;
            }
            if (!(loader.equals(mappedPrefix) || loader.startsWith(mappedPrefix + ".") || loader.startsWith(mappedPrefix + "-"))) {
                continue;
            }
            result.add(new AvailableVersion("neoforge:" + base + ":" + loader, "release", ""));
        }

        VersionOrdering.sortNewestFirst(result);
        return result;
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        Selection selection = parseSelection(versionId);
        String minecraftVersion = selection.minecraftVersion();
        String loaderVersion = selection.loaderVersion();

        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required for NeoForge");
        }

        if (loaderVersion.isBlank()) {
            List<AvailableVersion> loaders = listLoaderVersions("neoforge:" + minecraftVersion);
            if (loaders.isEmpty()) {
                throw new IllegalStateException("No NeoForge loader found for Minecraft " + minecraftVersion);
            }
            loaderVersion = parseSelection(loaders.getFirst().id()).loaderVersion();
        }

        String installerUrl = NEOFORGE_INSTALLER_URL.formatted(loaderVersion, loaderVersion);
        InstallerData installerData = loadFromInstaller(installerUrl, "neoforge-" + loaderVersion);

        JSONObject profile = installerData.versionJson();
        JSONObject installProfile = installerData.installProfile();

        JSONObject base = super.resolveMetadata("neoforge:" + minecraftVersion);
        mergeLibraries(base, profile);
        mergeArguments(base, profile);

        if (profile.has("mainClass")) {
            base.put("mainClass", profile.getString("mainClass"));
        }
        if (profile.has("minecraftArguments")) {
            base.put("minecraftArguments", profile.getString("minecraftArguments"));
        }
        if (profile.has("javaVersion")) {
            base.put("javaVersion", new JSONObject(profile.getJSONObject("javaVersion").toString()));
        }

        base.put("id", "neoforge:" + minecraftVersion + ":" + loaderVersion);
        base.put("jar", minecraftVersion);
        base.put("voltPlatform", PlatformRegistry.NEOFORGE_ID);

        base.put("voltInstallProfile", installProfile);
        base.put("voltInstallerPath", installerData.installerJar().toAbsolutePath().toString());

        return base;
    }

    private InstallerData loadFromInstaller(String installerUrl, String cacheName) throws Exception {
        Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir"), "volt-launcher", "loader-version-json");
        Files.createDirectories(cacheDir);
        Path installerJar = cacheDir.resolve(cacheName + "-installer.jar");
        http.download(installerUrl, installerJar, "");

        try (ZipFile zip = new ZipFile(installerJar.toFile())) {
            var versionEntry = zip.getEntry("version.json");
            var profileEntry = zip.getEntry("install_profile.json");

            if (versionEntry == null) {
                throw new IllegalStateException("NeoForge installer has no version.json: " + installerUrl);
            }
            if (profileEntry == null) {
                throw new IllegalStateException("NeoForge installer has no install_profile.json: " + installerUrl);
            }

            JSONObject versionJson = new JSONObject(new String(zip.getInputStream(versionEntry).readAllBytes(), StandardCharsets.UTF_8));
            JSONObject installProfile = new JSONObject(new String(zip.getInputStream(profileEntry).readAllBytes(), StandardCharsets.UTF_8));

            return new InstallerData(versionJson, installProfile, installerJar);
        }
    }

    private String extractMinecraftVersion(String versionId) {
        String raw = versionId == null ? "" : versionId.trim();
        if (raw.startsWith("neoforge:")) {
            String suffix = raw.substring("neoforge:".length());
            int sep = suffix.indexOf(':');
            return sep >= 0 ? suffix.substring(0, sep) : suffix;
        }
        int sep = raw.indexOf(':');
        return sep >= 0 ? raw.substring(0, sep) : raw;
    }

    private Selection parseSelection(String versionId) {
        String raw = versionId == null ? "" : versionId.trim();
        if (raw.startsWith("neoforge:")) {
            String suffix = raw.substring("neoforge:".length());
            String[] parts = suffix.split(":", 2);
            String mc = parts.length >= 1 ? parts[0].trim() : "";
            String loader = parts.length == 2 ? parts[1].trim() : "";
            return new Selection(mc, loader);
        }
        return new Selection(raw, "");
    }

    private String mapMinecraftToNeoForgePrefix(String minecraftVersion) {
        if (minecraftVersion.startsWith("1.")) {
            return minecraftVersion.substring(2);
        }
        return minecraftVersion;
    }

    private void mergeLibraries(JSONObject base, JSONObject source) {
        JSONArray existing = base.optJSONArray("libraries");
        JSONArray incoming = source.optJSONArray("libraries");
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        JSONArray merged = existing != null ? existing : new JSONArray();
        appendArray(merged, incoming);
        base.put("libraries", merged);
    }

    private void mergeArguments(JSONObject base, JSONObject profile) {
        JSONObject args = new JSONObject();
        JSONObject baseArgs = base.optJSONObject("arguments");
        JSONObject profileArgs = profile.optJSONObject("arguments");
        mergeArgArray(args, baseArgs, profileArgs, "jvm");
        mergeArgArray(args, baseArgs, profileArgs, "game");
        if (!args.isEmpty()) {
            base.put("arguments", args);
        }
    }

    private void mergeArgArray(JSONObject target, JSONObject baseArgs, JSONObject profileArgs, String key) {
        JSONArray arr = new JSONArray();
        appendArray(arr, baseArgs != null ? baseArgs.optJSONArray(key) : null);
        appendArray(arr, profileArgs != null ? profileArgs.optJSONArray(key) : null);
        if (!arr.isEmpty()) {
            target.put(key, arr);
        }
    }

    private void appendArray(JSONArray target, JSONArray source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            Object value = source.get(i);
            if (value instanceof JSONObject jo) {
                target.put(new JSONObject(jo.toString()));
                continue;
            }
            if (value instanceof JSONArray ja) {
                target.put(new JSONArray(ja.toString()));
                continue;
            }
            target.put(value);
        }
    }

    private record Selection(String minecraftVersion, String loaderVersion) {}

    private record InstallerData(JSONObject versionJson, JSONObject installProfile, Path installerJar) {}
}