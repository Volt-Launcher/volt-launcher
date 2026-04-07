package app.voltlauncher.voltlauncher.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class JsonUtil {

    private JsonUtil() {}

    public static JSONObject parse(String json) {
        return new JSONObject(json);
    }

    public static JSONArray parseArray(String json) {
        return new JSONArray(json);
    }

    public static JSONObject readFile(Path path) throws Exception {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static JSONArray readFileArray(Path path) throws Exception {
        return parseArray(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void writeFile(Path path, JSONObject json) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json.toString(2), StandardCharsets.UTF_8);
    }

    public static Optional<String> getString(JSONObject root, String dotPath) {
        JSONObject node = walkPath(root, dotPath);
        if (node == null) {
            return Optional.empty();
        }
        String key = lastKey(dotPath);
        if (!node.has(key)) {
            return Optional.empty();
        }
        return Optional.of(node.getString(key));
    }

    public static Optional<JSONObject> getObject(JSONObject root, String dotPath) {
        JSONObject node = walkPath(root, dotPath);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(node.optJSONObject(lastKey(dotPath)));
    }

    public static Optional<JSONArray> getArray(JSONObject root, String dotPath) {
        JSONObject node = walkPath(root, dotPath);
        if (node == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(node.optJSONArray(lastKey(dotPath)));
    }

    public static Optional<Integer> getInt(JSONObject root, String dotPath) {
        JSONObject node = walkPath(root, dotPath);
        if (node == null) {
            return Optional.empty();
        }
        String key = lastKey(dotPath);
        if (!node.has(key)) {
            return Optional.empty();
        }
        return Optional.of(node.getInt(key));
    }

    public static String requireString(JSONObject root, String dotPath) {
        return getString(root, dotPath)
                .orElseThrow(() -> new IllegalStateException("Missing required JSON field: " + dotPath));
    }

    public static JSONObject requireObject(JSONObject root, String dotPath) {
        return getObject(root, dotPath)
                .orElseThrow(() -> new IllegalStateException("Missing required JSON object: " + dotPath));
    }

    public static JSONArray requireArray(JSONObject root, String dotPath) {
        return getArray(root, dotPath)
                .orElseThrow(() -> new IllegalStateException("Missing required JSON array: " + dotPath));
    }

    private static JSONObject walkPath(JSONObject root, String dotPath) {
        String[] parts = dotPath.split("\\.", -1);
        JSONObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            current = current.optJSONObject(parts[i]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String lastKey(String dotPath) {
        int dot = dotPath.lastIndexOf('.');
        if (dot < 0) {
            return dotPath;
        }
        return dotPath.substring(dot + 1);
    }
}
