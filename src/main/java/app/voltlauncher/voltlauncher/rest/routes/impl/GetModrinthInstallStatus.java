package app.voltlauncher.voltlauncher.rest.routes.impl;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import app.voltlauncher.voltlauncher.rest.util.InstanceHelper;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/instances/from-modrinth/status", method = MethodType.GET)
public class GetModrinthInstallStatus implements IRoute {
    private final MinecraftLauncherService minecraftLauncher;

    public GetModrinthInstallStatus(MinecraftLauncherService minecraftLauncher) {
        this.minecraftLauncher = minecraftLauncher;
    }

    @Override
    public void execute(Context ctx) {
        String jobId = ctx.queryParam("jobId");
        if (jobId == null || jobId.isBlank()) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", "jobId is required");
            ctx.status(400).contentType("application/json").result(json.toString());
            return;
        }

        MinecraftLauncherService.ModrinthInstallJob job = minecraftLauncher.getModrinthInstallJob(jobId);
        if (job == null) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", "Job not found");
            ctx.status(404).contentType("application/json").result(json.toString());
            return;
        }

        JSONObject json = new JSONObject();
        json.put("success", true);
        json.put("phase", job.phase().name().toLowerCase());
        json.put("message", job.message() != null ? job.message() : "");

        Instance instance = job.instance();
        if (instance != null) {
            json.put("instance", InstanceHelper.toInstanceJson(instance, minecraftLauncher));
        }

        if (job.phase() == MinecraftLauncherService.ModrinthInstallPhase.DONE
                || job.phase() == MinecraftLauncherService.ModrinthInstallPhase.FAILED) {
            minecraftLauncher.clearModrinthInstallJob(jobId);
        }

        ctx.contentType("application/json").result(json.toString());
    }
}
