package de.eztxm.thelauncherproject.rest;

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

    public static void logError(String message, Exception exception) {
        System.err.println("[RestServer] " + message + ": " + exception.getMessage());
        exception.printStackTrace(System.err);
    }
}
