package app.voltlauncher.voltlauncher.rest.auth;

import app.voltlauncher.voltlauncher.auth.*;
import app.voltlauncher.voltlauncher.storage.EncryptedAccountStore;
import app.voltlauncher.voltlauncher.util.HttpFetcher;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MicrosoftAuth {

    private static final String CLIENT_ID = "00000000402b5328";
    private final OAuthClient oauthClient;
    private final SessionManager sessionManager;
    private final ConcurrentHashMap < String, PendingAuth> pendingStates = new ConcurrentHashMap <> ();

    public MicrosoftAuth() throws Exception {
        HttpFetcher http = new HttpFetcher();
        this.oauthClient = new OAuthClient(CLIENT_ID, http);
        this.sessionManager = new SessionManager( oauthClient, new XboxAuthClient(http), new MinecraftAuthClient(http), new EncryptedAccountStore());
    }

    public String getClientId() {
        return CLIENT_ID;
    }

    public StartAuthResult startAuthFlow() {
        String state = UUID.randomUUID().toString();
        String codeVerifier = OAuthClient.generateCodeVerifier();
        pendingStates.put(state, new PendingAuth(codeVerifier));
        return new StartAuthResult(state, oauthClient.buildAuthUrl(state, codeVerifier));
    }

    public PendingAuth checkAuthStatus(String state) {
        return pendingStates.get(state);
    }

    public void clearAuthState(String state) {
        pendingStates.remove(state);
    }

    public void failAuthFlow(String state, String message) {
        PendingAuth pending = pendingStates.get(state);
        if (pending == null) { throw new IllegalStateException("Invalid state: " + state); }
        pending.fail(message);
    }

    public AuthResult handleAuthCode(String code, String state) throws Exception {
        PendingAuth pending = pendingStates.get(state);
        if (pending == null) { throw new IllegalStateException("Invalid state: " + state); }
        synchronized (pending) {
            if (pending.isCompleted()) return pending.result();
            if (pending.hasFailed()) { throw new IllegalStateException(pending.errorMessage()); }
            try {
                OAuthClient.TokenResponse tokens = oauthClient.exchangeCode(code, pending.codeVerifier());
                MinecraftAccountSession session = sessionManager.createSession(tokens);
                AuthResult result = new AuthResult(session.uuid(), session.username());
                pending.complete(result);
                return result;
            } catch (Exception e) {
                pending.fail(e.getMessage() != null ? e.getMessage() : "Authentication failed");
                throw e;
            }
        }
    }

    public AuthResult getStoredSessionSummary() throws Exception {
        return sessionManager.getStoredSummary();
    }

    public MinecraftAccountSession getLaunchSession() throws Exception {
        return sessionManager.getLaunchSession();
    }

    public void logout() throws Exception {
        sessionManager.logout();
    }

    public record StartAuthResult(String state, String url) {}
}
