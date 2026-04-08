package app.voltlauncher.voltlauncher.rest.routes.impl.auth;


import app.voltlauncher.voltlauncher.rest.RestServer;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONObject;

@Route(path = "/api/auth/login")
public class GetLogin implements IRoute {
    private MicrosoftAuth msAuth;
    private RestServer.OpenUrlAction openUrlAction;

    public GetLogin(MicrosoftAuth msAuth, RestServer.OpenUrlAction openUrlAction) {
        this.msAuth = msAuth;
        this.openUrlAction = openUrlAction;
    }

    @Override
    public void execute(Context ctx) {
        try {
            MicrosoftAuth.StartAuthResult start = msAuth.startAuthFlow();
            openUrlAction.open(start.url());
            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("state",   start.state());
            json.put("url",     start.url());
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
