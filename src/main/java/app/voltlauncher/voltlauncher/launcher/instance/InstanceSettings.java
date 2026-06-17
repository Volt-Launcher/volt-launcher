package app.voltlauncher.voltlauncher.launcher.instance;

import org.json.JSONObject;

/**
 * Per-instance launch overrides. Any {@code null} field means "use the launcher default".
 */
public record InstanceSettings(
        Integer maxMemoryMb,
        Integer minMemoryMb,
        String jvmArgs,
        Integer resolutionWidth,
        Integer resolutionHeight,
        String javaPath) {

    public static InstanceSettings defaults() {
        return new InstanceSettings(null, null, null, null, null, null);
    }

    public static InstanceSettings fromJson(JSONObject json) {
        if (json == null) return defaults();
        return new InstanceSettings(
                json.has("maxMemoryMb") && !json.isNull("maxMemoryMb") ? json.getInt("maxMemoryMb") : null,
                json.has("minMemoryMb") && !json.isNull("minMemoryMb") ? json.getInt("minMemoryMb") : null,
                json.has("jvmArgs") && !json.isNull("jvmArgs") ? json.getString("jvmArgs") : null,
                json.has("resolutionWidth") && !json.isNull("resolutionWidth") ? json.getInt("resolutionWidth") : null,
                json.has("resolutionHeight") && !json.isNull("resolutionHeight") ? json.getInt("resolutionHeight") : null,
                json.has("javaPath") && !json.isNull("javaPath") ? json.getString("javaPath") : null);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("maxMemoryMb", maxMemoryMb == null ? JSONObject.NULL : maxMemoryMb);
        json.put("minMemoryMb", minMemoryMb == null ? JSONObject.NULL : minMemoryMb);
        json.put("jvmArgs", jvmArgs == null ? JSONObject.NULL : jvmArgs);
        json.put("resolutionWidth", resolutionWidth == null ? JSONObject.NULL : resolutionWidth);
        json.put("resolutionHeight", resolutionHeight == null ? JSONObject.NULL : resolutionHeight);
        json.put("javaPath", javaPath == null ? JSONObject.NULL : javaPath);
        return json;
    }

    public boolean hasCustomMemory() {
        return maxMemoryMb != null || minMemoryMb != null;
    }

    public boolean hasJavaOverride() {
        return javaPath != null && !javaPath.isBlank();
    }
}
