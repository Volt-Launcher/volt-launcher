package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/session")
public class GetSession implements IRoute {
    private MicrosoftAuth msAuth;

    public GetSession(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        try {
            MicrosoftAuth.AuthResult session = msAuth.getStoredSessionSummary();
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("authenticated", session != null);
            if (session != null) {
                json.put("uuid", session.getUuid());
                json.put("username", session.getUsername());
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
