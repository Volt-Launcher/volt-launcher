package de.eztxm.thelauncherproject.rest.auth;

import com.microsoft.aad.msal4j.AuthorizationCodeParameters;
import com.microsoft.aad.msal4j.AuthorizationRequestUrlParameters;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.Prompt;
import com.microsoft.aad.msal4j.ResponseMode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class MicrosoftAuth {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    private final String clientId = "312b6922-bc5f-4eb8-919b-c5c4cd5d9944";
    private final String redirectUri = "http://localhost:7070/callback";
    private final String authority = "https://login.microsoftonline.com/consumers/";
    private final Set<String> scopes = Set.of("XboxLive.signin", "offline_access");

    private final OkHttpClient httpClient = new OkHttpClient();
    private final PublicClientApplication msalApp;

    public static final class PendingAuth {
        private final String state;
        private final String codeVerifier;
        private boolean completed;

        public PendingAuth(String state, String codeVerifier) {
            this.state = state;
            this.codeVerifier = codeVerifier;
        }

        public String getState() {
            return state;
        }

        public String getCodeVerifier() {
            return codeVerifier;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    public static final class AuthResult {
        private final String uuid;
        private final String username;

        public AuthResult(String uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }

        public String getUuid() {
            return uuid;
        }

        public String getUsername() {
            return username;
        }
    }

    public static final class StartAuthResult {
        private final String state;
        private final String url;

        public StartAuthResult(String state, String url) {
            this.state = state;
            this.url = url;
        }

        public String getState() {
            return state;
        }

        public String getUrl() {
            return url;
        }
    }

    private final ConcurrentHashMap<String, PendingAuth> pendingAuthStates = new ConcurrentHashMap<>();

    public MicrosoftAuth() throws MalformedURLException {
        this.msalApp = PublicClientApplication
                .builder(clientId)
                .authority(authority)
                .build();
    }

    public StartAuthResult startAuthFlow() {
        String state = UUID.randomUUID().toString();
        String codeVerifier = generateCodeVerifier();
        pendingAuthStates.put(state, new PendingAuth(state, codeVerifier));

        String authUrl = buildAuthUrl(state, codeVerifier);
        return new StartAuthResult(state, authUrl);
    }

    private String buildAuthUrl(String state, String codeVerifier) {
        String codeChallenge = codeChallengeFrom(codeVerifier);

        AuthorizationRequestUrlParameters params = AuthorizationRequestUrlParameters
                .builder(redirectUri, scopes)
                .responseMode(ResponseMode.QUERY)
                .prompt(Prompt.SELECT_ACCOUNT)
                .state(state)
                .codeChallenge(codeChallenge)
                .codeChallengeMethod("S256")
                .build();

        return msalApp.getAuthorizationRequestUrl(params).toString();
    }

    public PendingAuth checkAuthStatus(String state) {
        return pendingAuthStates.get(state);
    }

    public AuthResult handleAuthCode(String code, String state) throws Exception {
        PendingAuth pendingAuth = pendingAuthStates.get(state);
        if (pendingAuth == null) {
            throw new IllegalStateException("Invalid state parameter");
        }

        try {
            IAuthenticationResult msToken = acquireMicrosoftToken(code, pendingAuth.getCodeVerifier());

            JSONObject xboxToken = authenticateWithXboxLive(msToken.accessToken());
            String xboxJwt = xboxToken.getString("Token");

            JSONObject xstsToken = getXSTSToken(xboxJwt);
            JSONObject displayClaims = xstsToken.getJSONObject("DisplayClaims");
            JSONArray xui = displayClaims.getJSONArray("xui");
            JSONObject firstXui = xui.getJSONObject(0);
            String uhs = firstXui.getString("uhs");

            JSONObject mcToken = authenticateWithMinecraft(uhs, xstsToken.getString("Token"));
            String accessToken = mcToken.getString("access_token");

            JSONObject profile = getMinecraftProfile(accessToken);
            String uuid = profile.getString("id");
            String username = profile.getString("name");

            AuthResult result = new AuthResult(uuid, username);
            pendingAuth.setCompleted(true);
            pendingAuthStates.remove(state);
            return result;
        } catch (Exception e) {
            pendingAuthStates.remove(state);
            throw e;
        }
    }

    private IAuthenticationResult acquireMicrosoftToken(String code, String codeVerifier)
            throws ExecutionException, InterruptedException {
        AuthorizationCodeParameters params = AuthorizationCodeParameters
                .builder(code, URI.create(redirectUri))
                .scopes(scopes)
                .codeVerifier(codeVerifier)
                .build();

        return msalApp.acquireToken(params).get();
    }

    private JSONObject authenticateWithXboxLive(String accessToken) throws Exception {
        JSONObject props = new JSONObject();
        props.put("AuthMethod", "RPS");
        props.put("SiteName", "user.auth.xboxlive.com");
        props.put("RpsTicket", "d=" + accessToken);

        JSONObject body = new JSONObject();
        body.put("Properties", props);
        body.put("RelyingParty", "http://auth.xboxlive.com");
        body.put("TokenType", "JWT");

        return postJson("https://user.auth.xboxlive.com/user/authenticate", body);
    }

    private JSONObject getXSTSToken(String xboxToken) throws Exception {
        JSONObject props = new JSONObject();
        props.put("SandboxId", "RETAIL");
        props.put("UserTokens", new JSONArray().put(xboxToken));

        JSONObject body = new JSONObject();
        body.put("Properties", props);
        body.put("RelyingParty", "rp://api.minecraftservices.com/");
        body.put("TokenType", "JWT");

        return postJson("https://xsts.auth.xboxlive.com/xsts/authorize", body);
    }

    private JSONObject authenticateWithMinecraft(String userHash, String xstsToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);

        return postJson("https://api.minecraftservices.com/authentication/login_with_xbox", body);
    }

    private JSONObject getMinecraftProfile(String accessToken) throws Exception {
        Request request = new Request.Builder()
                .url("https://api.minecraftservices.com/minecraft/profile")
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String content = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("Failed to get Minecraft profile: " + content);
            }
            return new JSONObject(content);
        }
    }

    private JSONObject postJson(String url, JSONObject body) throws Exception {
        RequestBody requestBody = RequestBody.create(body.toString(), JSON_MEDIA_TYPE);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String content = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + " calling " + url + ": " + content);
            }
            return new JSONObject(content);
        }
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallengeFrom(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create code challenge", e);
        }
    }
}
