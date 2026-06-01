package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

@Route(path = "/api/instances/{name}/open-folder", method = MethodType.POST)
public class PostInstanceOpenFolder implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public PostInstanceOpenFolder(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String instanceName = ctx.pathParam("name");
        try {
            Path folder = minecraftLauncher.getInstanceFolder(instanceName);
            Files.createDirectories(folder);

            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", folder.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", folder.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", folder.toString());
            }
            pb.start();

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("path", folder.toString());
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalStateException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(404).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Opening instance folder failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
