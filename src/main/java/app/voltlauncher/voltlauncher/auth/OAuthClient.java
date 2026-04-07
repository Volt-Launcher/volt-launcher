package app.voltlauncher.voltlauncher.auth;

import app.voltlauncher.voltlauncher.util.HttpFetcher;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class OAuthClient {

    private static final String AUTHORIZE_ENDPOINT = "https://login.live.com/oauth20_authorize.srf";
    private static final String TOKEN_ENDPOINT     = "https://login.live.com/oauth20_token.srf";
    // Einzige erlaubte Redirect-URI für den Xbox Live Public Client
    public  static final String REDIRECT_URI       = "https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE              = "XboxLive.signin offline_access";

    private final String clientId;
    private final HttpFetcher http;

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresAt) {}

    public OAuthClient(String clientId, HttpFetcher http) {
        this.clientId = clientId;
        this.http     = http;
    }

    public String buildAuthUrl(String state, String codeVerifier) {
        return AUTHORIZE_ENDPOINT + "?" + String.join("&",
                param("client_id",             clientId),
                param("response_type",         "code"),
                param("redirect_uri",          REDIRECT_URI),
                param("scope",                 SCOPE),
                param("prompt",                "select_account"),
                param("state",                 state),
                param("code_challenge",        codeChallenge(codeVerifier)),
                param("code_challenge_method", "S256"));
    }

    public TokenResponse exchangeCode(String code, String codeVerifier) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_id",     clientId);
        fields.put("grant_type",    "authorization_code");
        fields.put("code",          code);
        fields.put("redirect_uri",  REDIRECT_URI);
        fields.put("scope",         SCOPE);
        fields.put("code_verifier", codeVerifier);
        return parseTokenResponse(http.postForm(TOKEN_ENDPOINT, fields), null);
    }

    public TokenResponse refresh(String refreshToken) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("client_id",     clientId);
        fields.put("grant_type",    "refresh_token");
        fields.put("refresh_token", refreshToken);
        fields.put("scope",         SCOPE);
        return parseTokenResponse(http.postForm(TOKEN_ENDPOINT, fields), refreshToken);
    }

    private TokenResponse parseTokenResponse(JSONObject json, String fallbackRefresh) {
        long expiresIn    = json.optLong("expires_in", 3600L);
        String newRefresh = json.optString("refresh_token", "");
        String stored     = !newRefresh.isBlank() ? newRefresh
                : (fallbackRefresh != null ? fallbackRefresh : "");
        return new TokenResponse(
                json.getString("access_token"),
                stored,
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn));
    }

    public static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String param(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}