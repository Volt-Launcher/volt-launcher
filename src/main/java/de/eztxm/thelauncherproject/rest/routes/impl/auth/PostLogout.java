package de.eztxm.thelauncherproject.rest.routes.impl.auth;

import de.eztxm.thelauncherproject.rest.MethodType;
import de.eztxm.thelauncherproject.rest.auth.MicrosoftAuth;
import de.eztxm.thelauncherproject.rest.routes.IRoute;
import de.eztxm.thelauncherproject.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/logout", method = MethodType.POST)
public class PostLogout implements IRoute {
    private MicrosoftAuth msAuth;

    public PostLogout(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        try {
            msAuth.logout();
            JSONObject json = new JSONObject();
            json.put("success", true);
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
