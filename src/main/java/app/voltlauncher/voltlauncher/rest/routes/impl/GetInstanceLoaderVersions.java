package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

@Route(path = "/api/instances/loader-versions")
public class GetInstanceLoaderVersions implements IRoute {

    private final MinecraftLauncherService minecraftLauncher;

    public GetInstanceLoaderVersions(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String platformId = ctx.queryParam("platformId");
        String minecraftVersionId = ctx.queryParam("minecraftVersionId");

        if (platformId == null || platformId.isBlank() || minecraftVersionId == null || minecraftVersionId.isBlank()) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", "Query params 'platformId' and 'minecraftVersionId' are required");
            ctx.status(400).contentType("application/json").result(json.toString());
            return;
        }

        try {
            JSONArray versions = new JSONArray();
            for (AvailableVersion version : minecraftLauncher.listLoaderVersions(platformId, minecraftVersionId)) {
                versions.put(RestServer.toVersionJson(version));
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("versions", versions);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException e) {
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


