package app.voltlauncher.voltlauncher.rest.routes.impl.auth;

import app.voltlauncher.voltlauncher.auth.AuthResult;
import app.voltlauncher.voltlauncher.rest.auth.MicrosoftAuth;
import app.voltlauncher.voltlauncher.rest.routes.IRoute;
import app.voltlauncher.voltlauncher.rest.routes.Route;
import io.javalin.http.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

@Route(path = "/api/accounts")
public class GetAccounts implements IRoute {
    private final MicrosoftAuth msAuth;

    public GetAccounts(MicrosoftAuth msAuth) {
        this.msAuth = msAuth;
    }

    @Override
    public void execute(Context ctx) {
        try {
            List<AuthResult> accounts = msAuth.listAllAccounts();
            String selectedUuid = msAuth.getSelectedUuid();

            JSONArray arr = new JSONArray();
            for (AuthResult account : accounts) {
                JSONObject entry = new JSONObject();
                entry.put("uuid", account.uuid());
                entry.put("username", account.username());
                entry.put("selected", account.uuid().equals(selectedUuid));
                arr.put(entry);
            }

            JSONObject json = new JSONObject();
            json.put("success", true);
            json.put("accounts", arr);
            ctx.contentType("application/json").result(json.toString());
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("success", false);
            json.put("error", e.getMessage());
            ctx.status(500).contentType("application/json").result(json.toString());
        }
    }
}
