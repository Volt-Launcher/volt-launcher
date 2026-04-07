package app.voltlauncher.voltlauncher.rest.routes.impl.window;

import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;

@Route(path = "/api/window/minimize", method = MethodType.POST)
public class PostWindowMinimize implements IRoute {
    private RestServer.WindowAction minimizeWindowAction;

    public PostWindowMinimize(RestServer.WindowAction minimizeWindowAction) {
        this.minimizeWindowAction = minimizeWindowAction;
    }

    @Override
    public void execute(Context ctx) {
        try { minimizeWindowAction.execute(); ctx.contentType("application/json").result("{\"success\":true}"); }
        catch (Exception e) { ctx.status(500).contentType("application/json").result("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}"); }
    }
}
