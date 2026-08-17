package app.voltlauncher.providers.curseforge;

import app.voltlauncher.core.config.SettingsStore;
import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.providers.ContentProvider;
import app.voltlauncher.providers.FileHashes;
import app.voltlauncher.providers.model.ContentKind;
import app.voltlauncher.providers.model.ProjectDetail;
import app.voltlauncher.providers.model.ProjectSummary;
import app.voltlauncher.providers.model.ProjectVersion;
import app.voltlauncher.providers.model.ProviderId;
import app.voltlauncher.providers.model.SearchQuery;
import app.voltlauncher.providers.model.SearchResult;
import app.voltlauncher.providers.model.VersionDependency;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CurseForge content, reached through the launcher's bridge service rather than directly.
 *
 * <p>CurseForge's API requires a per-developer key that must not ship inside a desktop client, so
 * the bridge (see {@code bridge/}) holds the key and proxies requests. The bridge deliberately
 * mirrors CurseForge's own routes, which keeps it a thin, stable component; all normalisation
 * onto the launcher's model happens here.
 */
public final class CurseForgeProvider implements ContentProvider {

    /** CurseForge class ids for the Minecraft game. */
    private static final int CLASS_MOD = 6;
    private static final int CLASS_MODPACK = 4471;
    private static final int CLASS_RESOURCEPACK = 12;
    private static final int CLASS_SHADER = 6552;
    private static final int CLASS_DATAPACK = 6945;
    private static final int GAME_MINECRAFT = 432;

    private static final long HEALTH_CACHE_MS = 30_000L;

    private final HttpFetcher http;
    private final SettingsStore settings;

    private volatile long lastHealthCheckAt;
    private volatile boolean healthy;
    private volatile String unavailableReason = "The CurseForge bridge has not been contacted yet.";

    public CurseForgeProvider(HttpFetcher http, SettingsStore settings) {
        this.http = http;
        this.settings = settings;
    }

    @Override
    public ProviderId id() {
        return ProviderId.CURSEFORGE;
    }

    @Override
    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt < HEALTH_CACHE_MS) return healthy;
        lastHealthCheckAt = now;
        try {
            JSONObject response = http.getJson(bridgeUrl() + "/health");
            healthy = response.optBoolean("ok", false) && response.optBoolean("curseforge", false);
            unavailableReason = healthy
                    ? ""
                    : "The CurseForge bridge is running but has no valid API key configured.";
        } catch (Exception e) {
            healthy = false;
            unavailableReason = "The CurseForge bridge is not reachable at " + bridgeUrl()
                    + ". Start it with 'npm start' in the bridge/ directory.";
        }
        return healthy;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public SearchResult search(SearchQuery query) throws Exception {
        StringBuilder url = new StringBuilder(bridgeUrl()).append("/v1/mods/search?gameId=").append(GAME_MINECRAFT);
        url.append("&classId=").append(classIdFor(query.kind()));
        url.append("&sortField=").append(sortFieldFor(query.sort()));
        url.append("&sortOrder=desc");
        url.append("&index=").append(query.offset());
        url.append("&pageSize=").append(query.limit());
        if (query.hasQuery()) {
            url.append("&searchFilter=").append(encode(query.query()));
        }
        if (query.gameVersion() != null) {
            url.append("&gameVersion=").append(encode(query.gameVersion()));
        }
        if (query.loader() != null) {
            int loaderType = modLoaderType(query.loader());
            if (loaderType > 0) url.append("&modLoaderType=").append(loaderType);
        }
        if (!query.categories().isEmpty()) {
            url.append("&categoryIds=").append(encode(new JSONArray(query.categories()).toString()));
        }

        JSONObject response = http.getJson(url.toString());
        JSONArray data = response.optJSONArray("data");

        List<ProjectSummary> hits = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                hits.add(toSummary(data.getJSONObject(i), query.kind()));
            }
        }

        JSONObject pagination = response.optJSONObject("pagination");
        long total = pagination == null ? hits.size() : pagination.optLong("totalCount", hits.size());
        return new SearchResult(hits, total, query.offset(), query.limit());
    }

    @Override
    public ProjectDetail project(String projectIdOrSlug) throws Exception {
        JSONObject response = http.getJson(bridgeUrl() + "/v1/mods/" + encode(projectIdOrSlug));
        JSONObject mod = response.optJSONObject("data");
        if (mod == null) {
            throw new IllegalStateException("CurseForge project not found: " + projectIdOrSlug);
        }

        List<String> gallery = new ArrayList<>();
        JSONArray screenshots = mod.optJSONArray("screenshots");
        if (screenshots != null) {
            for (int i = 0; i < screenshots.length(); i++) {
                String imageUrl = screenshots.getJSONObject(i).optString("url", "");
                if (!imageUrl.isBlank()) gallery.add(imageUrl);
            }
        }

        Map<String, String> links = new LinkedHashMap<>();
        JSONObject linkJson = mod.optJSONObject("links");
        if (linkJson != null) {
            putIfPresent(links, "website", linkJson.optString("websiteUrl", ""));
            putIfPresent(links, "issues", linkJson.optString("issuesUrl", ""));
            putIfPresent(links, "source", linkJson.optString("sourceUrl", ""));
            putIfPresent(links, "wiki", linkJson.optString("wikiUrl", ""));
        }

        return new ProjectDetail(
                toSummary(mod, kindFromClassId(mod.optInt("classId", CLASS_MOD))),
                // The bridge inlines CurseForge's separate description endpoint as "descriptionHtml".
                mod.optString("descriptionHtml", mod.optString("summary", "")),
                gallery,
                "",
                links);
    }

    @Override
    public List<ProjectVersion> versions(String projectIdOrSlug, String gameVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder(bridgeUrl())
                .append("/v1/mods/").append(encode(projectIdOrSlug)).append("/files?pageSize=50");
        if (gameVersion != null && !gameVersion.isBlank()) {
            url.append("&gameVersion=").append(encode(gameVersion));
        }
        if (loader != null && !loader.isBlank()) {
            int loaderType = modLoaderType(loader);
            if (loaderType > 0) url.append("&modLoaderType=").append(loaderType);
        }

        JSONObject response = http.getJson(url.toString());
        JSONArray data = response.optJSONArray("data");
        List<ProjectVersion> versions = new ArrayList<>();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                versions.add(toVersion(data.getJSONObject(i)));
            }
        }
        return versions;
    }

    @Override
    public ProjectVersion version(String versionId) throws Exception {
        JSONObject response = http.getJson(bridgeUrl() + "/v1/files/" + encode(versionId));
        JSONObject file = response.optJSONObject("data");
        if (file == null) {
            throw new IllegalStateException("CurseForge file not found: " + versionId);
        }
        return toVersion(file);
    }

    /**
     * Matches local files against CurseForge by its murmur2 file fingerprint, which is the only
     * lookup CurseForge offers for "what is this jar?" — it has no hash search.
     */
    @Override
    public Map<Path, ProjectVersion> identify(List<Path> files) throws Exception {
        if (files.isEmpty()) return Map.of();

        Map<Long, Path> byFingerprint = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                byFingerprint.putIfAbsent(FileHashes.curseForgeFingerprint(file), file);
            } catch (Exception e) {
                // An unreadable file simply cannot be identified.
            }
        }
        if (byFingerprint.isEmpty()) return Map.of();

        JSONObject body = new JSONObject().put("fingerprints", new JSONArray(byFingerprint.keySet()));
        JSONObject response = http.postJson(bridgeUrl() + "/v1/fingerprints", body);

        JSONObject data = response.optJSONObject("data");
        JSONArray matches = data == null ? null : data.optJSONArray("exactMatches");
        if (matches == null) return Map.of();

        Map<Path, ProjectVersion> resolved = new LinkedHashMap<>();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject file = matches.getJSONObject(i).optJSONObject("file");
            if (file == null) continue;
            Path path = byFingerprint.get(file.optLong("fileFingerprint", -1L));
            if (path == null) continue;
            resolved.put(path, toVersion(file));
        }
        return resolved;
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private ProjectSummary toSummary(JSONObject mod, ContentKind fallbackKind) {
        JSONObject logo = mod.optJSONObject("logo");

        List<String> authors = new ArrayList<>();
        JSONArray authorArray = mod.optJSONArray("authors");
        if (authorArray != null) {
            for (int i = 0; i < authorArray.length(); i++) {
                String name = authorArray.getJSONObject(i).optString("name", "");
                if (!name.isBlank()) authors.add(name);
            }
        }

        List<String> categories = new ArrayList<>();
        JSONArray categoryArray = mod.optJSONArray("categories");
        if (categoryArray != null) {
            for (int i = 0; i < categoryArray.length(); i++) {
                String name = categoryArray.getJSONObject(i).optString("name", "");
                if (!name.isBlank()) categories.add(name);
            }
        }

        List<String> gameVersions = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        JSONArray latestIndexes = mod.optJSONArray("latestFilesIndexes");
        if (latestIndexes != null) {
            for (int i = 0; i < latestIndexes.length(); i++) {
                JSONObject index = latestIndexes.getJSONObject(i);
                String gameVersion = index.optString("gameVersion", "");
                if (!gameVersion.isBlank() && !gameVersions.contains(gameVersion)) {
                    gameVersions.add(gameVersion);
                }
                String loader = loaderName(index.optInt("modLoader", 0));
                if (loader != null && !loaders.contains(loader)) loaders.add(loader);
            }
        }

        return new ProjectSummary(
                ProviderId.CURSEFORGE,
                String.valueOf(mod.optInt("id", 0)),
                mod.optString("slug", ""),
                mod.optString("name", ""),
                mod.optString("summary", ""),
                authors.isEmpty() ? "" : authors.getFirst(),
                logo == null ? "" : logo.optString("thumbnailUrl", logo.optString("url", "")),
                mod.optLong("downloadCount", 0L),
                mod.optLong("thumbsUpCount", 0L),
                categories,
                loaders,
                gameVersions,
                kindFromClassId(mod.optInt("classId", classIdFor(fallbackKind))),
                mod.optString("dateModified", ""));
    }

    /** Package-visible so the pack installer can map bulk file lookups without re-fetching each file. */
    ProjectVersion toVersion(JSONObject file) {
        List<String> gameVersions = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        JSONArray gameVersionArray = file.optJSONArray("gameVersions");
        if (gameVersionArray != null) {
            for (int i = 0; i < gameVersionArray.length(); i++) {
                // CurseForge mixes loader names and Minecraft versions into one list.
                String value = gameVersionArray.optString(i, "");
                if (value.isBlank()) continue;
                if (isLoaderName(value)) {
                    loaders.add(value.toLowerCase(Locale.ROOT));
                } else {
                    gameVersions.add(value);
                }
            }
        }

        String sha1 = "";
        JSONArray hashes = file.optJSONArray("hashes");
        if (hashes != null) {
            for (int i = 0; i < hashes.length(); i++) {
                JSONObject hash = hashes.getJSONObject(i);
                // algo 1 = SHA-1, 2 = MD5
                if (hash.optInt("algo", 0) == 1) {
                    sha1 = hash.optString("value", "");
                    break;
                }
            }
        }

        List<VersionDependency> dependencies = new ArrayList<>();
        JSONArray dependencyArray = file.optJSONArray("dependencies");
        if (dependencyArray != null) {
            for (int i = 0; i < dependencyArray.length(); i++) {
                JSONObject dependency = dependencyArray.getJSONObject(i);
                dependencies.add(new VersionDependency(
                        String.valueOf(dependency.optInt("modId", 0)),
                        "",
                        relationTypeName(dependency.optInt("relationType", 0))));
            }
        }

        int fileId = file.optInt("id", 0);
        String fileName = file.optString("fileName", "");
        // CurseForge has no version-number field; the display name is the closest thing to one and
        // is what the site itself shows. The raw file name is kept in its own field, so using it as
        // the version would just repeat it — and reads badly next to a Modrinth "0.5.8".
        String displayName = file.optString("displayName", fileName);

        return new ProjectVersion(
                ProviderId.CURSEFORGE,
                String.valueOf(fileId),
                String.valueOf(file.optInt("modId", 0)),
                displayName,
                displayName,
                gameVersions,
                loaders,
                releaseTypeName(file.optInt("releaseType", 1)),
                resolveDownloadUrl(file, fileId, fileName),
                fileName,
                sha1,
                file.optLong("fileLength", 0L),
                file.optString("fileDate", ""),
                dependencies);
    }

    /**
     * Authors may opt out of third-party downloads, in which case CurseForge returns a null
     * {@code downloadUrl}. The file is still served from the CDN under a path derived from its id,
     * which is the documented fallback every launcher uses.
     */
    private String resolveDownloadUrl(JSONObject file, int fileId, String fileName) {
        String url = file.optString("downloadUrl", "");
        if (!url.isBlank() && !"null".equals(url)) return url;
        if (fileId <= 0 || fileName.isBlank()) return "";

        String id = String.valueOf(fileId);
        String prefix = id.length() > 4 ? id.substring(0, id.length() - 3) : id;
        String suffix = id.length() > 3 ? id.substring(id.length() - 3) : "0";
        return "https://edge.forgecdn.net/files/" + prefix + "/"
                + Integer.parseInt(suffix) + "/" + encodePathSegment(fileName);
    }

    private int classIdFor(ContentKind kind) {
        return switch (kind) {
            case MOD -> CLASS_MOD;
            case MODPACK -> CLASS_MODPACK;
            case RESOURCEPACK -> CLASS_RESOURCEPACK;
            case SHADER -> CLASS_SHADER;
            case DATAPACK -> CLASS_DATAPACK;
        };
    }

    private ContentKind kindFromClassId(int classId) {
        return switch (classId) {
            case CLASS_MODPACK -> ContentKind.MODPACK;
            case CLASS_RESOURCEPACK -> ContentKind.RESOURCEPACK;
            case CLASS_SHADER -> ContentKind.SHADER;
            case CLASS_DATAPACK -> ContentKind.DATAPACK;
            default -> ContentKind.MOD;
        };
    }

    private int sortFieldFor(String sort) {
        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "downloads" -> 6;
            case "newest", "created" -> 11;
            case "updated" -> 3;
            case "follows", "followers" -> 2;
            default -> 1;
        };
    }

    private int modLoaderType(String loader) {
        return switch (loader.toLowerCase(Locale.ROOT)) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> 0;
        };
    }

    private String loaderName(int modLoaderType) {
        return switch (modLoaderType) {
            case 1 -> "forge";
            case 2 -> "cauldron";
            case 3 -> "liteloader";
            case 4 -> "fabric";
            case 5 -> "quilt";
            case 6 -> "neoforge";
            default -> null;
        };
    }

    private boolean isLoaderName(String value) {
        return modLoaderType(value) > 0
                || "liteloader".equalsIgnoreCase(value)
                || "cauldron".equalsIgnoreCase(value);
    }

    private String releaseTypeName(int releaseType) {
        return switch (releaseType) {
            case 2 -> "beta";
            case 3 -> "alpha";
            default -> "release";
        };
    }

    private String relationTypeName(int relationType) {
        return switch (relationType) {
            case 2 -> "optional";
            case 3 -> "required";
            case 4 -> "embedded";
            case 5 -> "incompatible";
            default -> "optional";
        };
    }

    private String bridgeUrl() {
        String url = settings.get().curseForgeBridgeUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }
}
