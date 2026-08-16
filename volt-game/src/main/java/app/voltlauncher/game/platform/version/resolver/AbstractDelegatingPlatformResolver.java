package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.version.AvailableVersion;
import app.voltlauncher.game.platform.version.IVersionResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Base for loader platforms that decorate a vanilla Minecraft version.
 *
 * <p>Version ids are {@code <platform>:<minecraftVersion>[:<loaderVersion>]}. A missing loader
 * version means "newest available", which subclasses resolve at metadata time and pin into the
 * returned {@code id}.
 */
public abstract class AbstractDelegatingPlatformResolver implements IVersionResolver {

    private final String platformId;
    private final IVersionResolver vanillaResolver;
    protected final HttpFetcher http;

    protected AbstractDelegatingPlatformResolver(String platformId, IVersionResolver vanillaResolver, HttpFetcher http) {
        this.platformId = Objects.requireNonNull(platformId, "platformId").toLowerCase(Locale.ROOT);
        this.vanillaResolver = Objects.requireNonNull(vanillaResolver, "vanillaResolver");
        this.http = Objects.requireNonNull(http, "http");
    }

    protected final String platformId() {
        return platformId;
    }

    protected final IVersionResolver vanillaResolver() {
        return vanillaResolver;
    }

    @Override
    public List<AvailableVersion> listAvailableVersions() throws Exception {
        List<AvailableVersion> mapped = new ArrayList<>();
        for (AvailableVersion version : vanillaResolver.listAvailableVersions()) {
            mapped.add(new AvailableVersion(qualify(version.id()), version.type(), version.releaseTime()));
        }
        return mapped;
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String baseVersionId = minecraftVersionOf(minecraftVersionId);
        return List.of(new AvailableVersion(qualify(baseVersionId), "release", releaseTimeOf(baseVersionId)));
    }

    /**
     * Vanilla metadata for the given Minecraft version, re-tagged for this platform. Subclasses
     * merge their loader profile on top of the returned object.
     */
    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        String baseVersionId = minecraftVersionOf(versionId);
        JSONObject merged = new JSONObject(vanillaResolver.resolveMetadata(baseVersionId).toString());
        merged.put("id", qualify(baseVersionId));
        merged.put("jar", baseVersionId);
        merged.put("voltPlatform", platformId);
        return merged;
    }

    // ── id parsing ────────────────────────────────────────────────────────────

    protected final String qualify(String versionId) {
        return platformId + ":" + versionId;
    }

    /** Builds a fully qualified id from its parts. */
    protected final String qualify(String minecraftVersion, String loaderVersion) {
        return platformId + ":" + minecraftVersion + ":" + loaderVersion;
    }

    /** The Minecraft version component of an id, with or without the platform prefix. */
    protected final String minecraftVersionOf(String versionId) {
        return parseSelection(versionId).minecraftVersion();
    }

    /**
     * Splits {@code platform:mc[:loader]} into its parts. Ids without the platform prefix are
     * treated as a bare Minecraft version.
     */
    protected final Selection parseSelection(String versionId) {
        String raw = versionId == null ? "" : versionId.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("versionId is required");
        }

        String prefix = platformId + ":";
        if (!raw.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return new Selection(raw, "");
        }

        String suffix = raw.substring(prefix.length()).trim();
        if (suffix.isBlank()) {
            throw new IllegalArgumentException("Missing Minecraft version after '" + platformId + ":'");
        }
        int sep = suffix.indexOf(':');
        return sep < 0
                ? new Selection(suffix, "")
                : new Selection(suffix.substring(0, sep).trim(), suffix.substring(sep + 1).trim());
    }

    private String releaseTimeOf(String versionId) throws Exception {
        for (AvailableVersion version : vanillaResolver.listAvailableVersions()) {
            if (version.id().equals(versionId)) return version.releaseTime();
        }
        return "";
    }

    // ── profile merging (shared by every loader) ──────────────────────────────

    /**
     * Merges a loader profile (Fabric/Quilt profile JSON, or a Forge installer's version.json)
     * into resolved vanilla metadata: libraries and argument lists are concatenated, scalar
     * launch fields are overridden.
     */
    protected final void mergeProfile(JSONObject base, JSONObject profile) {
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
    }

    /**
     * Loader libraries take precedence: they are appended after the vanilla ones, then
     * de-duplicated by {@code group:artifact[:classifier]} keeping the last occurrence, so a
     * loader-supplied ASM or Guava wins over Mojang's older copy.
     */
    private void mergeLibraries(JSONObject base, JSONObject profile) {
        JSONArray merged = new JSONArray();
        appendArray(merged, base.optJSONArray("libraries"));
        appendArray(merged, profile.optJSONArray("libraries"));
        if (merged.isEmpty()) return;

        java.util.LinkedHashMap<String, JSONObject> byCoordinate = new java.util.LinkedHashMap<>();
        List<JSONObject> unnamed = new ArrayList<>();
        for (int i = 0; i < merged.length(); i++) {
            JSONObject lib = merged.optJSONObject(i);
            if (lib == null) continue;
            String name = lib.optString("name", "");
            if (name.isBlank()) {
                unnamed.add(lib);
                continue;
            }
            byCoordinate.put(versionlessKey(name), lib);
        }

        JSONArray deduped = new JSONArray();
        byCoordinate.values().forEach(deduped::put);
        unnamed.forEach(deduped::put);
        base.put("libraries", deduped);
    }

    /** {@code group:artifact:version[:classifier]} → {@code group:artifact[:classifier]}. */
    private String versionlessKey(String gav) {
        String[] parts = gav.split(":");
        if (parts.length < 3) return gav;
        String key = parts[0] + ":" + parts[1];
        return parts.length >= 4 ? key + ":" + parts[3] : key;
    }

    private void mergeArguments(JSONObject base, JSONObject profile) {
        JSONObject baseArgs = base.optJSONObject("arguments");
        JSONObject profileArgs = profile.optJSONObject("arguments");
        if (baseArgs == null && profileArgs == null) return;

        JSONObject args = new JSONObject();
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

    protected static void appendArray(JSONArray target, JSONArray source) {
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

    protected record Selection(String minecraftVersion, String loaderVersion) {}
}
