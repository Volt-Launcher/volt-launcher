package de.eztxm.thelauncherproject.rest.routes.impl;

import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/test")
public class Test implements IRoute {
    @Override
    public void execute(Context ctx) {
        JSONObject json = new JSONObject();
        json.put("message", "Hello from Javalin!");
        ctx.contentType("application/json").result(json.toString());
    }
}
