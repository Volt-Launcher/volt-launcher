package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
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

