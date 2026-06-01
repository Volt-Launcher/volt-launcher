package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}", method = MethodType.DELETE)
public class DeleteInstance implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public DeleteInstance(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            minecraftLauncher.deleteInstance(instanceName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instanceName", instanceName);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Deleting instance failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
