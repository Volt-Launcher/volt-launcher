package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
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
            json.put("status", "error");
            json.put("error", "Missing state parameter");
            ctx.status(400).contentType("application/json").result(json.toString());
            return;
        }

        MicrosoftAuth.PendingAuth pendingAuth = msAuth.checkAuthStatus(state);
        if (pendingAuth == null) {
            json.put("success", false);
            json.put("status", "expired");
            json.put("error", "Authentication session expired or was already completed");
            ctx.contentType("application/json").result(json.toString());
            return;
        }

        MicrosoftAuth.AuthFlowStatus status = pendingAuth.getStatus();
        if (status == MicrosoftAuth.AuthFlowStatus.PENDING) {
            json.put("success", true);
            json.put("status", "pending");
            ctx.contentType("application/json").result(json.toString());
            return;
        }

        if (status == MicrosoftAuth.AuthFlowStatus.SUCCESS && pendingAuth.getResult() != null) {
            MicrosoftAuth.AuthResult result = pendingAuth.getResult();
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
    }
}
