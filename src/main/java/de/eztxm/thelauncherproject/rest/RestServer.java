package de.eztxm.thelauncherproject.rest;

import de.eztxm.thelauncherproject.launcher.*;
import de.eztxm.thelauncherproject.launcher.MinecraftLauncherService;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.impl.*;
import de.eztxm.thelauncherproject.rest.routes.impl.auth.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class RestServer {

    private final int port;
    private Javalin app;
    private RouteManager routeManager;
    private final MicrosoftAuth msAuth;
    private final MinecraftLauncherService minecraftLauncher;

    public RestServer(int port) {
        this.port = port;
        try {
            this.msAuth = new MicrosoftAuth();
            this.minecraftLauncher = new MinecraftLauncherService(msAuth.getClientId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize launcher services", e);
        }
        this.routeManager = new RouteManager();

        this.routeManager.register(new Test());

        this.routeManager.register(new GetSession(this.msAuth));
        this.routeManager.register(new GetLogin(this.msAuth));
        this.routeManager.register(new PostLogout(this.msAuth));
        this.routeManager.register(new GetCallback(this.msAuth));
        this.routeManager.register(new GetAuthStatus(this.msAuth));
        this.routeManager.register(new PostAuthSubmit(this.msAuth));

        this.routeManager.register(new GetInstances(this.minecraftLauncher));
        this.routeManager.register(new GetInstancesVersions(this.minecraftLauncher));
        this.routeManager.register(new PostInstances(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceLaunch(this.minecraftLauncher, this.msAuth));
        this.routeManager.register(new PostInstanceStop(this.minecraftLauncher));
    }

    public void start() {
        this.app = this.routeManager.createJavalin(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/dist";
                staticFiles.location = Location.CLASSPATH;
            });

            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));

            config.routes.beforeMatched(ctx -> {
                String path = ctx.path();
                if (!path.startsWith("/api")
                        && !path.startsWith("/callback")
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
                    json.put("uuid", session.getUuid());
                    json.put("username", session.getUsername());
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
        });

        app.post("/api/instances", ctx -> {
            try {
                JSONObject body = new JSONObject(ctx.body());
                String name = body.optString("name", "");
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

        app.get("/api/auth/login", ctx -> {
            try {
                StartAuthResult start = msAuth.startAuthFlow();
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("state", start.state());
                json.put("url", start.url());
                json.put("message",
                        "Browser opened. After authentication you will be redirected back automatically.");
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
                json.put("status", "error");
                json.put("error", "Missing state parameter");
                ctx.status(400).contentType("application/json").result(json.toString());
                return;
            }

            PendingAuth pendingAuth = msAuth.checkAuthStatus(state);
            if (pendingAuth == null) {
                json.put("success", false);
                json.put("status", "expired");
                json.put("error", "Authentication session expired or was already completed");
                ctx.contentType("application/json").result(json.toString());
                return;
            }

            AuthFlowStatus status = pendingAuth.getStatus();
            if (status == AuthFlowStatus.PENDING) {
                json.put("success", true);
                json.put("status", "pending");
                ctx.contentType("application/json").result(json.toString());
                return;
            }

            if (status == AuthFlowStatus.SUCCESS && pendingAuth.getResult() != null) {
                AuthResult result = pendingAuth.getResult();
                json.put("success", true);
                json.put("status", "success");
                json.put("uuid", result.getUuid());
                json.put("username", result.getUsername());
                msAuth.clearAuthState(state);
                ctx.contentType("application/json").result(json.toString());
                return;
            }

            json.put("success", false);
            json.put("status", "error");
            json.put("error", pendingAuth.getErrorMessage() != null
                    ? pendingAuth.getErrorMessage()
                    : "Authentication failed");
            msAuth.clearAuthState(state);
            ctx.contentType("application/json").result(json.toString());
        });

        app.post("/api/auth/submit", ctx -> {
            String code = ctx.formParam("code");
            if (code == null) {
                code = ctx.queryParam("code");
            }

            String state = ctx.formParam("state");
            if (state == null) {
                state = ctx.queryParam("state");
            }

            if (code == null || state == null) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", "Missing code or state");
                ctx.status(400).contentType("application/json").result(json.toString());
                return;
            }

            try {
                AuthResult result = msAuth.handleAuthCode(code, state);
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("uuid", result.getUuid());
                json.put("username", result.getUsername());
                msAuth.clearAuthState(state);
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                logError("Authentication submit failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                msAuth.clearAuthState(state);
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.post("/api/instances/{name}/launch", ctx -> {
            String instanceName = ctx.pathParam("name");
            try {
                LaunchResult result = minecraftLauncher.launchInstance(msAuth.getLaunchSession(), instanceName);
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("instanceName", result.instanceName());
                json.put("version", result.versionId());
                json.put("pid", result.pid());
                json.put("command", result.launchCommand());
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
                logError("Minecraft launch failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.post("/api/instances/{name}/stop", ctx -> {
            String instanceName = ctx.pathParam("name");
            try {
                boolean stopped = minecraftLauncher.stopInstance(instanceName);
                JSONObject json = new JSONObject();
                json.put("success", true);
                json.put("instanceName", instanceName);
                json.put("stopped", stopped);
                ctx.contentType("application/json").result(json.toString());
            } catch (IllegalStateException e) {
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(400).contentType("application/json").result(json.toString());
            } catch (Exception e) {
                logError("Stopping instance failed", e);
                JSONObject json = new JSONObject();
                json.put("success", false);
                json.put("error", e.getMessage());
                ctx.status(500).contentType("application/json").result(json.toString());
            }
        });

        app.get("/callback", ctx -> {
            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");
            String error = ctx.queryParam("error");
            String errorDescription = ctx.queryParam("error_description");

            if (error != null) {
                String msg = errorDescription != null ? errorDescription : error;
                if (state != null && !state.isBlank()) {
                    try {
                        msAuth.failAuthFlow(state, msg);
                    } catch (IllegalStateException ignored) {
                    }
                }
                ctx.status(400).html(buildCallbackPage("Microsoft login failed", msg, false));
                return;
            }

            if (code == null || state == null) {
                ctx.status(400).html(buildCallbackPage(
                        "Microsoft login failed",
                        "Missing code or state parameter. Please retry the login.",
                        false));
                return;
            }

            try {
                AuthResult result = msAuth.handleAuthCode(code, state);
                ctx.html(buildCallbackPage(
                        "Microsoft login successful",
                        "Welcome, " + result.getUsername() + "! You can close this window.",
                        true));
            } catch (IllegalStateException e) {
                ctx.status(400).html(buildCallbackPage(
                        "Microsoft login failed",
                        e.getMessage(),
                        false));
            } catch (Exception e) {
                logError("Authentication callback failed", e);
                ctx.status(500).html(buildCallbackPage(
                        "Microsoft login failed",
                        e.getMessage(),
                        false));
            }
            });
        });
        this.app.start(port);

        System.out.println("Javalin server started on http://localhost:" + port);
        System.out.println("UI available at http://localhost:" + port);
    }

    public void stop() {
        minecraftLauncher.stopAllRunningInstances();
        if (app != null) {
            app.stop();
        }
    }

    public static String buildCallbackPage(String title, String message, boolean success) {
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message != null ? message : "Authentication finished.");
        String accentColor = success ? "#16a34a" : "#dc2626";

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <script>
                        window.addEventListener('load', () => {
                            setTimeout(() => window.close(), 1200);
                        });
                    </script>
                    <style>
                        body {
                            margin: 0;
                            min-height: 100vh;
                            display: grid;
                            place-items: center;
                            background: #111827;
                            color: #f9fafb;
                            font-family: Arial, sans-serif;
                        }
                        main {
                            max-width: 28rem;
                            padding: 2rem;
                            border-radius: 1rem;
                            background: #1f2937;
                            box-shadow: 0 20px 45px rgba(0, 0, 0, 0.35);
                            text-align: center;
                        }
                        h1 {
                            margin-top: 0;
                            color: %s;
                        }
                        p {
                            line-height: 1.5;
                            word-break: break-word;
                        }
                        button {
                            margin-top: 1rem;
                            border: 0;
                            border-radius: 9999px;
                            padding: 0.8rem 1.2rem;
                            font-weight: 700;
                            cursor: pointer;
                            color: white;
                            background: %s;
                        }
                    </style>
                </head>
                <body>
                    <main>
                        <h1>%s</h1>
                        <p>%s</p>
                        <button onclick="window.close()">Close window</button>
                    </main>
                </body>
                </html>
                """.formatted(safeTitle, accentColor, accentColor, safeTitle, safeMessage);
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private JSONObject toInstanceJson(LauncherInstance instance) {
        JSONObject json = new JSONObject();
        json.put("name", instance.name());
        json.put("slug", instance.slug());
        json.put("versionId", instance.versionId());
        json.put("versionType", instance.versionType());
        json.put("createdAt", instance.createdAt());
        json.put("lastPlayedAt", instance.lastPlayedAt());
        json.put("javaMajorVersion", instance.javaMajorVersion());
        json.put("javaComponent", instance.javaComponent());

        RunningInstanceStatus runningStatus = minecraftLauncher.getRunningInstanceStatus(instance.name());
        json.put("running", runningStatus != null && runningStatus.alive());
        if (runningStatus != null) {
            json.put("pid", runningStatus.pid());
            json.put("startedAt", runningStatus.startedAt());
            json.put("javaExecutable", runningStatus.javaExecutable());
            json.put("runningJavaMajorVersion", runningStatus.javaMajorVersion());
        }
        return json;
    }

    private JSONObject toVersionJson(AvailableVersion version) {
        JSONObject json = new JSONObject();
        json.put("id", version.id());
        json.put("type", version.type());
        json.put("releaseTime", version.releaseTime());
        return json;
    }

    private void logError(String message, Exception exception) {
    public static void logError(String message, Exception exception) {
        System.err.println("[RestServer] " + message + ": " + exception.getMessage());
        exception.printStackTrace(System.err);
    }
}
