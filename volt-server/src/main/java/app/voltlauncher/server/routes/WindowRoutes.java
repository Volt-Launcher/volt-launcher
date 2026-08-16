package app.voltlauncher.server.routes;

import app.voltlauncher.server.route.RouteModule;
import app.voltlauncher.server.route.RouteRegistry;
import io.javalin.http.Context;
import org.json.JSONObject;

/**
 * Window chrome controls for the frameless Electron shell. The renderer normally drives these
 * over IPC; these routes exist so the window stays controllable if IPC is unavailable.
 */
public final class WindowRoutes implements RouteModule {

    private final Runnable minimize;
    private final Runnable maximize;
    private final Runnable close;

    public WindowRoutes(Runnable minimize, Runnable maximize, Runnable close) {
        this.minimize = minimize;
        this.maximize = maximize;
        this.close = close;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.post("/api/window/minimize", ctx -> run(minimize));
        routes.post("/api/window/maximize", ctx -> run(maximize));
        routes.post("/api/window/close", ctx -> run(close));
    }

    private JSONObject run(Runnable action) {
        action.run();
        return new JSONObject();
    }
}
