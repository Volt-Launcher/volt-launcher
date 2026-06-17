package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceContentService;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/content/{type}")
public class GetInstanceContent implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public GetInstanceContent(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String name = ctx.pathParam("name");
        try {
            InstanceContentService.ContentType type = InstanceContentService.ContentType.fromId(ctx.pathParam("type"));
            JSONArray items = new JSONArray();
            for (InstanceContentService.ContentEntry entry : minecraftLauncher.listContent(name, type)) {
                items.put(InstanceHelper.toContentJson(entry));
            }
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("items", items);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
