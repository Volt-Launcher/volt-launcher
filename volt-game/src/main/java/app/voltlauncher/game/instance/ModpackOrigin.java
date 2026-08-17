package app.voltlauncher.game.instance;

import org.json.JSONObject;

/**
 * The modpack a profile was created from, remembered so the launcher can offer to update it later.
 * A profile the user built by hand has none.
 *
 * @param sourceFile file name of a locally imported archive, blank for packs installed from a
 *                   provider — an imported pack has no upstream to check for updates
 */
public record ModpackOrigin(
        String provider,
        String projectId,
        String versionId,
        String versionNumber,
        String name,
        String sourceFile,
        String iconUrl) {

    public ModpackOrigin {
        provider = blankToEmpty(provider);
        projectId = blankToEmpty(projectId);
        versionId = blankToEmpty(versionId);
        versionNumber = blankToEmpty(versionNumber);
        name = blankToEmpty(name);
        sourceFile = blankToEmpty(sourceFile);
        iconUrl = blankToEmpty(iconUrl);
    }

    /** Imported archives carry no project id, so there is nothing to check for a newer release. */
    public boolean isUpdatable() {
        return !provider.isEmpty() && !projectId.isEmpty();
    }

    public ModpackOrigin withVersion(String newVersionId, String newVersionNumber) {
        return new ModpackOrigin(provider, projectId, newVersionId, newVersionNumber, name, sourceFile, iconUrl);
    }

    public JSONObject toJson() {
        return new JSONObject()
                .put("provider", provider)
                .put("projectId", projectId)
                .put("versionId", versionId)
                .put("versionNumber", versionNumber)
                .put("name", name)
                .put("sourceFile", sourceFile)
                .put("iconUrl", iconUrl)
                .put("updatable", isUpdatable());
    }

    public static ModpackOrigin fromJson(JSONObject json) {
        if (json == null) return null;
        return new ModpackOrigin(
                json.optString("provider", ""),
                json.optString("projectId", ""),
                json.optString("versionId", ""),
                json.optString("versionNumber", ""),
                json.optString("name", ""),
                json.optString("sourceFile", ""),
                json.optString("iconUrl", ""));
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
