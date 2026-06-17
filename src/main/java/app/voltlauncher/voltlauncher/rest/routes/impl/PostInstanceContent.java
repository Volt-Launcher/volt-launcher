package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.InstanceContentService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds one or more files (by absolute local path) to a content folder.
 * The Electron renderer resolves dropped file paths and posts them here.
 */
@Route(path = "/api/instances/{name}/content/{type}", method = MethodType.POST)
public class PostInstanceContent implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public PostInstanceContent(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String name = ctx.pathParam("name");
        try {
            InstanceContentService.ContentType type = InstanceContentService.ContentType.fromId(ctx.pathParam("type"));
            JSONObject body = new JSONObject(ctx.body());
            JSONArray paths = body.optJSONArray("paths");
            if (paths == null || paths.isEmpty()) {
                throw new IllegalArgumentException("No file paths provided");
            }

            JSONArray added = new JSONArray();
            List<String> failures = new ArrayList<>();
            for (int i = 0; i < paths.length(); i++) {
                String raw = paths.optString(i, "").trim();
                if (raw.isEmpty()) continue;
                try {
                    InstanceContentService.ContentEntry entry = minecraftLauncher.addContent(name, type, Path.of(raw));
                    added.put(InstanceHelper.toContentJson(entry));
                } catch (Exception e) {
                    failures.add(raw + ": " + e.getMessage());
                }
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("added", added);
            if (!failures.isEmpty()) json.put("failures", failures);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException | IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Adding instance content failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
