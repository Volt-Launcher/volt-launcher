package app.voltlauncher.providers.modrinth;

import java.util.Locale;
import java.util.Set;

/**
 * Modrinth returns loaders inside the generic "categories" array on search hits, so they have to
 * be separated out by name to render loader chips distinctly from real categories.
 */
final class KnownLoaders {

    private static final Set<String> NAMES = Set.of(
            "fabric", "forge", "neoforge", "quilt", "liteloader", "rift", "modloader",
            "bukkit", "spigot", "paper", "purpur", "sponge", "bungeecord", "velocity",
            "waterfall", "folia", "iris", "optifine", "canvas", "vanilla", "datapack",
            "minecraft", "babric", "bta-babric", "java-agent", "legacy-fabric", "nilloader", "ornithe");

    private KnownLoaders() {}

    static boolean isLoader(String value) {
        return value != null && NAMES.contains(value.toLowerCase(Locale.ROOT));
    }
}
