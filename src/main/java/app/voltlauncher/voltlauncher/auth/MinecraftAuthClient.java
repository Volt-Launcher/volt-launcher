package app.voltlauncher.voltlauncher.auth;

import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MinecraftAuthClient {

    private static final String MC_LOGIN_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpFetcher http;

    public MinecraftAuthClient(HttpFetcher http) {
        this.http = http;
    }

    public MinecraftTokenResponse login(String xstsToken, String userHash) throws Exception {
        JSONObject body = new JSONObject();
        body.put("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);

        JSONObject response = http.postJson(MC_LOGIN_ENDPOINT, body);
        long expiresIn = response.optLong("expires_in", 86_400L);

        return new MinecraftTokenResponse( response.getString("access_token"), System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn));
    }

    public MinecraftProfile fetchProfile(String minecraftAccessToken) throws Exception {
        JSONObject json = http.getJson( MC_PROFILE_ENDPOINT, Map.of("Authorization", "Bearer " + minecraftAccessToken));

        return new MinecraftProfile(json.getString("id"), json.getString("name"));
    }

    public record MinecraftTokenResponse(String accessToken, long expiresAt) {}

    public record MinecraftProfile(String uuid, String username) {}
}
