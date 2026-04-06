package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.RestServer;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;

@Route(path = "/callback")
public class GetCallback implements IRoute {
    private MicrosoftAuth msAuth;

    public GetCallback(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
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
            ctx.status(400).html(RestServer.buildCallbackPage("Microsoft login failed", msg, false));
            return;
        }

        if (code == null || state == null) {
            ctx.status(400).html(RestServer.buildCallbackPage(
                    "Microsoft login failed",
                    "Missing code or state parameter. Please retry the login.",
                    false));
            return;
        }

        try {
            MicrosoftAuth.AuthResult result = msAuth.handleAuthCode(code, state);
            ctx.html(RestServer.buildCallbackPage(
                    "Microsoft login successful",
                    "Welcome, " + result.getUsername() + "! You can close this window.",
                    true));
        } catch (IllegalStateException e) {
            ctx.status(400).html(RestServer.buildCallbackPage(
                    "Microsoft login failed",
                    e.getMessage(),
                    false));
        } catch (Exception e) {
            RestServer.logError("Authentication callback failed", e);
            ctx.status(500).html(RestServer.buildCallbackPage(
                    "Microsoft login failed",
                    e.getMessage(),
                    false));
        }
    }
}
