package app.voltlauncher.voltlauncher.rest.routes.impl.auth;

import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/callback", method = MethodType.POST)
public class PostAuthCallback implements IRoute {

    private final MicrosoftAuth msAuth;

    public PostAuthCallback(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        JSONObject body = new JSONObject(ctx.body());
        String state = body.optString("state", null);
        String code = body.optString("code", null);
        String error = body.optString("error", null);

        if (error != null && !error.isEmpty() && state != null) {
            try {
                msAuth.failAuthFlow(state, error);
            } catch (Exception ignored) {}
        } else if (code != null && !code.isEmpty() && state != null) {
            Thread.ofVirtual().start(() -> {
                try {
                    msAuth.handleAuthCode(code, state);
                } catch (Exception e) {
                    System.err.println("[RestServer] Auth code exchange failed: " + e.getMessage());
                }
            });
        }

        ctx.json(new JSONObject().put("success", true).toMap());
    }
}

