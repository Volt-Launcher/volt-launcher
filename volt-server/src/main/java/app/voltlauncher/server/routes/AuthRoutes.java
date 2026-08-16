package app.voltlauncher.server.routes;

import app.voltlauncher.auth.AuthFlowStatus;
import app.voltlauncher.auth.AuthResult;
import app.voltlauncher.auth.PendingAuth;
import app.voltlauncher.auth.session.MicrosoftAuth;
import app.voltlauncher.server.route.RequestBody;
import app.voltlauncher.server.route.RouteModule;
import app.voltlauncher.server.route.RouteRegistry;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

/** Microsoft sign-in and the multi-account switcher. */
public final class AuthRoutes implements RouteModule {

    private final MicrosoftAuth auth;
    private final Consumer<String> openUrl;

    public AuthRoutes(MicrosoftAuth auth, Consumer<String> openUrl) {
        this.auth = auth;
        this.openUrl = openUrl;
    }

    @Override
    public void register(RouteRegistry routes) {
        routes.get("/api/session", this::session);
        routes.get("/api/auth/login", this::login);
        routes.get("/api/auth/status", this::status);
        routes.post("/api/auth/callback", this::callback);
        routes.post("/api/auth/logout", this::logout);

        routes.get("/api/accounts", this::listAccounts);
        routes.post("/api/accounts/{uuid}/select", this::selectAccount);
        routes.delete("/api/accounts/{uuid}", this::removeAccount);
    }

    private JSONObject session(Context ctx) throws Exception {
        AuthResult session = auth.getStoredSessionSummary();
        JSONObject json = new JSONObject().put("authenticated", session != null);
        if (session != null) {
            json.put("uuid", session.uuid()).put("username", session.username());
        }
        return json;
    }

    private JSONObject login(Context ctx) {
        MicrosoftAuth.StartAuthResult start = auth.startAuthFlow();
        openUrl.accept(start.url());
        return new JSONObject().put("state", start.state()).put("url", start.url());
    }

    private JSONObject status(Context ctx) {
        String state = RequestBody.requiredQuery(ctx, "state");
        PendingAuth pending = auth.checkAuthStatus(state);

        if (pending == null) {
            // The flow already completed and was cleared, or the launcher restarted mid-login.
            return new JSONObject()
                    .put("status", "expired")
                    .put("error", "The sign-in attempt expired or was already completed");
        }

        AuthFlowStatus flowStatus = pending.status();
        if (flowStatus == AuthFlowStatus.PENDING) {
            return new JSONObject().put("status", "pending");
        }

        auth.clearAuthState(state);
        if (flowStatus == AuthFlowStatus.SUCCESS && pending.result() != null) {
            return new JSONObject()
                    .put("status", "success")
                    .put("uuid", pending.result().uuid())
                    .put("username", pending.result().username());
        }
        return new JSONObject()
                .put("status", "error")
                .put("error", pending.errorMessage() == null ? "Sign-in failed" : pending.errorMessage());
    }

    /**
     * Receives the OAuth redirect forwarded by the Electron login window. The token exchange runs
     * off-thread so the browser window can close immediately; the UI polls {@code /api/auth/status}.
     */
    private JSONObject callback(Context ctx) {
        JSONObject body = RequestBody.json(ctx);
        String state = body.optString("state", "");
        String code = body.optString("code", "");
        String error = body.optString("error", "");

        if (state.isBlank()) {
            throw new IllegalArgumentException("Missing OAuth state");
        }
        if (!error.isBlank()) {
            auth.failAuthFlow(state, error);
            return new JSONObject();
        }
        if (code.isBlank()) {
            auth.failAuthFlow(state, "Microsoft did not return an authorization code");
            return new JSONObject();
        }

        Thread.ofVirtual().name("auth-exchange").start(() -> {
            try {
                auth.handleAuthCode(code, state);
            } catch (Exception e) {
                System.err.println("[Auth] Token exchange failed: " + e.getMessage());
            }
        });
        return new JSONObject();
    }

    private JSONObject logout(Context ctx) throws Exception {
        auth.logout();
        return new JSONObject();
    }

    private JSONObject listAccounts(Context ctx) throws Exception {
        String selectedUuid = auth.getSelectedUuid();
        JSONArray array = new JSONArray();
        for (AuthResult account : auth.listAllAccounts()) {
            array.put(new JSONObject()
                    .put("uuid", account.uuid())
                    .put("username", account.username())
                    .put("selected", account.uuid().equals(selectedUuid)));
        }
        return new JSONObject().put("accounts", array);
    }

    private JSONObject selectAccount(Context ctx) throws Exception {
        try {
            auth.switchAccount(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            throw new NoSuchElementException(e.getMessage());
        }
        return new JSONObject();
    }

    private JSONObject removeAccount(Context ctx) throws Exception {
        try {
            auth.removeAccount(ctx.pathParam("uuid"));
        } catch (IllegalArgumentException e) {
            throw new NoSuchElementException(e.getMessage());
        }
        return new JSONObject();
    }
}
