package app.voltlauncher.providers.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** A downloadable release of a project. */
public record ProjectVersion(
        ProviderId provider,
        String versionId,
        String projectId,
        String name,
        String versionNumber,
        List<String> gameVersions,
        List<String> loaders,
        String releaseType,
        String downloadUrl,
        String fileName,
        String sha1,
        long fileSize,
        String datePublished,
        List<VersionDependency> dependencies) {

    public ProjectVersion {
        gameVersions = gameVersions == null ? List.of() : List.copyOf(gameVersions);
        loaders = loaders == null ? List.of() : List.copyOf(loaders);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public boolean isDownloadable() {
        return downloadUrl != null && !downloadUrl.isBlank();
    }

    public JSONObject toJson() {
        JSONArray deps = new JSONArray();
        dependencies.forEach(dependency -> deps.put(dependency.toJson()));

        return new JSONObject()
                .put("provider", provider.id())
                .put("versionId", versionId)
                .put("projectId", projectId == null ? "" : projectId)
                .put("name", name == null ? "" : name)
                .put("versionNumber", versionNumber == null ? "" : versionNumber)
                .put("gameVersions", new JSONArray(gameVersions))
                .put("loaders", new JSONArray(loaders))
                .put("releaseType", releaseType == null ? "release" : releaseType)
                .put("fileName", fileName == null ? "" : fileName)
                .put("fileSize", fileSize)
                .put("datePublished", datePublished == null ? "" : datePublished)
                .put("downloadable", isDownloadable())
                .put("dependencies", deps);
    }
}
