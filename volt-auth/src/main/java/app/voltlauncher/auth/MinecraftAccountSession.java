package app.voltlauncher.auth;

public record MinecraftAccountSession( String uuid, String username, String minecraftAccessToken, long minecraftAccessTokenExpiresAt, String microsoftAccessToken, long microsoftAccessTokenExpiresAt, String microsoftRefreshToken, String userHash,
String xuid) {

    public boolean hasRefreshToken() {
        return microsoftRefreshToken != null && !microsoftRefreshToken.isBlank();
    }
}

