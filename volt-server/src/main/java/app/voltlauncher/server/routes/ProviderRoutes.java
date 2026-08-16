package app.voltlauncher.server.routes;

import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.providers.ContentProvider;
import app.voltlauncher.providers.ProviderRegistry;
import app.voltlauncher.providers.content.ContentInstallService;
import app.voltlauncher.providers.content.ModpackInstallService;
import app.voltlauncher.providers.model.ContentKind;
import app.voltlauncher.providers.model.ProjectVersion;
import app.voltlauncher.providers.model.ProviderId;
import app.voltlauncher.providers.model.SearchQuery;
import app.voltlauncher.server.route.RequestBody;
import app.voltlauncher.server.route.RouteModule;
import app.voltlauncher.server.route.RouteRegistry;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Discovery and installation across content providers. Every route is provider-parameterised, so
 * Modrinth and CurseForge are reached through exactly the same surface.
 */
public final class ProviderRoutes implements RouteModule {

    private final ProviderRegistry providers;
    private final ContentInstallService contentInstaller;
    private final ModpackInstallService modpackInstaller;

    public ProviderRoutes(ProviderRegistry providers, ContentInstallService contentInstaller,
                          ModpackInstallService modpackInstaller) {
        this.providers = providers;
        this.contentInstaller = contentInstaller;
        this.modpackInstaller = modpackInstaller;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.get("/api/providers", this::listProviders);
        routes.get("/api/providers/{provider}/search", this::search);
        routes.get("/api/providers/{provider}/projects/{projectId}", this::project);
        routes.get("/api/providers/{provider}/projects/{projectId}/versions", this::versions);

        routes.post("/api/instances/{name}/install", this::installIntoInstance);

        routes.post("/api/modpacks/install", this::installModpack);
        routes.get("/api/modpacks/install/{jobId}", this::modpackStatus);
        routes.delete("/api/modpacks/install/{jobId}", this::clearModpackJob);
    }

    // ── discovery ─────────────────────────────────────────────────────────────

    private JSONObject listProviders(Context ctx) {
        JSONArray array = new JSONArray();
        for (ContentProvider provider : providers.all()) {
            JSONArray kinds = new JSONArray();
            provider.supportedKinds().forEach(kind -> kinds.put(kind.id()));
            array.put(new JSONObject()
                    .put("id", provider.id().id())
                    .put("displayName", provider.id().displayName())
                    .put("available", provider.isAvailable())
                    .put("unavailableReason", provider.unavailableReason())
                    .put("kinds", kinds));
        }
        return new JSONObject().put("providers", array);
    }

    private JSONObject search(Context ctx) throws Exception {
        ContentProvider provider = providers.requireAvailable(providerOf(ctx));
        SearchQuery query = new SearchQuery(
                RequestBody.optionalQuery(ctx, "query"),
                ContentKind.fromId(ctx.queryParam("kind") == null ? "mod" : ctx.queryParam("kind")),
                RequestBody.optionalQuery(ctx, "gameVersion"),
                RequestBody.optionalQuery(ctx, "loader"),
                categories(ctx),
                RequestBody.optionalQuery(ctx, "sort"),
                RequestBody.queryInt(ctx, "offset", 0),
                RequestBody.queryInt(ctx, "limit", SearchQuery.DEFAULT_LIMIT));
        return provider.search(query).toJson();
    }

    private JSONObject project(Context ctx) throws Exception {
        ContentProvider provider = providers.requireAvailable(providerOf(ctx));
        return new JSONObject().put("project", provider.project(ctx.pathParam("projectId")).toJson());
    }

    private JSONObject versions(Context ctx) throws Exception {
        ContentProvider provider = providers.requireAvailable(providerOf(ctx));
        List<ProjectVersion> versions = provider.versions(
                ctx.pathParam("projectId"),
                RequestBody.optionalQuery(ctx, "gameVersion"),
                RequestBody.optionalQuery(ctx, "loader"));

        JSONArray array = new JSONArray();
        versions.forEach(version -> array.put(version.toJson()));
        return new JSONObject().put("versions", array);
    }

    // ── installation ──────────────────────────────────────────────────────────

    private JSONObject installIntoInstance(Context ctx) throws Exception {
        JSONObject body = RequestBody.json(ctx);
        ContentInstallService.InstallReport report = contentInstaller.install(
                ctx.pathParam("name"),
                ProviderId.fromId(RequestBody.requiredString(body, "provider")),
                RequestBody.requiredString(body, "versionId"),
                ContentKind.fromId(RequestBody.requiredString(body, "kind")),
                body.optBoolean("withDependencies", true));

        JSONArray installed = new JSONArray();
        report.installed().forEach(file -> installed.put(new JSONObject()
                .put("projectId", file.projectId())
                .put("versionId", file.versionId())
                .put("fileName", file.fileName())
                .put("dependency", file.dependency())));

        return new JSONObject()
                .put("installed", installed)
                .put("skipped", new JSONArray(report.skipped()));
    }

    private JSONObject installModpack(Context ctx) {
        JSONObject body = RequestBody.json(ctx);
        String jobId = modpackInstaller.installAsync(
                ProviderId.fromId(RequestBody.requiredString(body, "provider")),
                RequestBody.requiredString(body, "versionId"),
                body.optString("name", ""));
        return new JSONObject().put("jobId", jobId);
    }

    private JSONObject modpackStatus(Context ctx) {
        ModpackInstallService.Job job = modpackInstaller.job(ctx.pathParam("jobId"));
        if (job == null) {
            throw new java.util.NoSuchElementException("Unknown installation job");
        }

        JSONObject json = new JSONObject()
                .put("phase", job.phase().name().toLowerCase(Locale.ROOT))
                .put("stage", job.stage() == null ? JSONObject.NULL : job.stage())
                .put("completed", job.completed())
                .put("total", job.total())
                .put("percent", job.percent())
                .put("failureReason", job.error() == null ? JSONObject.NULL : job.error());

        Instance instance = job.instance();
        if (instance != null) {
            json.put("instance", new JSONObject()
                    .put("name", instance.name())
                    .put("slug", instance.slug())
                    .put("versionId", instance.versionId())
                    .put("versionType", instance.versionType()));
        }
        return json;
    }

    private JSONObject clearModpackJob(Context ctx) {
        modpackInstaller.clearJob(ctx.pathParam("jobId"));
        return new JSONObject();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProviderId providerOf(Context ctx) {
        return ProviderId.fromId(ctx.pathParam("provider"));
    }

    private List<String> categories(Context ctx) {
        String raw = RequestBody.optionalQuery(ctx, "categories");
        if (raw == null) return List.of();
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) values.add(trimmed);
        }
        return values;
    }
}
