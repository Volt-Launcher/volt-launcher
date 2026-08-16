package app.voltlauncher.auth.skin;

import org.json.JSONObject;

/**
 * A skin in the user's local library.
 *
 * @param slim whether the texture uses the 3px-arm ("Alex") model rather than the classic one
 */
public record SavedSkin(String id, String name, boolean slim, long createdAt) {

    public SavedSkin withName(String newName) {
        return new SavedSkin(id, newName, slim, createdAt);
    }

    public JSONObject toJson() {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("slim", slim)
                .put("createdAt", createdAt);
    }

    public static SavedSkin fromJson(JSONObject json) {
        return new SavedSkin(
                json.getString("id"),
                json.getString("name"),
                json.optBoolean("slim", false),
                json.optLong("createdAt", 0L));
    }
}
