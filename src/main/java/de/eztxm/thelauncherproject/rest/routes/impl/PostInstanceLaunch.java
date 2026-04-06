package de.eztxm.thelauncherproject.rest.routes.impl;

import de.eztxm.thelauncherproject.launcher.MinecraftLauncherService;
import de.eztxm.thelauncherproject.rest.MethodType;
import de.eztxm.thelauncherproject.rest.RestServer;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/{name}/launch", method = MethodType.POST)
public class PostInstanceLaunch implements IRoute {
    private MinecraftLauncherService minecraftLauncher;
    private MicrosoftAuth msAuth;

    public PostInstanceLaunch(MinecraftLauncherService minecraftLauncher, MicrosoftAuth msAuth) {
        this.minecraftLauncher = minecraftLauncher;
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            MinecraftLauncherService.LaunchResult result = minecraftLauncher.launchInstance(msAuth.getLaunchSession(), instanceName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("instanceName", result.instanceName());
            json.put("version", result.version());
            json.put("pid", result.pid());
            json.put("command", result.command());
            json.put("logFile", result.logFile());
            json.put("javaMajorVersion", result.javaMajorVersion());
            json.put("javaExecutable", result.javaExecutable());
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
