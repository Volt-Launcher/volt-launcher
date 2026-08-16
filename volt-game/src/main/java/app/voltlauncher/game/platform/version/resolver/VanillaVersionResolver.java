package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.game.platform.version.AvailableVersion;
import app.voltlauncher.game.platform.version.IVersionResolver;
import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.core.util.JsonUtil;
import app.voltlauncher.core.util.async.SingleFlight;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public final class VanillaVersionResolver implements IVersionResolver {

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpFetcher http;
    private final SingleFlight < JSONObject> flight = new SingleFlight <> ();

    public VanillaVersionResolver(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public List<AvailableVersion> listAvailableVersions() throws Exception {
        List<AvailableVersion> versions = new ArrayList<>();
        for (ManifestEntry entry : loadEntries()) {
            versions.add(new AvailableVersion(entry.id(), entry.type(), entry.releaseTime()));
        }
        return versions;
    }

    public List<ManifestEntry> loadEntries() throws Exception {
        JSONArray versions = JsonUtil.requireArray(http.getJson(MANIFEST_URL), "versions");
        List<ManifestEntry> result = new ArrayList<>(versions.length());
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            result.add(new ManifestEntry( version.getString("id"), version.getString("type"), version.getString("url"), version.optString("releaseTime", version.optString("time", "")) ));
        }
        return result;
    }

    public ManifestEntry findEntry(String versionId) throws Exception {
        return loadEntries().stream()
        .filter(e -> e.id().equals(versionId))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Minecraft version not found: " + versionId));
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        return flight.call(versionId, () -> resolveInternal(versionId, new HashSet<>()));
    }

    private JSONObject resolveInternal(String versionId, Set<String> visited) throws Exception {
        if (!visited.add(versionId)) {
            throw new IllegalStateException("Circular version inheritance for " + versionId);
        }
        JSONObject raw = http.getJson(findEntry(versionId).url());
        if (!raw.has("inheritsFrom")) {
            return raw;
        }
        JSONObject parent = resolveInternal(raw.getString("inheritsFrom"), visited);
        return merge(parent, raw);
    }

    private JSONObject merge(JSONObject parent, JSONObject child) {
        JSONObject merged = new JSONObject(parent.toString());

        if (parent.has("libraries") || child.has("libraries")) {
            // Fix: new JSONArray() statt new JSONArray(parent.toString())
            JSONArray libs = new JSONArray();
            appendArray(libs, parent.optJSONArray("libraries"));
            appendArray(libs, child.optJSONArray("libraries"));
            merged.put("libraries", libs);
        }

        if (parent.has("arguments") || child.has("arguments")) {
            JSONObject args = new JSONObject();
            mergeArgArray(args, parent.optJSONObject("arguments"), child.optJSONObject("arguments"), "game");
            mergeArgArray(args, parent.optJSONObject("arguments"), child.optJSONObject("arguments"), "jvm");
            if (!args.isEmpty()) {
                merged.put("arguments", args);
            }
        }

        for (String key : child.keySet()) {
            if (Objects.equals(key, "inheritsFrom") || Objects.equals(key, "libraries")
            || Objects.equals(key, "arguments")) {
                continue;
            }
            Object cv = child.get(key);
            if (cv instanceof JSONObject co && merged.opt(key) instanceof JSONObject po) {
                merged.put(key, mergeObjects(po, co));
                continue;
            }
            merged.put(key, deepCopy(cv));
        }
        merged.put("id", child.getString("id"));
        return merged;
    }

    private void mergeArgArray(JSONObject target, JSONObject pa, JSONObject ca, String key) {
        JSONArray arr = new JSONArray();
        appendArray(arr, pa != null ? pa.optJSONArray(key) : null);
        appendArray(arr, ca != null ? ca.optJSONArray(key) : null);
        if (!arr.isEmpty()) {
            target.put(key, arr);
        }
    }

    private JSONObject mergeObjects(JSONObject parent, JSONObject child) {
        JSONObject merged = new JSONObject(parent.toString());
        for (String key : child.keySet()) {
            Object cv = child.get(key);
            if (cv instanceof JSONObject co && merged.opt(key) instanceof JSONObject po) {
                merged.put(key, mergeObjects(po, co));
                continue;
            }
            merged.put(key, deepCopy(cv));
        }
        return merged;
    }

    private void appendArray(JSONArray target, JSONArray source) {
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            target.put(deepCopy(source.get(i)));
        }
    }

    private Object deepCopy(Object v) {
        if (v instanceof JSONObject jo) return new JSONObject(jo.toString());
        if (v instanceof JSONArray ja) return new JSONArray(ja.toString());
        return v;
    }

    public record ManifestEntry(String id, String type, String url, String releaseTime) {}
}
