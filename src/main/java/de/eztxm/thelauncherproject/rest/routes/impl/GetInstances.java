package de.eztxm.thelauncherproject.rest.routes.impl;

import de.eztxm.thelauncherproject.launcher.LauncherInstance;
import de.eztxm.thelauncherproject.launcher.MinecraftLauncherService;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import de.eztxm.thelauncherproject.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

@Route(path = "/api/instances")
public class GetInstances implements IRoute {
    private MinecraftLauncherService minecraftLauncher;

    public GetInstances(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        try {
            JSONArray instances = new JSONArray();
            for (LauncherInstance instance : minecraftLauncher.listInstances()) {
                instances.put(InstanceHelper.toInstanceJson(instance, this.minecraftLauncher));
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instances", instances);
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
