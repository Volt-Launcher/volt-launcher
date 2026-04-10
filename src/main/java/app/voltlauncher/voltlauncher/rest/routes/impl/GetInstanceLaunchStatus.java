package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.LaunchResult;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

// Frontend pollt diesen Endpoint um den Launch-Fortschritt zu verfolgen

@Route(path = "/api/instances/{name}/launch-status")
public class GetInstanceLaunchStatus implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public GetInstanceLaunchStatus(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        MinecraftLauncherService.LaunchState state = minecraftLauncher.getLaunchState(instanceName);
        JSONObject json = new JSONObject();
        json.put("success", true);
        json.put("phase", state.phase().name().toLowerCase());
        if (state.message() != null) { json.put("message", state.message()); }
        if (state.result() != null) {
            LaunchResult r = state.result();
            json.put("instanceName", r.instanceName());
            json.put("version", r.versionId());
            json.put("pid", r.pid());
            json.put("logFile", r.logFile());
            json.put("javaMajorVersion", r.javaMajorVersion());
            json.put("javaExecutable", r.javaExecutable());
        }
        ctx.contentType("application/json").result(json.toString());
    }
}

