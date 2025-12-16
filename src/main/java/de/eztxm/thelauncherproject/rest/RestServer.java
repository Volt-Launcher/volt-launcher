package de.eztxm.thelauncherproject.rest;

import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth.AuthResult;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth.StartAuthResult;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.json.JSONObject;

public class RestServer {

    private final int port;
    private Javalin app;
    private final MicrosoftAuth msAuth;

    public RestServer(int port) {
        this.port = port;
        try {
            this.msAuth = new MicrosoftAuth();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MicrosoftAuth", e);
        }
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
                ctx.contentType("application/json").result(json.toString());
            } catch (Exception e) {
                e.printStackTrace();
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
                ctx.status(400).html("<p>" + msg + "</p>");
                return;
            }

            if (code == null || state == null) {
                ctx.status(400).html(
                        "<p>Missing code or state parameter.</p><p>Please retry the login.</p>");
                return;
            }

            try {
                AuthResult result = msAuth.handleAuthCode(code, state);
                ctx.html("<p>Welcome, " + result.getUsername()
                        + "!</p><p>You can close this window.</p>");
            } catch (IllegalStateException e) {
                ctx.status(400).html("<p>" + e.getMessage() + "</p>");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).html("<p>" + e.getMessage() + "</p>");
            }
        });

        System.out.println("Javalin server started on http://localhost:" + port);
        System.out.println("UI available at http://localhost:" + port);
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }
}
