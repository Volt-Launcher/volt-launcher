package app.voltlauncher.voltlauncher.rest.routes.impl;


import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/stop", method = MethodType.POST)
public class PostInstanceStop implements IRoute {
    private MinecraftLauncherService minecraftLauncher;

    public PostInstanceStop(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            boolean stopped = minecraftLauncher.stopInstance(instanceName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instanceName", instanceName);
            json.put("stopped", stopped);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Stopping instance failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
