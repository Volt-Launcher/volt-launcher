package app.voltlauncher.providers.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** One discovery result, normalised across providers. */
public record ProjectSummary(
        ProviderId provider,
        String projectId,
        String slug,
        String title,
        String description,
        String author,
        String iconUrl,
        long downloads,
        long follows,
        List<String> categories,
        List<String> loaders,
        List<String> gameVersions,
        ContentKind kind,
        String updated) {

    public ProjectSummary {
        categories = categories == null ? List.of() : List.copyOf(categories);
        loaders = loaders == null ? List.of() : List.copyOf(loaders);
        gameVersions = gameVersions == null ? List.of() : List.copyOf(gameVersions);
    }

    public JSONObject toJson() {
        return new JSONObject()
                .put("provider", provider.id())
                .put("projectId", projectId)
                .put("slug", slug == null ? "" : slug)
                .put("title", title == null ? "" : title)
                .put("description", description == null ? "" : description)
                .put("author", author == null ? "" : author)
                .put("iconUrl", iconUrl == null ? "" : iconUrl)
                .put("downloads", downloads)
                .put("follows", follows)
                .put("categories", new JSONArray(categories))
                .put("loaders", new JSONArray(loaders))
                .put("gameVersions", new JSONArray(gameVersions))
                .put("kind", kind.id())
                .put("updated", updated == null ? "" : updated);
    }
}
