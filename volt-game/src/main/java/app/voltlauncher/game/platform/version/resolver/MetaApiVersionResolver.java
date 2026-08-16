package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.version.AvailableVersion;
import app.voltlauncher.game.platform.version.IVersionResolver;
import app.voltlauncher.game.platform.version.VersionOrdering;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolver for loaders that expose a FabricMC-style meta API: a loader listing endpoint plus a
 * ready-made launch profile per {@code (minecraftVersion, loaderVersion)} pair. Used by both
 * Fabric and Quilt, whose APIs are shape-compatible.
 */
public abstract class MetaApiVersionResolver extends AbstractDelegatingPlatformResolver {

    private final String loadersUrl;
    private final String profileUrlTemplate;

    /**
     * @param loadersUrl         base URL the Minecraft version is appended to
     * @param profileUrlTemplate {@code String.format} template taking (minecraftVersion, loaderVersion)
     */
    protected MetaApiVersionResolver(String platformId, IVersionResolver vanillaResolver, HttpFetcher http,
                                     String loadersUrl, String profileUrlTemplate) {
        super(platformId, vanillaResolver, http);
        this.loadersUrl = loadersUrl;
        this.profileUrlTemplate = profileUrlTemplate;
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String minecraftVersion = minecraftVersionOf(minecraftVersionId);
        JSONArray loaders = http.getJsonArray(loadersUrl + encode(minecraftVersion));

        List<AvailableVersion> versions = new ArrayList<>(loaders.length());
        for (int i = 0; i < loaders.length(); i++) {
            JSONObject loader = loaders.getJSONObject(i).optJSONObject("loader");
            if (loader == null) continue;
            String loaderVersion = loader.optString("version", "").trim();
            if (loaderVersion.isBlank()) continue;
            String type = loader.optBoolean("stable", true) ? "release" : "snapshot";
            versions.add(new AvailableVersion(qualify(minecraftVersion, loaderVersion), type, ""));
        }

        if (versions.isEmpty()) {
            throw new IllegalStateException(
                    "No " + platformId() + " loader available for Minecraft " + minecraftVersion);
        }
        VersionOrdering.sortNewestFirst(versions);
        return versions;
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        Selection selection = parseSelection(versionId);
        String minecraftVersion = selection.minecraftVersion();
        String loaderVersion = selection.loaderVersion().isBlank()
                ? newestLoaderVersion(minecraftVersion)
                : selection.loaderVersion();

        JSONObject base = super.resolveMetadata(qualify(minecraftVersion));
        JSONObject profile = http.getJson(
                String.format(profileUrlTemplate, encode(minecraftVersion), encode(loaderVersion)));

        mergeProfile(base, profile);
        base.put("id", qualify(minecraftVersion, loaderVersion));
        base.put("jar", minecraftVersion);
        return base;
    }

    private String newestLoaderVersion(String minecraftVersion) throws Exception {
        List<AvailableVersion> loaders = listLoaderVersions(qualify(minecraftVersion));
        return parseSelection(loaders.getFirst().id()).loaderVersion();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
