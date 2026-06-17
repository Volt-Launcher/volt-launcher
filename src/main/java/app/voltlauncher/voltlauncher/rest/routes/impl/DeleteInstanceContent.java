package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceContentService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/content/{type}/{file}", method = MethodType.DELETE)
public class DeleteInstanceContent implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public DeleteInstanceContent(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String name = ctx.pathParam("name");
        try {
            InstanceContentService.ContentType type = InstanceContentService.ContentType.fromId(ctx.pathParam("type"));
            minecraftLauncher.removeContent(name, type, ctx.pathParam("file"));
            JSONObject json = new JSONObject();
            json.put("success", true);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Deleting instance content failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
