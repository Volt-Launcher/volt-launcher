package app.voltlauncher.providers.model;

import org.json.JSONObject;

/**
 * A dependency declared by a project version.
 *
 * @param relation {@code required}, {@code optional}, {@code incompatible} or {@code embedded}
 */
public record VersionDependency(String projectId, String versionId, String relation) {

    public boolean isRequired() {
        return "required".equalsIgnoreCase(relation);
    }

    public JSONObject toJson() {
        return new JSONObject()
                .put("projectId", projectId == null ? "" : projectId)
                .put("versionId", versionId == null ? "" : versionId)
                .put("relation", relation == null ? "" : relation);
    }
}
