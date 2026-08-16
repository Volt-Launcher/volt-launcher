package app.voltlauncher.server.route;

import io.javalin.http.Context;
import org.json.JSONException;
import org.json.JSONObject;

/** Helpers for reading request input, converting malformed input into a 400 rather than a 500. */
public final class RequestBody {

    private RequestBody() {}

    public static JSONObject json(Context ctx) {
        String body = ctx.body();
        if (body == null || body.isBlank()) return new JSONObject();
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Request body is not valid JSON");
        }
    }

    public static String requiredString(JSONObject body, String key) {
        String value = body.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    public static String requiredQuery(Context ctx, String key) {
        String value = ctx.queryParam(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required query parameter: " + key);
        }
        return value.trim();
    }

    public static boolean queryFlag(Context ctx, String key, boolean fallback) {
        String value = ctx.queryParam(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    public static int queryInt(Context ctx, String key, int fallback) {
        String value = ctx.queryParam(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Query parameter '" + key + "' must be a number");
        }
    }

    /** Trimmed query parameter, or {@code null} when absent — used for optional filters. */
    public static String optionalQuery(Context ctx, String key) {
        String value = ctx.queryParam(key);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
