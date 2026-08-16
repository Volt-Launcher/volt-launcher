package app.voltlauncher.server.route;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import org.json.JSONObject;

import java.util.NoSuchElementException;

/**
 * Registers endpoints on Javalin and gives every one of them the same response envelope
 * ({@code success} plus either the payload or an {@code error} message) and the same mapping from
 * exception type to HTTP status. Handlers therefore only describe the happy path.
 */
public final class RouteRegistry {

    private static final String JSON = "application/json";

    private final JavalinConfig config;
    private final boolean logRoutes;

    public RouteRegistry(JavalinConfig config, boolean logRoutes) {
        this.config = config;
        this.logRoutes = logRoutes;
    }

    public RouteRegistry get(String path, JsonHandler handler) {
        return add(HandlerType.GET, path, handler);
    }

    public RouteRegistry post(String path, JsonHandler handler) {
        return add(HandlerType.POST, path, handler);
    }

    public RouteRegistry patch(String path, JsonHandler handler) {
        return add(HandlerType.PATCH, path, handler);
    }

    public RouteRegistry put(String path, JsonHandler handler) {
        return add(HandlerType.PUT, path, handler);
    }

    public RouteRegistry delete(String path, JsonHandler handler) {
        return add(HandlerType.DELETE, path, handler);
    }

    public RouteRegistry module(RouteModule module) {
        module.register(this);
        return this;
    }

    private RouteRegistry add(HandlerType method, String path, JsonHandler handler) {
        config.routes.addHttpHandler(method, path, ctx -> dispatch(ctx, handler));
        if (logRoutes) {
            System.out.println("[API] " + method + " " + path);
        }
        return this;
    }

    private void dispatch(Context ctx, JsonHandler handler) {
        try {
            JSONObject payload = handler.handle(ctx);
            JSONObject body = payload == null ? new JSONObject() : payload;
            respond(ctx, 200, body.put("success", true));
        } catch (IllegalArgumentException e) {
            respond(ctx, 400, error(e));
        } catch (NoSuchElementException e) {
            respond(ctx, 404, error(e));
        } catch (IllegalStateException e) {
            // A well-formed request the launcher cannot satisfy right now, e.g. deleting a
            // running instance or a provider whose bridge is offline.
            respond(ctx, 409, error(e));
        } catch (Exception e) {
            System.err.println("[API] " + ctx.method() + " " + ctx.path() + " failed: " + e);
            e.printStackTrace(System.err);
            respond(ctx, 500, error(e));
        }
    }

    private JSONObject error(Exception e) {
        String message = e.getMessage();
        return new JSONObject()
                .put("success", false)
                .put("error", message == null || message.isBlank() ? e.getClass().getSimpleName() : message);
    }

    private void respond(Context ctx, int status, JSONObject body) {
        ctx.status(status).contentType(JSON).result(body.toString());
    }
}
