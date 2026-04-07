package app.voltlauncher.voltlauncher.rest;

import app.voltlauncher.voltlauncher.launcher.*;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.impl.*;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetAuthStatus;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetLogin;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetSession;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.PostLogout;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowClose;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowMaximize;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowMinimize;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
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
    private RouteManager routeManager;
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

        this.routeManager = new RouteManager();
        this.routeManager.register(new Test());

        this.routeManager.register(new GetSession(this.msAuth));
        this.routeManager.register(new GetInstances(this.minecraftLauncher));
        this.routeManager.register(new GetInstancesVersions(this.minecraftLauncher));
        this.routeManager.register(new PostInstances(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceLaunch(this.minecraftLauncher, this.msAuth));
        this.routeManager.register(new GetInstanceLaunchStatus(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceStop(this.minecraftLauncher));
        this.routeManager.register(new GetLogin(this.msAuth, this.openUrlAction));
        this.routeManager.register(new PostLogout(this.msAuth));
        this.routeManager.register(new GetAuthStatus(this.msAuth));
        this.routeManager.register(new PostWindowMinimize(this.minimizeWindowAction));
        this.routeManager.register(new PostWindowMaximize(this.maximizeWindowAction));
        this.routeManager.register(new PostWindowClose(this.closeWindowAction));
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
        app = routeManager.createJavalin(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/dist";
                staticFiles.location = Location.CLASSPATH;
            });
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            config.routes.beforeMatched(ctx -> {
                String path = ctx.path();
                if (!path.startsWith("/api")
                        && !path.contains(".")
                        && !"/".equals(path)
                        && !path.startsWith("/assets")) {
                    ctx.redirect("/");
                }
            });
        }).start(port);

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

    public static JSONObject toVersionJson(AvailableVersion version) {
        JSONObject json = new JSONObject();
        json.put("id",          version.id());
        json.put("type",        version.type());
        json.put("releaseTime", version.releaseTime());
        return json;
    }

    public static void logError(String message, Exception e) {
        System.err.println("[RestServer] " + message + ": " + e.getMessage());
        e.printStackTrace(System.err);
    }
}