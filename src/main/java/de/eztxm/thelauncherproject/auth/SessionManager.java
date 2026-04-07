package de.eztxm.thelauncherproject.auth;

import de.eztxm.thelauncherproject.storage.EncryptedAccountStore;

public final class SessionManager {

    private static final long REFRESH_SAFETY_WINDOW_MS = 5 * 60 * 1_000L;
    private static final long MIN_USABLE_REMAINING_MS  = 60_000L;

    private final OAuthClient         oauth;
    private final XboxAuthClient      xbox;
    private final MinecraftAuthClient minecraft;
    private final EncryptedAccountStore store;

    private volatile MinecraftAccountSession cachedSession;

    public SessionManager(
            OAuthClient oauth,
            XboxAuthClient xbox,
            MinecraftAuthClient minecraft,
            EncryptedAccountStore store) {
        this.oauth     = oauth;
        this.xbox      = xbox;
        this.minecraft = minecraft;
        this.store     = store;
    }

    public MinecraftAccountSession createSession(OAuthClient.TokenResponse microsoftTokens) throws Exception {
        XboxAuthClient.XboxSession xboxSession = xbox.authenticate(microsoftTokens.accessToken());
        MinecraftAuthClient.MinecraftTokenResponse mc = minecraft.login(xboxSession.xstsToken(), xboxSession.userHash());
        MinecraftAuthClient.MinecraftProfile profile  = minecraft.fetchProfile(mc.accessToken());

        MinecraftAccountSession session = new MinecraftAccountSession(
                profile.uuid(),
                profile.username(),
                mc.accessToken(),
                mc.expiresAt(),
                microsoftTokens.accessToken(),
                microsoftTokens.accessTokenExpiresAt(),
                microsoftTokens.refreshToken(),
                xboxSession.userHash(),
                xboxSession.xuid());

        persist(session);
        return session;
    }

    public synchronized MinecraftAccountSession getLaunchSession() throws Exception {
        MinecraftAccountSession session = loadCached();
        if (session == null) {
            throw new IllegalStateException("Not logged in");
        }
        if (!needsRefresh(session)) {
            return session;
        }
        return refresh(session);
    }

    public synchronized AuthResult getStoredSummary() throws Exception {
        MinecraftAccountSession session = loadCached();
        return session == null ? null : toAuthResult(session);
    }

    public synchronized void logout() throws Exception {
        cachedSession = null;
        store.clear();
    }

    private MinecraftAccountSession refresh(MinecraftAccountSession session) throws Exception {
        if (!session.hasRefreshToken()) {
            if (isMinecraftTokenStillUsable(session)) {
                return session;
            }
            throw new IllegalStateException("Session expired and cannot be refreshed. Please log in again.");
        }
        try {
            return createSession(oauth.refresh(session.microsoftRefreshToken()));
        } catch (Exception e) {
            if (isInvalidGrant(e.getMessage())) {
                logout();
                throw new IllegalStateException("Session revoked. Please log in again.", e);
            }
            if (isMinecraftTokenStillUsable(session)) {
                return session;
            }
            throw e;
        }
    }

    private synchronized MinecraftAccountSession loadCached() throws Exception {
        if (cachedSession != null) {
            return cachedSession;
        }
        cachedSession = store.load();
        return cachedSession;
    }

    private synchronized void persist(MinecraftAccountSession session) throws Exception {
        store.save(session);
        cachedSession = session;
    }

    private boolean needsRefresh(MinecraftAccountSession session) {
        long now = System.currentTimeMillis();
        return session.minecraftAccessTokenExpiresAt()  <= now + REFRESH_SAFETY_WINDOW_MS
                || session.microsoftAccessTokenExpiresAt() <= now + REFRESH_SAFETY_WINDOW_MS;
    }

    private boolean isMinecraftTokenStillUsable(MinecraftAccountSession session) {
        return session.minecraftAccessTokenExpiresAt() > System.currentTimeMillis() + MIN_USABLE_REMAINING_MS;
    }

    private boolean isInvalidGrant(String message) {
        return message != null && message.toLowerCase().contains("invalid_grant");
    }

    private AuthResult toAuthResult(MinecraftAccountSession session) {
        return new AuthResult(session.uuid(), session.username());
    }
}