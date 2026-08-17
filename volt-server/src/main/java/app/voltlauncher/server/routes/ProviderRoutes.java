package app.voltlauncher.server.routes;

import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.providers.ContentProvider;
import app.voltlauncher.providers.ProviderRegistry;
import app.voltlauncher.providers.content.ContentInstallService;
import app.voltlauncher.providers.content.ModpackInstallService;
import app.voltlauncher.providers.content.PackExportService;
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
    private final PackExportService packExporter;

    public ProviderRoutes(ProviderRegistry providers, ContentInstallService contentInstaller,
                          ModpackInstallService modpackInstaller, PackExportService packExporter) {
        this.providers = providers;
        this.contentInstaller = contentInstaller;
        this.modpackInstaller = modpackInstaller;
        this.packExporter = packExporter;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.get("/api/providers", this::listProviders);
        routes.get("/api/providers/{provider}/search", this::search);
        routes.get("/api/providers/{provider}/projects/{projectId}", this::project);
        routes.get("/api/providers/{provider}/projects/{projectId}/versions", this::versions);

        routes.post("/api/instances/{name}/install", this::installIntoInstance);

        routes.post("/api/modpacks/install", this::installModpack);
        routes.post("/api/modpacks/import", this::importModpack);
        routes.get("/api/modpacks/install/{jobId}", this::modpackStatus);
        routes.delete("/api/modpacks/install/{jobId}", this::clearModpackJob);

        routes.get("/api/instances/{name}/modpack", this::modpackStatusForInstance);
        routes.get("/api/instances/{name}/modpack/versions", this::modpackVersions);
        routes.post("/api/instances/{name}/modpack/update", this::updateModpack);

        routes.post("/api/instances/{name}/export", this::exportPack);
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

    /** Installs a {@code .mrpack} or CurseForge {@code .zip} the user picked from disk. */
    private JSONObject importModpack(Context ctx) {
        JSONObject body = RequestBody.json(ctx);
        String jobId = modpackInstaller.importAsync(
                java.nio.file.Path.of(RequestBody.requiredString(body, "path")),
                body.optString("name", ""));
        return new JSONObject().put("jobId", jobId);
    }

    // ── modpack lifecycle for an installed profile ────────────────────────────

    private JSONObject modpackStatusForInstance(Context ctx) throws Exception {
        ModpackInstallService.UpdateCheck check = modpackInstaller.checkUpdate(ctx.pathParam("name"));
        return new JSONObject()
                .put("modpack", check.origin() == null ? JSONObject.NULL : check.origin().toJson())
                .put("updateAvailable", check.updateAvailable())
                .put("latestVersionId", check.latestVersionId())
                .put("latestVersionNumber", check.latestVersionNumber())
                .put("latestReleaseDate", check.latestReleaseDate())
                .put("reason", check.reason());
    }

    /** Every release of the profile's pack, so the user can move to a specific one, not just the newest. */
    private JSONObject modpackVersions(Context ctx) throws Exception {
        ModpackInstallService.UpdateCheck check = modpackInstaller.checkUpdate(ctx.pathParam("name"));
        JSONArray array = new JSONArray();
        if (check.origin() != null && check.origin().isUpdatable()) {
            List<ProjectVersion> versions = providers
                    .requireAvailable(ProviderId.fromId(check.origin().provider()))
                    .versions(check.origin().projectId(), null, null);
            versions.forEach(version -> array.put(version.toJson()));
        }
        return new JSONObject().put("versions", array);
    }

    private JSONObject updateModpack(Context ctx) {
        JSONObject body = RequestBody.json(ctx);
        String jobId = modpackInstaller.updateAsync(ctx.pathParam("name"), body.optString("versionId", ""));
        return new JSONObject().put("jobId", jobId);
    }

    // ── export ────────────────────────────────────────────────────────────────

    private JSONObject exportPack(Context ctx) throws Exception {
        JSONObject body = RequestBody.json(ctx);
        PackExportService.ExportResult result = packExporter.export(
                ctx.pathParam("name"),
                PackExportService.Format.fromId(RequestBody.requiredString(body, "format")),
                body.optString("version", ""));

        return new JSONObject()
                .put("path", result.file().toString())
                .put("fileName", result.file().getFileName().toString())
                .put("referenced", result.referenced())
                .put("bundled", result.bundled())
                .put("notes", new JSONArray(result.notes()));
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
