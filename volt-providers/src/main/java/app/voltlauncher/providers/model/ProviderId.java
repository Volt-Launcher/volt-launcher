package app.voltlauncher.providers.model;

import java.util.Locale;

public enum ProviderId {

    MODRINTH("modrinth", "Modrinth"),
    CURSEFORGE("curseforge", "CurseForge");

    private final String id;
    private final String displayName;

    ProviderId(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static ProviderId fromId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ProviderId provider : values()) {
            if (provider.id.equals(normalized)) return provider;
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }
}
