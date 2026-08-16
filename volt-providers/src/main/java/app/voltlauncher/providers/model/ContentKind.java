package app.voltlauncher.providers.model;

import app.voltlauncher.game.instance.InstanceContentService;

import java.util.Locale;

/**
 * The kinds of content the launcher can discover and install. Each maps onto the instance
 * sub-directory the game reads it from, except modpacks, which create a whole instance.
 */
public enum ContentKind {

    MOD("mod", InstanceContentService.ContentType.MODS),
    MODPACK("modpack", null),
    RESOURCEPACK("resourcepack", InstanceContentService.ContentType.RESOURCEPACKS),
    SHADER("shader", InstanceContentService.ContentType.SHADERPACKS),
    DATAPACK("datapack", InstanceContentService.ContentType.DATAPACKS);

    private final String id;
    private final InstanceContentService.ContentType contentType;

    ContentKind(String id, InstanceContentService.ContentType contentType) {
        this.id = id;
        this.contentType = contentType;
    }

    public String id() {
        return id;
    }

    /** The instance folder this kind installs into, or {@code null} for modpacks. */
    public InstanceContentService.ContentType contentType() {
        return contentType;
    }

    public boolean installsIntoInstance() {
        return contentType != null;
    }

    public static ContentKind fromId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Content kind is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        for (ContentKind kind : values()) {
            if (kind.id.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        // Tolerate the plural forms the UI uses for its tabs.
        return switch (normalized) {
            case "mods" -> MOD;
            case "modpacks" -> MODPACK;
            case "resourcepacks" -> RESOURCEPACK;
            case "shaders", "shaderpack", "shaderpacks" -> SHADER;
            case "datapacks" -> DATAPACK;
            default -> throw new IllegalArgumentException("Unknown content kind: " + value);
        };
    }
}
