package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.auth.MinecraftAccountSession;
import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/launch", method = MethodType.POST)
public class PostInstanceLaunch implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;
    private final MicrosoftAuth msAuth;

    public PostInstanceLaunch(MinecraftLauncherService minecraftLauncher, MicrosoftAuth msAuth) {
        this.minecraftLauncher = minecraftLauncher;
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            MinecraftAccountSession session = msAuth.getLaunchSession();
            minecraftLauncher.launchInstanceAsync(session, instanceName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instanceName", instanceName);
            json.put("status", "installing");
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(400).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Minecraft launch failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}

