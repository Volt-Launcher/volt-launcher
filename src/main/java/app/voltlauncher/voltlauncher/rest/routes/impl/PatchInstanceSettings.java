package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceSettings;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/settings", method = MethodType.PATCH)
public class PatchInstanceSettings implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public PatchInstanceSettings(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            JSONObject body = new JSONObject(ctx.body());
            InstanceSettings settings = InstanceSettings.fromJson(body);
            Instance updated = minecraftLauncher.updateInstanceSettings(instanceName, settings);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instance", InstanceHelper.toInstanceJson(updated, minecraftLauncher));
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Updating instance settings failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
