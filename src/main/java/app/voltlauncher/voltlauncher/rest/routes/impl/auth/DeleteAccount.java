package app.voltlauncher.voltlauncher.rest.routes.impl.auth;

import app.voltlauncher.voltlauncher.rest.MethodType;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/accounts/{uuid}", method = MethodType.DELETE)
public class DeleteAccount implements IRoute {
    private final MicrosoftAuth msAuth;

    public DeleteAccount(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        String uuid = ctx.pathParam("uuid");
        try {
            msAuth.removeAccount(uuid);
            JSONObject json = new JSONObject();
            json.put("success", true);
            ctx.contentType("application/json").result(json.toString());
        } catch (IllegalArgumentException e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(404).contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
