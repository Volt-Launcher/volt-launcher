package app.voltlauncher.providers.modrinth;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.providers.content.AbstractModpackInstaller;
import app.voltlauncher.providers.content.ProgressSink;
import app.voltlauncher.providers.model.ProviderId;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Installs Modrinth {@code .mrpack} archives. */
public final class ModrinthPackInstaller extends AbstractModpackInstaller {

    private static final String INDEX_ENTRY = "modrinth.index.json";
    private static final List<String> OVERRIDE_PREFIXES = List.of("overrides", "client-overrides");

    public ModrinthPackInstaller(HttpFetcher http) {
        super(http);
    }

    @Override
    public ProviderId provider() {
        return ProviderId.MODRINTH;
    }

    @Override
    public PackInfo fetchPackInfo(String modrinthVersionId) throws Exception {
        JSONObject versionMeta = http.getJson(
                ModrinthProvider.API_BASE + "/version/" + modrinthVersionId, ModrinthProvider.HEADERS);

        JSONObject packFile = pickPackFile(versionMeta.optJSONArray("files"));
        if (packFile == null) {
            throw new IllegalStateException("No .mrpack file found for Modrinth version " + modrinthVersionId);
        }
        JSONObject hashes = packFile.optJSONObject("hashes");

        Path archive = Files.createTempFile("voltlauncher-pack-", ".mrpack");
        try {
            http.download(packFile.getString("url"), archive,
                    hashes == null ? "" : hashes.optString("sha1", ""));

            JSONObject index = readJsonEntry(archive, INDEX_ENTRY);
            return new PackInfo(resolveVersionId(index), index.optString("name", "Modpack"), archive);
        } catch (Exception e) {
            Files.deleteIfExists(archive);
            throw e;
        }
    }

    @Override
    public void applyPackContents(Instance instance, Path archive, ProgressSink progress) throws Exception {
        try {
            JSONObject index = readJsonEntry(archive, INDEX_ENTRY);

            downloadFiles(instance.gameDirectory(), collectFiles(index.optJSONArray("files")), progress);

            progress.stage(STAGE_OVERRIDES);
            applyOverrides(instance.gameDirectory(), archive, OVERRIDE_PREFIXES);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private JSONObject pickPackFile(JSONArray files) {
        if (files == null) return null;
        JSONObject firstMrpack = null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            if (file.optBoolean("primary", false)) return file;
            if (firstMrpack == null && file.optString("filename", "").endsWith(".mrpack")) {
                firstMrpack = file;
            }
        }
        return firstMrpack;
    }

    /** Maps the pack's declared dependencies onto a launcher version id. */
    private String resolveVersionId(JSONObject index) {
        JSONObject dependencies = index.optJSONObject("dependencies");
        if (dependencies == null) {
            throw new IllegalStateException("The modpack does not declare any dependencies");
        }
        String minecraftVersion = dependencies.optString("minecraft", "");
        if (minecraftVersion.isBlank()) {
            throw new IllegalStateException("The modpack does not declare a Minecraft version");
        }

        if (dependencies.has("neoforge")) {
            return "neoforge:" + minecraftVersion + ":" + dependencies.getString("neoforge");
        }
        if (dependencies.has("fabric-loader")) {
            return "fabric:" + minecraftVersion + ":" + dependencies.getString("fabric-loader");
        }
        if (dependencies.has("forge")) {
            return "forge:" + minecraftVersion + ":" + dependencies.getString("forge");
        }
        if (dependencies.has("quilt-loader")) {
            return "quilt:" + minecraftVersion + ":" + dependencies.getString("quilt-loader");
        }
        return minecraftVersion;
    }

    private List<RemoteFile> collectFiles(JSONArray files) {
        List<RemoteFile> result = new ArrayList<>();
        if (files == null) return result;

        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            String relativePath = file.optString("path", "");
            if (relativePath.isBlank()) continue;

            // Server-only files are declared as unsupported on the client and must be skipped,
            // otherwise the pack installs mods that crash a client launch.
            JSONObject env = file.optJSONObject("env");
            if (env != null && "unsupported".equals(env.optString("client", ""))) continue;

            JSONArray downloads = file.optJSONArray("downloads");
            if (downloads == null || downloads.isEmpty()) continue;

            JSONObject hashes = file.optJSONObject("hashes");
            String hash = hashes == null ? ""
                    : hashes.has("sha512") ? hashes.getString("sha512") : hashes.optString("sha1", "");

            result.add(new RemoteFile(relativePath, downloads.getString(0), hash));
        }
        return result;
    }
}
