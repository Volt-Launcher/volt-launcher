package app.voltlauncher.voltlauncher.launcher.platform.version.resolver;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;
import app.voltlauncher.voltlauncher.launcher.platform.version.VersionOrdering;
import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class FabricVersionResolver extends AbstractDelegatingPlatformResolver {

    private static final String FABRIC_LOADERS_URL = "https://meta.fabricmc.net/v2/versions/loader/";
    private static final String FABRIC_PROFILE_URL = "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json";
    private final HttpFetcher http = new HttpFetcher();

    public FabricVersionResolver(IVersionResolver vanillaResolver) {
        super(PlatformRegistry.FABRIC_ID, vanillaResolver);
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String base = extractMinecraftVersion(minecraftVersionId);
        JSONArray loaders = http.getJsonArray(FABRIC_LOADERS_URL + URLEncoder.encode(base, StandardCharsets.UTF_8));
        List<AvailableVersion> versions = new ArrayList<>(loaders.length());
        for (int i = 0; i < loaders.length(); i++) {
            JSONObject entry = loaders.getJSONObject(i);
            JSONObject loader = entry.optJSONObject("loader");
            if (loader == null) continue;
            String loaderVersion = loader.optString("version", "").trim();
            if (loaderVersion.isBlank()) continue;
            boolean stable = loader.optBoolean("stable", true);
            String type = stable ? "release" : "snapshot";
            versions.add(new AvailableVersion("fabric:" + base + ":" + loaderVersion, type, ""));
        }
        VersionOrdering.sortNewestFirst(versions);
        return versions;
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        Selection selection = parseSelection(versionId);
        String loaderVersion = selection.loaderVersion();
        if (loaderVersion.isBlank()) {
            List<AvailableVersion> loaders = listLoaderVersions("fabric:" + selection.minecraftVersion());
            if (loaders.isEmpty()) {
                throw new IllegalStateException("No Fabric loader found for Minecraft " + selection.minecraftVersion());
            }
            loaderVersion = parseSelection(loaders.getFirst().id()).loaderVersion();
        }

        JSONObject base = super.resolveMetadata("fabric:" + selection.minecraftVersion());
        String url = String.format(FABRIC_PROFILE_URL,
                URLEncoder.encode(selection.minecraftVersion(), StandardCharsets.UTF_8),
                URLEncoder.encode(loaderVersion, StandardCharsets.UTF_8));
        JSONObject profile = http.getJson(url);

        mergeLibraries(base, profile);
        mergeArguments(base, profile);
        if (profile.has("mainClass")) {
            base.put("mainClass", profile.getString("mainClass"));
        }

        base.put("id", "fabric:" + selection.minecraftVersion() + ":" + loaderVersion);
        base.put("jar", selection.minecraftVersion());
        return base;
    }

    private String extractMinecraftVersion(String versionId) {
        String raw = versionId == null ? "" : versionId.trim();
        if (raw.startsWith("fabric:")) {
            String suffix = raw.substring("fabric:".length());
            int sep = suffix.indexOf(':');
            return sep >= 0 ? suffix.substring(0, sep) : suffix;
        }
        int sep = raw.indexOf(':');
        return sep >= 0 ? raw.substring(0, sep) : raw;
    }

    private Selection parseSelection(String versionId) {
        String raw = versionId == null ? "" : versionId.trim();
        if (raw.startsWith("fabric:")) {
            String suffix = raw.substring("fabric:".length());
            String[] parts = suffix.split(":", 2);
            String mc = parts.length >= 1 ? parts[0].trim() : "";
            String loader = parts.length == 2 ? parts[1].trim() : "";
            return new Selection(mc, loader);
        }
        return new Selection(raw, "");
    }

    private void mergeLibraries(JSONObject base, JSONObject profile) {
        JSONArray merged = new JSONArray();
        appendArray(merged, base.optJSONArray("libraries"));
        appendArray(merged, profile.optJSONArray("libraries"));
        if (!merged.isEmpty()) {
            base.put("libraries", merged);
        }
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
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            Object value = source.get(i);
            if (value instanceof JSONObject jo) {
                target.put(new JSONObject(jo.toString()));
            } else if (value instanceof JSONArray ja) {
                target.put(new JSONArray(ja.toString()));
            } else {
                target.put(value);
            }
        }
    }

    private record Selection(String minecraftVersion, String loaderVersion) {}
}


