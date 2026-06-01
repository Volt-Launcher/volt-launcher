package app.voltlauncher.voltlauncher.rest;

import app.voltlauncher.voltlauncher.launcher.MinecraftLauncherService;
import app.voltlauncher.voltlauncher.launcher.instance.Instance;
import app.voltlauncher.voltlauncher.launcher.instance.RunningInstanceStatus;
import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.impl.*;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetAuthStatus;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetLogin;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.GetSession;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.PostAuthCallback;
import app.voltlauncher.voltlauncher.rest.routes.impl.auth.PostLogout;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowClose;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowMaximize;
import app.voltlauncher.voltlauncher.rest.routes.impl.window.PostWindowMinimize;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.json.JSONObject;

public class RestServer {

    private final int port;
    private final MicrosoftAuth msAuth;
    private final MinecraftLauncherService minecraftLauncher;
    private final WindowAction minimizeWindowAction;
    private final WindowAction maximizeWindowAction;
    private final WindowAction closeWindowAction;
    private final OpenUrlAction openUrlAction;
    private Javalin app;
    private final RouteManager routeManager;
    public RestServer(int port) {
        this(port, () -> {}, () -> {}, () -> {}, url -> {});
    }
    public RestServer( int port, WindowAction minimizeWindowAction, WindowAction maximizeWindowAction, WindowAction closeWindowAction,
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
        this.routeManager.register(new GetInstanceLoaderVersions(this.minecraftLauncher));
        this.routeManager.register(new PostInstances(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceLaunch(this.minecraftLauncher, this.msAuth));
        this.routeManager.register(new GetInstanceLaunchStatus(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceStop(this.minecraftLauncher));
        this.routeManager.register(new DeleteInstance(this.minecraftLauncher));
        this.routeManager.register(new PatchInstance(this.minecraftLauncher));
        this.routeManager.register(new PostInstanceOpenFolder(this.minecraftLauncher));
        this.routeManager.register(new GetLogin(this.msAuth, this.openUrlAction));
        this.routeManager.register(new PostAuthCallback(this.msAuth));
        this.routeManager.register(new PostLogout(this.msAuth));
        this.routeManager.register(new GetAuthStatus(this.msAuth));
        this.routeManager.register(new PostWindowMinimize(this.minimizeWindowAction));
        this.routeManager.register(new PostWindowMaximize(this.maximizeWindowAction));
        this.routeManager.register(new PostWindowClose(this.closeWindowAction));
    }

    public static JSONObject toVersionJson(AvailableVersion version) {
        JSONObject json = new JSONObject();
        json.put("id", version.id());
        json.put("type", version.type());
        json.put("releaseTime", version.releaseTime());
        return json;
    }

    public static void logError(String message, Exception e) {
        System.err.println("[RestServer] " + message + ": " + e.getMessage());
        e.printStackTrace(System.err);
    }

    public void start() {
        app = routeManager.createJavalin(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
            // We no longer serve the UI from Javalin since Electron loads it directly.
        });

        app.start(port);

        System.out.println("Javalin server started on http://localhost:" + port);
    }

    public void stop() {
        minecraftLauncher.stopAllRunningInstances();
        if (app != null) { app.stop(); }
    }

    private JSONObject toInstanceJson(Instance instance) {
        JSONObject json = new JSONObject();
        json.put("name", instance.name());
        json.put("slug", instance.slug());
        json.put("versionId", instance.versionId());
        json.put("versionType", instance.versionType());
        json.put("createdAt", instance.createdAt());
        json.put("lastPlayedAt", instance.lastPlayedAt());
        json.put("javaMajorVersion", instance.javaMajorVersion());
        json.put("javaComponent", instance.javaComponent());

        RunningInstanceStatus running = minecraftLauncher.getRunningInstanceStatus(instance.name());
        MinecraftLauncherService.LaunchState state = minecraftLauncher.getLaunchState(instance.name());
        json.put("running", running != null && running.alive());
        json.put("launchPhase", state.phase().name().toLowerCase());
        if (running != null) {
            json.put("pid", running.pid());
            json.put("startedAt", running.startedAt());
            json.put("javaExecutable", running.javaExecutable());
            json.put("runningJavaMajorVersion", running.javaMajorVersion());
        }
        return json;
    }

    @FunctionalInterface
    public interface WindowAction {
        void execute();
    }

    @FunctionalInterface
    public interface OpenUrlAction {
        void open(String url);
    }
}
