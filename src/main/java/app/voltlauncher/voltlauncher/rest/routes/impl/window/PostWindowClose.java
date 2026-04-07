package app.voltlauncher.voltlauncher.rest.routes.impl.window;

import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;

@Route(path = "/api/window/close", method = MethodType.POST)
public class PostWindowClose implements IRoute {
    private RestServer.WindowAction closeWindowAction;

    public PostWindowClose(RestServer.WindowAction closeWindowAction) {
        this.closeWindowAction = closeWindowAction;
    }

    @Override
    public void execute(Context ctx) {
        try { closeWindowAction.execute(); ctx.contentType("application/json").result("{\"success\":true}"); }
        catch (Exception e) { ctx.status(500).contentType("application/json").result("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}"); }
    }
}
