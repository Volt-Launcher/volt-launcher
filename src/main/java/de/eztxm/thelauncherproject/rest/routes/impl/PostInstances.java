package de.eztxm.thelauncherproject.rest.routes.impl;

import de.eztxm.thelauncherproject.launcher.LauncherInstance;
import de.eztxm.thelauncherproject.launcher.MinecraftLauncherService;
import de.eztxm.thelauncherproject.rest.MethodType;
import de.eztxm.thelauncherproject.rest.RestServer;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import de.eztxm.thelauncherproject.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances", method = MethodType.POST)
public class PostInstances implements IRoute {
    private MinecraftLauncherService minecraftLauncher;

    public PostInstances(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        try {
            JSONObject body = new JSONObject(ctx.body());
            String name = body.optString("name", "");
            String versionId = body.optString("versionId", "");

            LauncherInstance instance = minecraftLauncher.createInstance(name, versionId);
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
