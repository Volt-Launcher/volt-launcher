package app.voltlauncher.game.platform;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.impl.FabricPlatform;
import app.voltlauncher.game.platform.impl.ForgePlatform;
import app.voltlauncher.game.platform.impl.NeoForgePlatform;
import app.voltlauncher.game.platform.impl.QuiltPlatform;
import app.voltlauncher.game.platform.impl.VanillaPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;
import app.voltlauncher.game.platform.version.resolver.FabricVersionResolver;
import app.voltlauncher.game.platform.version.resolver.ForgeVersionResolver;
import app.voltlauncher.game.platform.version.resolver.NeoForgeVersionResolver;
import app.voltlauncher.game.platform.version.resolver.QuiltVersionResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PlatformRegistry {

    public static final String VANILLA_ID = "vanilla";
    public static final String FABRIC_ID = "fabric";
    public static final String FORGE_ID = "forge";
    public static final String NEOFORGE_ID = "neoforge";
    public static final String QUILT_ID = "quilt";

    /** Display order in the UI — vanilla first, then loaders by popularity. */
    private static final List<String> DISPLAY_ORDER =
            List.of(VANILLA_ID, FABRIC_ID, NEOFORGE_ID, FORGE_ID, QUILT_ID);

    private final Map<String, IPlatform> byId = new ConcurrentHashMap<>();

    public static PlatformRegistry withDefaults(IVersionResolver vanillaResolver, HttpFetcher http) {
        PlatformRegistry registry = new PlatformRegistry();
        registry.register(new VanillaPlatform(vanillaResolver));
        registry.register(new FabricPlatform(new FabricVersionResolver(vanillaResolver, http)));
        registry.register(new ForgePlatform(new ForgeVersionResolver(vanillaResolver, http)));
        registry.register(new NeoForgePlatform(new NeoForgeVersionResolver(vanillaResolver, http)));
        registry.register(new QuiltPlatform(new QuiltVersionResolver(vanillaResolver, http)));
        return registry;
    }

    public PlatformRegistry register(IPlatform platform) {
        byId.put(normalize(platform.id()), platform);
        return this;
    }

    public Optional<IPlatform> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(byId.get(normalize(id)));
    }

    public IPlatform require(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("Unsupported platform: " + id));
    }

    public IPlatform requireDefault() {
        return require(VANILLA_ID);
    }

    public List<IPlatform> list() {
        List<IPlatform> values = new ArrayList<>(byId.values());
        values.sort(Comparator.comparingInt(p -> {
            int index = DISPLAY_ORDER.indexOf(p.id().toLowerCase(Locale.ROOT));
            return index < 0 ? DISPLAY_ORDER.size() : index;
        }));
        return Collections.unmodifiableList(values);
    }

    /**
     * Maps a version id such as {@code fabric:1.21.1:0.16.9} onto its platform. Plain
     * Minecraft ids (no prefix) fall back to the supplied platform.
     */
    public IPlatform resolvePlatformForVersion(String versionId, IPlatform fallback) {
        if (versionId == null || versionId.isBlank()) return fallback;
        int sep = versionId.indexOf(':');
        if (sep <= 0) return fallback;
        String candidate = versionId.substring(0, sep).trim().toLowerCase(Locale.ROOT);
        return get(candidate).orElse(fallback);
    }

    /** Extracts the plain Minecraft version from a possibly platform-qualified id. */
    public static String extractBaseMinecraftVersionId(String versionId) {
        if (versionId == null || versionId.isBlank()) return versionId;
        String trimmed = versionId.trim();
        int firstSep = trimmed.indexOf(':');
        if (firstSep <= 0) return trimmed;
        String rest = trimmed.substring(firstSep + 1);
        int secondSep = rest.indexOf(':');
        return secondSep >= 0 ? rest.substring(0, secondSep).trim() : rest.trim();
    }

    /** Extracts the loader version from a qualified id, or an empty string when unqualified. */
    public static String extractLoaderVersion(String versionId) {
        if (versionId == null || versionId.isBlank()) return "";
        String trimmed = versionId.trim();
        int firstSep = trimmed.indexOf(':');
        if (firstSep <= 0) return "";
        int secondSep = trimmed.indexOf(':', firstSep + 1);
        return secondSep < 0 ? "" : trimmed.substring(secondSep + 1).trim();
    }

    private String normalize(String id) {
        return Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT);
    }
}
