package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances", method = MethodType.POST)
public class PostInstances implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public PostInstances(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        try {
            JSONObject body = new JSONObject(ctx.body());
            String name = body.optString("name", "");
            String versionId = body.optString("versionId", "");

            Instance instance = minecraftLauncher.createInstance(name, versionId);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instance", InstanceHelper.toInstanceJson(instance, this.minecraftLauncher));
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(409).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Creating instance failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}

