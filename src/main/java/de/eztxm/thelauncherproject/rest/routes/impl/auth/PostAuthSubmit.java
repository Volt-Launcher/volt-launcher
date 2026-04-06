package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.MethodType;
import de.eztxm.thelauncherproject.rest.RestServer;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/submit", method = MethodType.POST)
public class PostAuthSubmit implements IRoute {
    private MicrosoftAuth msAuth;

    public PostAuthSubmit(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
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
            MicrosoftAuth.AuthResult result = msAuth.handleAuthCode(code, state);
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("uuid", result.getUuid());
            json.put("username", result.getUsername());
            msAuth.clearAuthState(state);
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            RestServer.logError("Authentication submit failed", e);
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            msAuth.clearAuthState(state);
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
