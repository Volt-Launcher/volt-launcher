package app.voltlauncher.voltlauncher.rest.routes.impl.auth;

import app.voltlauncher.voltlauncher.auth.AuthResult;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/session")
public class GetSession implements IRoute {
    private final MicrosoftAuth msAuth;

    public GetSession(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        try {
            AuthResult session = msAuth.getStoredSessionSummary();
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("authenticated", session != null);
            if (session != null) {
                json.put("uuid", session.uuid());
                json.put("username", session.username());
            }
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("authenticated", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}

