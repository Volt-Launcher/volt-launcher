package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/login")
public class GetLogin implements IRoute {
    private MicrosoftAuth msAuth;

    public GetLogin(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        try {
            MicrosoftAuth.StartAuthResult start = msAuth.startAuthFlow();
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
    }
}
