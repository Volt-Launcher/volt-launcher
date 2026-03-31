package de.eztxm.thelauncherproject.rest.auth;

import de.eztxm.thelauncherproject.auth.MinecraftAccountSession;
import de.eztxm.thelauncherproject.storage.EncryptedAccountStore;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MicrosoftAuth {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final long REFRESH_SAFETY_WINDOW_MS = 5 * 60 * 1000L;

    public enum AuthFlowStatus {
        PENDING,
        SUCCESS,
        ERROR
    }

    public static final class PendingAuth {
        private final String codeVerifier;
        private volatile AuthFlowStatus status = AuthFlowStatus.PENDING;
        private volatile AuthResult result;
        private volatile String errorMessage;

        public PendingAuth(String codeVerifier) {
            this.codeVerifier = codeVerifier;
        }

        public String getCodeVerifier() {
            return codeVerifier;
        }

        public AuthFlowStatus getStatus() {
            return status;
        }

        public AuthResult getResult() {
            return result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isCompleted() {
            return status == AuthFlowStatus.SUCCESS;
        }

        public boolean hasFailed() {
            return status == AuthFlowStatus.ERROR;
        }

        public boolean isTerminal() {
            return status != AuthFlowStatus.PENDING;
        }

        public void complete(AuthResult result) {
            this.result = result;
            this.errorMessage = null;
            this.status = AuthFlowStatus.SUCCESS;
        }

        public void fail(String errorMessage) {
            this.result = null;
            this.errorMessage = errorMessage;
            this.status = AuthFlowStatus.ERROR;
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

    public record StartAuthResult(String state, String url) {
    }

    private record OAuthTokenResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresAt) {
    }

    private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private final String clientId = "312b6922-bc5f-4eb8-919b-c5c4cd5d9944";
    private final String redirectUri = "http://localhost:7070/callback";
    private final String scopeString = "XboxLive.signin offline_access";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final EncryptedAccountStore accountStore;
    private final ConcurrentHashMap<String, PendingAuth> pendingAuthStates = new ConcurrentHashMap<>();

    private MinecraftAccountSession currentSession;

    public MicrosoftAuth() throws Exception {
        this.accountStore = new EncryptedAccountStore();
    }

    public String getClientId() {
        return clientId;
    }

    public StartAuthResult startAuthFlow() {
        String state = UUID.randomUUID().toString();
        String codeVerifier = generateCodeVerifier();
        pendingAuthStates.put(state, new PendingAuth(codeVerifier));
        return new StartAuthResult(state, buildAuthUrl(state, codeVerifier));
    }

    public PendingAuth checkAuthStatus(String state) {
        return pendingAuthStates.get(state);
    }

    public void clearAuthState(String state) {
        pendingAuthStates.remove(state);
    }

    public void failAuthFlow(String state, String errorMessage) {
        PendingAuth pendingAuth = pendingAuthStates.get(state);
        if (pendingAuth == null) {
            throw new IllegalStateException("Invalid state parameter");
        }

        synchronized (pendingAuth) {
            if (!pendingAuth.isTerminal()) {
                pendingAuth.fail(errorMessage);
            }
        }
    }

    public AuthResult handleAuthCode(String code, String state) throws Exception {
        PendingAuth pendingAuth = pendingAuthStates.get(state);
        if (pendingAuth == null) {
            throw new IllegalStateException("Invalid state parameter");
        }

        synchronized (pendingAuth) {
            if (pendingAuth.isCompleted() && pendingAuth.getResult() != null) {
                return pendingAuth.getResult();
            }

            if (pendingAuth.hasFailed()) {
                throw new IllegalStateException(pendingAuth.getErrorMessage());
            }

            try {
                OAuthTokenResponse microsoftTokens = exchangeAuthorizationCode(code, pendingAuth.getCodeVerifier());
                MinecraftAccountSession session = createSessionFromMicrosoftTokens(microsoftTokens);
                saveSession(session);

                AuthResult result = toAuthResult(session);
                pendingAuth.complete(result);
                return result;
            } catch (Exception e) {
                pendingAuth.fail(e.getMessage() != null ? e.getMessage() : "Authentication failed");
                throw e;
            }
        }
    }

    public synchronized AuthResult getStoredSessionSummary() throws Exception {
        MinecraftAccountSession session = loadStoredSession();
        return session == null ? null : toAuthResult(session);
    }

    public synchronized MinecraftAccountSession getLaunchSession() throws Exception {
        MinecraftAccountSession session = loadStoredSession();
        if (session == null) {
            throw new IllegalStateException("Not logged in");
        }

        if (!session.hasRefreshToken()) {
            if (isMinecraftTokenStillUsable(session)) {
                return session;
            }
            throw new IllegalStateException("Stored session cannot be refreshed anymore. Please log in again.");
        }

        if (!needsRefresh(session)) {
            return session;
        }

        try {
            OAuthTokenResponse refreshedTokens = refreshMicrosoftTokens(
                    session.microsoftRefreshToken(),
                    session.microsoftRefreshToken());
            MinecraftAccountSession refreshedSession = createSessionFromMicrosoftTokens(refreshedTokens);
            saveSession(refreshedSession);
            return refreshedSession;
        } catch (Exception refreshError) {
            if (isMinecraftTokenStillUsable(session)) {
                return session;
            }

            if (looksLikeInvalidGrant(refreshError.getMessage())) {
                logout();
            }
            throw refreshError;
        }
    }

    public synchronized void logout() throws Exception {
        currentSession = null;
        accountStore.clear();
    }

    private synchronized MinecraftAccountSession loadStoredSession() throws Exception {
        if (currentSession != null) {
            return currentSession;
        }

        currentSession = accountStore.load();
        return currentSession;
    }

    private synchronized void saveSession(MinecraftAccountSession session) throws Exception {
        accountStore.save(session);
        currentSession = session;
    }

    private String buildAuthUrl(String state, String codeVerifier) {
        String codeChallenge = codeChallengeFrom(codeVerifier);
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("login.microsoftonline.com")
                .addPathSegment("consumers")
                .addPathSegment("oauth2")
                .addPathSegment("v2.0")
                .addPathSegment("authorize")
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("redirect_uri", redirectUri)
                .addQueryParameter("response_mode", "query")
                .addQueryParameter("scope", scopeString)
                .addQueryParameter("prompt", "select_account")
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", codeChallenge)
                .addQueryParameter("code_challenge_method", "S256")
                .build();
        return url.toString();
    }

    private OAuthTokenResponse exchangeAuthorizationCode(String code, String codeVerifier) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("scope", scopeString)
                .add("code_verifier", codeVerifier)
                .build();
        return requestMicrosoftTokens(body, "");
    }

    private OAuthTokenResponse refreshMicrosoftTokens(String refreshToken, String fallbackRefreshToken) throws Exception {
        FormBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("scope", scopeString)
                .build();
        return requestMicrosoftTokens(body, fallbackRefreshToken);
    }

    private OAuthTokenResponse requestMicrosoftTokens(FormBody body, String fallbackRefreshToken) throws Exception {
        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .header("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String content = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(buildMicrosoftTokenError(content, response.code()));
            }

            JSONObject json = new JSONObject(content);
            long expiresInSeconds = json.optLong("expires_in", 3600L);
            String refreshToken = json.optString("refresh_token", fallbackRefreshToken);
            return new OAuthTokenResponse(
                    json.getString("access_token"),
                    refreshToken,
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresInSeconds));
        }
    }

    private String buildMicrosoftTokenError(String responseBody, int statusCode) {
        try {
            JSONObject json = new JSONObject(responseBody);
            String description = json.optString("error_description", "");
            String error = json.optString("error", "Authentication failed");
            if (!description.isBlank()) {
                return description;
            }
            return "HTTP " + statusCode + ": " + error;
        } catch (Exception ignored) {
            return "HTTP " + statusCode + " while requesting Microsoft tokens";
        }
    }

    private MinecraftAccountSession createSessionFromMicrosoftTokens(OAuthTokenResponse microsoftTokens) throws Exception {
        JSONObject xboxToken = authenticateWithXboxLive(microsoftTokens.accessToken());
        String xboxJwt = xboxToken.getString("Token");

        JSONObject xstsToken = getXSTSToken(xboxJwt);
        JSONObject displayClaims = xstsToken.getJSONObject("DisplayClaims");
        JSONArray xui = displayClaims.getJSONArray("xui");
        JSONObject firstXui = xui.getJSONObject(0);
        String userHash = firstXui.getString("uhs");
        String xuid = firstXui.optString("xid", firstXui.optString("xuid", ""));

        JSONObject minecraftToken = authenticateWithMinecraft(userHash, xstsToken.getString("Token"));
        long minecraftExpiresInSeconds = minecraftToken.optLong("expires_in", 86400L);
        String minecraftAccessToken = minecraftToken.getString("access_token");

        JSONObject profile = getMinecraftProfile(minecraftAccessToken);
        return new MinecraftAccountSession(
                profile.getString("id"),
                profile.getString("name"),
                minecraftAccessToken,
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(minecraftExpiresInSeconds),
                microsoftTokens.accessToken(),
                microsoftTokens.accessTokenExpiresAt(),
                microsoftTokens.refreshToken(),
                userHash,
                xuid);
    }

    private boolean needsRefresh(MinecraftAccountSession session) {
        long now = System.currentTimeMillis();
        return session.minecraftAccessTokenExpiresAt() <= now + REFRESH_SAFETY_WINDOW_MS
                || session.microsoftAccessTokenExpiresAt() <= now + REFRESH_SAFETY_WINDOW_MS;
    }

    private boolean isMinecraftTokenStillUsable(MinecraftAccountSession session) {
        return session.minecraftAccessTokenExpiresAt() > System.currentTimeMillis() + 60_000L;
    }

    private boolean looksLikeInvalidGrant(String message) {
        return message != null && message.toLowerCase().contains("invalid_grant");
    }

    private AuthResult toAuthResult(MinecraftAccountSession session) {
        return new AuthResult(session.uuid(), session.username());
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
