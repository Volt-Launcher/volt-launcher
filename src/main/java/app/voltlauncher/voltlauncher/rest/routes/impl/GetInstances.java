package app.voltlauncher.voltlauncher.rest.routes.impl;


import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
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
            for (Instance instance : minecraftLauncher.listInstances()) {
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
