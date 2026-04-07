package app.voltlauncher.voltlauncher.rest.routes.impl;


import app.voltlauncher.voltlauncher.launcher.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

@Route(path = "/api/instances/versions")
public class GetInstancesVersions implements IRoute {
    private MinecraftLauncherService minecraftLauncher;

    public GetInstancesVersions(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        boolean includeSnapshots = ctx.queryParamAsClass("includeSnapshots", Boolean.class).getOrDefault(false);
        boolean includeBetas = ctx.queryParamAsClass("includeBetas", Boolean.class).getOrDefault(false);
        boolean includeAlphas = ctx.queryParamAsClass("includeAlphas", Boolean.class).getOrDefault(false);

        try {
            JSONArray versions = new JSONArray();
            for (AvailableVersion version : minecraftLauncher.listVersions(
                    includeSnapshots,
                    includeBetas,
                    includeAlphas)) {
                versions.put(toVersionJson(version));
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("versions", versions);
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }

    private JSONObject toVersionJson(AvailableVersion version) {
        JSONObject json = new JSONObject();
        json.put("id", version.id());
        json.put("type", version.type());
        json.put("releaseTime", version.releaseTime());
        return json;
    }
}
