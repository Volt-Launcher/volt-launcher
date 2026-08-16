package app.voltlauncher.auth.client;

import app.voltlauncher.core.util.HttpFetcher;
import org.json.JSONArray;
import org.json.JSONObject;

public final class XboxAuthClient {

    private static final String XBL_ENDPOINT = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_ENDPOINT = "https://xsts.auth.xboxlive.com/xsts/authorize";

    private final HttpFetcher http;

    public XboxAuthClient(HttpFetcher http) {
        this.http = http;
    }

    public XboxSession authenticate(String microsoftAccessToken) throws Exception {
        String xblToken = getXboxLiveToken(microsoftAccessToken);
        JSONObject xsts = getXstsToken(xblToken);

        JSONObject xui = xsts.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0);
        String userHash = xui.getString("uhs");
        String xuid = xui.optString("xid", xui.optString("xuid", ""));
        String xstsToken = xsts.getString("Token");

        return new XboxSession(xstsToken, userHash, xuid);
    }

    private String getXboxLiveToken(String microsoftAccessToken) throws Exception {
        JSONObject props = new JSONObject();
        props.put("AuthMethod", "RPS");
        props.put("SiteName", "user.auth.xboxlive.com");
        props.put("RpsTicket", "d=" + microsoftAccessToken);

        JSONObject body = new JSONObject();
        body.put("Properties", props);
        body.put("RelyingParty", "http://auth.xboxlive.com");
        body.put("TokenType", "JWT");

        return http.postJson(XBL_ENDPOINT, body).getString("Token");
    }

    private JSONObject getXstsToken(String xblToken) throws Exception {
        JSONObject props = new JSONObject();
        props.put("SandboxId", "RETAIL");
        props.put("UserTokens", new JSONArray().put(xblToken));

        JSONObject body = new JSONObject();
        body.put("Properties", props);
        body.put("RelyingParty", "rp://api.minecraftservices.com/");
        body.put("TokenType", "JWT");

        return http.postJson(XSTS_ENDPOINT, body);
    }

    public record XboxSession(String xstsToken, String userHash, String xuid) {}
}
