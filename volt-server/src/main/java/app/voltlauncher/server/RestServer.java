package app.voltlauncher.server;

import app.voltlauncher.auth.session.MicrosoftAuth;
import app.voltlauncher.core.config.SettingsStore;
import app.voltlauncher.game.MinecraftLauncherService;
import app.voltlauncher.game.java.JavaInstallService;
import app.voltlauncher.providers.ProviderRegistry;
import app.voltlauncher.providers.content.ContentInstallService;
import app.voltlauncher.providers.content.ModpackInstallService;
import app.voltlauncher.providers.curseforge.CurseForgeProvider;
import app.voltlauncher.providers.model.ProviderId;
import app.voltlauncher.server.route.RouteRegistry;
import app.voltlauncher.server.routes.AuthRoutes;
import app.voltlauncher.server.routes.ContentRoutes;
import app.voltlauncher.server.routes.InstanceRoutes;
import app.voltlauncher.server.routes.ProviderRoutes;
import app.voltlauncher.server.routes.SettingsRoutes;
import app.voltlauncher.server.routes.WindowRoutes;
import io.javalin.Javalin;
import org.json.JSONObject;

import java.util.function.Consumer;

/**
 * The launcher's local HTTP API, consumed by the Electron front end.
 *
 * <p>It binds to loopback only, which is what keeps it private: the API can start Minecraft and
 * read account state, so it must not be reachable from other machines on the network.
 */
public final class RestServer {

    private static final String BIND_HOST = "127.0.0.1";
    private static final String LAUNCHER_VERSION = "0.2.0";

    private final int port;
    private final MicrosoftAuth auth;
    private final MinecraftLauncherService launcher;
    private final SettingsStore settings;
    private final ProviderRegistry providers;
    private final ContentInstallService contentInstaller;
    private final ModpackInstallService modpackInstaller;
    private final JavaInstallService javaInstaller;

    private final Runnable minimizeWindow;
    private final Runnable maximizeWindow;
    private final Runnable closeWindow;
    private final Consumer<String> openUrl;

    private Javalin app;

    public RestServer(int port) {
        this(port, () -> {}, () -> {}, () -> {}, url -> {});
    }

    public RestServer(int port, Runnable minimizeWindow, Runnable maximizeWindow,
                      Runnable closeWindow, Consumer<String> openUrl) {
        this.port = port;
        this.minimizeWindow = minimizeWindow;
        this.maximizeWindow = maximizeWindow;
        this.closeWindow = closeWindow;
        this.openUrl = openUrl;

        try {
            this.settings = new SettingsStore();
            this.auth = new MicrosoftAuth();
            this.launcher = new MinecraftLauncherService(auth.getClientId());
            this.providers = new ProviderRegistry(launcher.http(), settings);
            this.contentInstaller = new ContentInstallService(launcher, providers, launcher.http());
            this.modpackInstaller = new ModpackInstallService(
                    launcher, launcher.http(),
                    (CurseForgeProvider) providers.require(ProviderId.CURSEFORGE), settings);
            this.javaInstaller = new JavaInstallService(launcher.javaResolver());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise launcher services", e);
        }
    }

    public void start() {
        app = Javalin.create(config -> {
            // The packaged UI is loaded from file://, whose Origin header is the literal "null",
            // so an origin allow-list cannot express it. Binding to loopback is what actually
            // keeps this API private; CORS only governs which pages a browser lets talk to it.
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));

            new RouteRegistry(config, true)
                    .module(new AuthRoutes(auth, openUrl))
                    .module(new InstanceRoutes(launcher, auth))
                    .module(new ContentRoutes(launcher))
                    .module(new ProviderRoutes(providers, contentInstaller, modpackInstaller))
                    .module(new SettingsRoutes(settings, launcher.javaResolver(), javaInstaller, LAUNCHER_VERSION))
                    .module(new WindowRoutes(minimizeWindow, maximizeWindow, closeWindow))
                    .get("/api/health", ctx -> new JSONObject()
                            .put("version", LAUNCHER_VERSION)
                            .put("status", "ok"));
        });

        app.start(BIND_HOST, port);
        System.out.println("[API] VoltLauncher API listening on http://" + BIND_HOST + ":" + port);
    }

    public void stop() {
        launcher.stopAllRunningInstances();
        if (app != null) app.stop();
    }
}
