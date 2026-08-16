package app.voltlauncher.server.route;

import io.javalin.http.Context;
import org.json.JSONObject;

/**
 * A single endpoint. Returning a payload marks the request successful; throwing describes the
 * failure, which {@link RouteRegistry} turns into an error response.
 */
@FunctionalInterface
public interface JsonHandler {

    /** @return the response body, without the {@code success} flag the registry adds */
    JSONObject handle(Context ctx) throws Exception;
}
