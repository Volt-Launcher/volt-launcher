package app.voltlauncher.server.routes;

import app.voltlauncher.game.MinecraftLauncherService;
import app.voltlauncher.game.instance.InstanceContentService;
import app.voltlauncher.server.route.RequestBody;
import app.voltlauncher.server.route.RouteModule;
import app.voltlauncher.server.route.RouteRegistry;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Managing the files already installed in a profile: mods, resource packs, shaders, data packs. */
public final class ContentRoutes implements RouteModule {

    private final MinecraftLauncherService launcher;

    public ContentRoutes(MinecraftLauncherService launcher) {
        this.launcher = launcher;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.get("/api/instances/{name}/content/{type}", this::list);
        routes.post("/api/instances/{name}/content/{type}", this::addFromDisk);
        routes.delete("/api/instances/{name}/content/{type}/{file}", this::remove);
        routes.post("/api/instances/{name}/content/{type}/{file}/toggle", this::toggle);
    }

    private JSONObject list(Context ctx) throws Exception {
        List<InstanceContentService.ContentEntry> items =
                launcher.listContent(ctx.pathParam("name"), type(ctx));

        JSONArray array = new JSONArray();
        items.forEach(item -> array.put(toJson(item)));
        return new JSONObject().put("items", array);
    }

    /**
     * Installs local files that the user dropped onto the window or picked from disk. Failures are
     * reported per file so one unreadable path does not discard the rest of a multi-file drop.
     */
    private JSONObject addFromDisk(Context ctx) throws Exception {
        JSONObject body = RequestBody.json(ctx);
        JSONArray paths = body.optJSONArray("paths");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("Provide at least one file path in 'paths'");
        }

        String name = ctx.pathParam("name");
        InstanceContentService.ContentType type = type(ctx);

        JSONArray added = new JSONArray();
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < paths.length(); i++) {
            String raw = paths.optString(i, "").trim();
            if (raw.isEmpty()) continue;
            try {
                added.put(toJson(launcher.addContent(name, type, Path.of(raw))));
            } catch (Exception e) {
                failures.add(raw + ": " + (e.getMessage() == null ? "could not be added" : e.getMessage()));
            }
        }

        return new JSONObject()
                .put("added", added)
                .put("failures", new JSONArray(failures));
    }

    private JSONObject remove(Context ctx) throws Exception {
        launcher.removeContent(ctx.pathParam("name"), type(ctx), ctx.pathParam("file"));
        return new JSONObject();
    }

    private JSONObject toggle(Context ctx) throws Exception {
        InstanceContentService.ContentEntry entry =
                launcher.toggleContent(ctx.pathParam("name"), type(ctx), ctx.pathParam("file"));
        return new JSONObject().put("item", toJson(entry));
    }

    private InstanceContentService.ContentType type(Context ctx) {
        return InstanceContentService.ContentType.fromId(ctx.pathParam("type"));
    }

    private JSONObject toJson(InstanceContentService.ContentEntry entry) {
        return new JSONObject()
                .put("fileName", entry.fileName())
                .put("size", entry.size())
                .put("enabled", entry.enabled());
    }
}
