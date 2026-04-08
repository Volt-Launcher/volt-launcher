package app.voltlauncher.voltlauncher.rest.routes.impl.auth;


import app.voltlauncher.voltlauncher.auth.AuthFlowStatus;
import app.voltlauncher.voltlauncher.auth.AuthResult;
import app.voltlauncher.voltlauncher.auth.PendingAuth;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/status")
public class GetAuthStatus implements IRoute {
    private MicrosoftAuth msAuth;

    public GetAuthStatus(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
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
    }
}
