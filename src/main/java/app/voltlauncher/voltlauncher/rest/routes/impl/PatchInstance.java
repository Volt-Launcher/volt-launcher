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

@Route(path = "/api/instances/{name}", method = MethodType.PATCH)
public class PatchInstance implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public PatchInstance(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            JSONObject body = new JSONObject(ctx.body());
            String newName = body.optString("name", "").trim();
            if (newName.isEmpty()) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", "New name is required");
                ctx.status(400).contentType("application/json").result(json.toString());
                return;
            }
            Instance renamed = minecraftLauncher.renameInstance(instanceName, newName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instance", InstanceHelper.toInstanceJson(renamed, minecraftLauncher));
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
            RestServer.logError("Renaming instance failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
