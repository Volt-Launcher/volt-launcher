package de.eztxm.thelauncherproject.rest;

import de.eztxm.thelauncherproject.auth.AuthFlowStatus;
import de.eztxm.thelauncherproject.auth.AuthResult;
import de.eztxm.thelauncherproject.auth.MinecraftAccountSession;
import de.eztxm.thelauncherproject.auth.PendingAuth;
import de.eztxm.thelauncherproject.launcher.*;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.json.JSONArray;
import org.json.JSONObject;

public class RestServer {

    @FunctionalInterface
    public interface WindowAction {
        void execute();
    }

    @FunctionalInterface
    public interface OpenUrlAction {
        void open(String url);
    }

    private final int port;
    private Javalin app;
    private final MicrosoftAuth msAuth;
    private final MinecraftLauncherService minecraftLauncher;
    private final WindowAction minimizeWindowAction;
    private final WindowAction maximizeWindowAction;
    private final WindowAction closeWindowAction;
    private final OpenUrlAction openUrlAction;

    public RestServer(int port) {
        this(port, () -> {}, () -> {}, () -> {}, url -> {});
    }

    public RestServer(
            int port,
            WindowAction minimizeWindowAction,
            WindowAction maximizeWindowAction,
            WindowAction closeWindowAction,
            OpenUrlAction openUrlAction) {
        this.port = port;
        this.minimizeWindowAction = minimizeWindowAction;
        this.maximizeWindowAction = maximizeWindowAction;
        this.closeWindowAction = closeWindowAction;
        this.openUrlAction = openUrlAction;
        try {
            this.msAuth = new MicrosoftAuth();
            this.minecraftLauncher = new MinecraftLauncherService(msAuth.getClientId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize launcher services", e);
        }
    }

    public void submitAuthCode(String code, String state) {
        Thread.ofVirtual().start(() -> {
            try {
                msAuth.handleAuthCode(code, state);
            } catch (Exception e) {
                System.err.println("[RestServer] Auth code exchange failed: " + e.getMessage());
            }
        });
    }

    public void failAuth(String state, String message) {
        try { msAuth.failAuthFlow(state, message); } catch (Exception ignored) {}
    }

    public void start() {
        app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/dist";
                staticFiles.location = Location.CLASSPATH;
            });
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            config.router.mount(router -> router.beforeMatched(ctx -> {
                String path = ctx.path();
                if (!path.startsWith("/api")
                        && !path.contains(".")
                        && !"/".equals(path)
                        && !path.startsWith("/assets")) {
                    ctx.redirect("/");
                }
            }));
        }).start(port);

        app.get("/api/test", ctx -> {
            JSONObject json = new JSONObject();
            json.put("message", "Hello from Javalin!");
            ctx.contentType("application/json").result(json.toString());
        });

        app.get("/api/session", ctx -> {
            try {
                AuthResult session = msAuth.getStoredSessionSummary();
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("authenticated", session != null);
                if (session != null) {
                    json.put("uuid", session.uuid());
                    json.put("username", session.username());
                }
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("authenticated", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.get("/api/instances", ctx -> {
            try {
                JSONArray instances = new JSONArray();
                for (LauncherInstance instance : minecraftLauncher.listInstances()) {
                    instances.put(toInstanceJson(instance));
                }
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("instances", instances);
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.get("/api/instances/versions", ctx -> {
            boolean includeSnapshots = ctx.queryParamAsClass("includeSnapshots", Boolean.class).getOrDefault(false);
            boolean includeBetas     = ctx.queryParamAsClass("includeBetas",     Boolean.class).getOrDefault(false);
            boolean includeAlphas    = ctx.queryParamAsClass("includeAlphas",    Boolean.class).getOrDefault(false);
            try {
                JSONArray versions = new JSONArray();
                for (AvailableVersion version : minecraftLauncher.listVersions(includeSnapshots, includeBetas, includeAlphas)) {
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
        });

        app.post("/api/instances", ctx -> {
            try {
                JSONObject body = new JSONObject(ctx.body());
                String name      = body.optString("name", "");
                String versionId = body.optString("versionId", "");
                LauncherInstance instance = minecraftLauncher.createInstance(name, versionId);
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("instance", toInstanceJson(instance));
                ctx.contentType("application/json").result(json.toString());
            } catch (IllegalArgumentException e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(400).contentType("application/json").result(json.toString());
            } catch (IllegalStateException e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(409).contentType("application/json").result(json.toString());
            } catch (Exception e) {
                logError("Creating instance failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.post("/api/instances/{name}/launch", ctx -> {
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
                logError("Minecraft launch failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        // Frontend pollt diesen Endpoint um den Launch-Fortschritt zu verfolgen
        app.get("/api/instances/{name}/launch-status", ctx -> {
            String instanceName = ctx.pathParam("name");
            MinecraftLauncherService.LaunchState state = minecraftLauncher.getLaunchState(instanceName);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("phase", state.phase().name().toLowerCase());
            if (state.message() != null) json.put("message", state.message());
            if (state.result() != null) {
                LaunchResult r = state.result();
                json.put("instanceName",     r.instanceName());
                json.put("version",          r.versionId());
                json.put("pid",              r.pid());
                json.put("logFile",          r.logFile());
                json.put("javaMajorVersion", r.javaMajorVersion());
                json.put("javaExecutable",   r.javaExecutable());
            }
            ctx.contentType("application/json").result(json.toString());
        });

        app.post("/api/instances/{name}/stop", ctx -> {
            String instanceName = ctx.pathParam("name");
            try {
                boolean stopped = minecraftLauncher.stopInstance(instanceName);
                JSONObject json = new JSONObject();
                json.put("success",      true);
                json.put("instanceName", instanceName);
                json.put("stopped",      stopped);
                ctx.contentType("application/json").result(json.toString());
            } catch (IllegalStateException e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error",   e.getMessage());
                ctx.status(400).contentType("application/json").result(json.toString());
            } catch (Exception e) {
                logError("Stopping instance failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error",   e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.get("/api/auth/login", ctx -> {
            try {
                MicrosoftAuth.StartAuthResult start = msAuth.startAuthFlow();
                openUrlAction.open(start.url());
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("state",   start.state());
                json.put("url",     start.url());
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.post("/api/auth/logout", ctx -> {
            try {
                msAuth.logout();
                JSONObject json = new JSONObject();
                json.put("success", true);
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.get("/api/auth/status", ctx -> {
            String state = ctx.queryParam("state");
            JSONObject json = new JSONObject();
            if (state == null || state.isBlank()) {
                json.put("success", false);
                json.put("status",  "error");
                json.put("error",   "Missing state parameter");
                ctx.status(400).contentType("application/json").result(json.toString());
                return;
            }
            PendingAuth pending = msAuth.checkAuthStatus(state);
            if (pending == null) {
                json.put("success", false);
                json.put("status",  "expired");
                json.put("error",   "Authentication session expired or was already completed");
                ctx.contentType("application/json").result(json.toString());
                return;
            }
            AuthFlowStatus status = pending.status();
            if (status == AuthFlowStatus.PENDING) {
                json.put("success", true);
                json.put("status",  "pending");
                ctx.contentType("application/json").result(json.toString());
                return;
            }
            if (status == AuthFlowStatus.SUCCESS && pending.result() != null) {
                AuthResult result = pending.result();
                json.put("success",  true);
                json.put("status",   "success");
                json.put("uuid",     result.uuid());
                json.put("username", result.username());
                msAuth.clearAuthState(state);
                ctx.contentType("application/json").result(json.toString());
                return;
            }
            json.put("success", false);
            json.put("status",  "error");
            json.put("error",   pending.errorMessage() != null ? pending.errorMessage() : "Authentication failed");
            msAuth.clearAuthState(state);
            ctx.contentType("application/json").result(json.toString());
        });

        app.post("/api/window/minimize", ctx -> {
            try { minimizeWindowAction.execute(); ctx.contentType("application/json").result("{\"success\":true}"); }
            catch (Exception e) { ctx.status(500).contentType("application/json").result("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}"); }
        });

        app.post("/api/window/maximize", ctx -> {
            try { maximizeWindowAction.execute(); ctx.contentType("application/json").result("{\"success\":true}"); }
            catch (Exception e) { ctx.status(500).contentType("application/json").result("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}"); }
        });

        app.post("/api/window/close", ctx -> {
            try { closeWindowAction.execute(); ctx.contentType("application/json").result("{\"success\":true}"); }
            catch (Exception e) { ctx.status(500).contentType("application/json").result("{\"success\":false,\"error\":\"" + e.getMessage() + "\"}"); }
        });

        System.out.println("Javalin server started on http://localhost:" + port);
    }

    public void stop() {
        minecraftLauncher.stopAllRunningInstances();
        if (app != null) app.stop();
    }

    private JSONObject toInstanceJson(LauncherInstance instance) {
        JSONObject json = new JSONObject();
        json.put("name",             instance.name());
        json.put("slug",             instance.slug());
        json.put("versionId",        instance.versionId());
        json.put("versionType",      instance.versionType());
        json.put("createdAt",        instance.createdAt());
        json.put("lastPlayedAt",     instance.lastPlayedAt());
        json.put("javaMajorVersion", instance.javaMajorVersion());
        json.put("javaComponent",    instance.javaComponent());

        RunningInstanceStatus running = minecraftLauncher.getRunningInstanceStatus(instance.name());
        MinecraftLauncherService.LaunchState state = minecraftLauncher.getLaunchState(instance.name());
        json.put("running", running != null && running.alive());
        json.put("launchPhase", state.phase().name().toLowerCase());
        if (running != null) {
            json.put("pid",                     running.pid());
            json.put("startedAt",               running.startedAt());
            json.put("javaExecutable",          running.javaExecutable());
            json.put("runningJavaMajorVersion", running.javaMajorVersion());
        }
        return json;
    }

    private JSONObject toVersionJson(AvailableVersion version) {
        JSONObject json = new JSONObject();
        json.put("id",          version.id());
        json.put("type",        version.type());
        json.put("releaseTime", version.releaseTime());
        return json;
    }

    private void logError(String message, Exception e) {
        System.err.println("[RestServer] " + message + ": " + e.getMessage());
        e.printStackTrace(System.err);
    }
}