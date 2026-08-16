package app.voltlauncher.providers.modrinth;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.providers.ContentProvider;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Modrinth Labrinth API v2. Public and unauthenticated, so no bridge is involved. */
public final class ModrinthProvider implements ContentProvider {

    static final String API_BASE = "https://api.modrinth.com/v2";
    static final Map<String, String> HEADERS = Map.of("User-Agent", ModrinthUserAgent.VALUE);

    private final HttpFetcher http;

    public ModrinthProvider(HttpFetcher http) {
        this.http = http;
    }

    @Override
    public ProviderId id() {
        return ProviderId.MODRINTH;
    }

    @Override
    public SearchResult search(SearchQuery query) throws Exception {
        StringBuilder url = new StringBuilder(API_BASE).append("/search?");
        url.append("index=").append(encode(mapSort(query.sort())));
        url.append("&offset=").append(query.offset());
        url.append("&limit=").append(query.limit());
        if (query.hasQuery()) {
            url.append("&query=").append(encode(query.query()));
        }
        url.append("&facets=").append(encode(buildFacets(query)));

        JSONObject response = http.getJson(url.toString(), HEADERS);
        JSONArray hits = response.optJSONArray("hits");

        List<ProjectSummary> summaries = new ArrayList<>();
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                summaries.add(toSummary(hits.getJSONObject(i), query.kind()));
            }
        }
        return new SearchResult(summaries, response.optLong("total_hits", summaries.size()),
                response.optInt("offset", query.offset()), response.optInt("limit", query.limit()));
    }

    @Override
    public ProjectDetail project(String projectIdOrSlug) throws Exception {
        JSONObject json = http.getJson(API_BASE + "/project/" + encode(projectIdOrSlug), HEADERS);

        List<String> gallery = new ArrayList<>();
        JSONArray galleryArray = json.optJSONArray("gallery");
        if (galleryArray != null) {
            for (int i = 0; i < galleryArray.length(); i++) {
                String imageUrl = galleryArray.getJSONObject(i).optString("url", "");
                if (!imageUrl.isBlank()) gallery.add(imageUrl);
            }
        }

        Map<String, String> links = new LinkedHashMap<>();
        putIfPresent(links, "issues", json.optString("issues_url", ""));
        putIfPresent(links, "source", json.optString("source_url", ""));
        putIfPresent(links, "wiki", json.optString("wiki_url", ""));
        putIfPresent(links, "discord", json.optString("discord_url", ""));

        JSONObject license = json.optJSONObject("license");
        return new ProjectDetail(
                toSummary(json, kindOf(json.optString("project_type", "mod"))),
                json.optString("body", ""),
                gallery,
                license == null ? "" : license.optString("name", license.optString("id", "")),
                links);
    }

    @Override
    public List<ProjectVersion> versions(String projectIdOrSlug, String gameVersion, String loader) throws Exception {
        StringBuilder url = new StringBuilder(API_BASE)
                .append("/project/").append(encode(projectIdOrSlug)).append("/version");

        List<String> filters = new ArrayList<>();
        if (gameVersion != null && !gameVersion.isBlank()) {
            filters.add("game_versions=" + encode("[\"" + gameVersion + "\"]"));
        }
        if (loader != null && !loader.isBlank()) {
            filters.add("loaders=" + encode("[\"" + loader.toLowerCase(java.util.Locale.ROOT) + "\"]"));
        }
        if (!filters.isEmpty()) {
            url.append('?').append(String.join("&", filters));
        }

        JSONArray array = http.getJsonArray(url.toString());
        List<ProjectVersion> versions = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            versions.add(toVersion(array.getJSONObject(i)));
        }
        return versions;
    }

    @Override
    public ProjectVersion version(String versionId) throws Exception {
        return toVersion(http.getJson(API_BASE + "/version/" + encode(versionId), HEADERS));
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    /**
     * Modrinth facets are a list of AND-ed groups whose members are OR-ed, e.g.
     * {@code [["project_type:mod"],["categories:fabric"],["versions:1.21.1"]]}.
     */
    private String buildFacets(SearchQuery query) {
        JSONArray facets = new JSONArray();
        facets.put(new JSONArray().put("project_type:" + modrinthProjectType(query.kind())));

        if (query.gameVersion() != null) {
            facets.put(new JSONArray().put("versions:" + query.gameVersion()));
        }
        if (query.loader() != null) {
            facets.put(new JSONArray().put("categories:" + query.loader().toLowerCase(java.util.Locale.ROOT)));
        }
        if (!query.categories().isEmpty()) {
            JSONArray group = new JSONArray();
            query.categories().forEach(category ->
                    group.put("categories:" + category.toLowerCase(java.util.Locale.ROOT)));
            facets.put(group);
        }
        return facets.toString();
    }

    /** Modrinth serves data packs through the {@code mod} type with a {@code datapack} category. */
    private String modrinthProjectType(ContentKind kind) {
        return switch (kind) {
            case MOD -> "mod";
            case MODPACK -> "modpack";
            case RESOURCEPACK -> "resourcepack";
            case SHADER -> "shader";
            case DATAPACK -> "datapack";
        };
    }

    private String mapSort(String sort) {
        return switch (sort.toLowerCase(java.util.Locale.ROOT)) {
            case "downloads" -> "downloads";
            case "follows", "followers" -> "follows";
            case "newest", "created" -> "newest";
            case "updated" -> "updated";
            default -> "relevance";
        };
    }

    private ContentKind kindOf(String projectType) {
        try {
            return ContentKind.fromId(projectType);
        } catch (IllegalArgumentException e) {
            return ContentKind.MOD;
        }
    }

    private ProjectSummary toSummary(JSONObject json, ContentKind fallbackKind) {
        List<String> categories = stringList(json.optJSONArray("categories"));
        List<String> displayCategories = stringList(json.optJSONArray("display_categories"));
        List<String> loaders = stringList(json.optJSONArray("loaders"));

        // Search hits fold loaders into "categories"; project pages expose them separately.
        if (loaders.isEmpty()) {
            loaders = categories.stream().filter(KnownLoaders::isLoader).toList();
        }
        List<String> plainCategories = displayCategories.isEmpty()
                ? categories.stream().filter(category -> !KnownLoaders.isLoader(category)).toList()
                : displayCategories;

        return new ProjectSummary(
                ProviderId.MODRINTH,
                json.optString("project_id", json.optString("id", "")),
                json.optString("slug", ""),
                json.optString("title", ""),
                json.optString("description", ""),
                json.optString("author", ""),
                json.optString("icon_url", ""),
                json.optLong("downloads", 0L),
                json.optLong("follows", json.optLong("followers", 0L)),
                plainCategories,
                loaders,
                // Project pages expose "game_versions"; search hits call the same list "versions".
                stringList(json.has("game_versions")
                        ? json.optJSONArray("game_versions")
                        : json.optJSONArray("versions")),
                kindOf(json.optString("project_type", fallbackKind.id())),
                json.optString("date_modified", json.optString("updated", "")));
    }

    private ProjectVersion toVersion(JSONObject json) {
        JSONObject primaryFile = pickPrimaryFile(json.optJSONArray("files"));
        JSONObject hashes = primaryFile == null ? null : primaryFile.optJSONObject("hashes");

        List<VersionDependency> dependencies = new ArrayList<>();
        JSONArray dependencyArray = json.optJSONArray("dependencies");
        if (dependencyArray != null) {
            for (int i = 0; i < dependencyArray.length(); i++) {
                JSONObject dependency = dependencyArray.getJSONObject(i);
                dependencies.add(new VersionDependency(
                        dependency.optString("project_id", ""),
                        dependency.optString("version_id", ""),
                        dependency.optString("dependency_type", "")));
            }
        }

        return new ProjectVersion(
                ProviderId.MODRINTH,
                json.optString("id", ""),
                json.optString("project_id", ""),
                json.optString("name", ""),
                json.optString("version_number", ""),
                stringList(json.optJSONArray("game_versions")),
                stringList(json.optJSONArray("loaders")),
                json.optString("version_type", "release"),
                primaryFile == null ? "" : primaryFile.optString("url", ""),
                primaryFile == null ? "" : primaryFile.optString("filename", ""),
                hashes == null ? "" : hashes.optString("sha1", ""),
                primaryFile == null ? 0L : primaryFile.optLong("size", 0L),
                json.optString("date_published", ""),
                dependencies);
    }

    private JSONObject pickPrimaryFile(JSONArray files) {
        if (files == null || files.isEmpty()) return null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            if (file.optBoolean("primary", false)) return file;
        }
        return files.getJSONObject(0);
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    static List<String> stringList(JSONArray array) {
        if (array == null) return List.of();
        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
