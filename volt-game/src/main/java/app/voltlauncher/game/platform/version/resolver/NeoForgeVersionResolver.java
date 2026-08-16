package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.version.IVersionResolver;

import java.util.Optional;

/**
 * NeoForge. Its versions drop the leading "1." of the Minecraft version and append their own
 * build number, so Minecraft {@code 1.21.1} maps onto NeoForge {@code 21.1.x}. The installer
 * coordinate is the NeoForge version alone.
 *
 * <p>NeoForge's very first release line (Minecraft 1.20.1) instead used Forge-style
 * {@code 1.20.1-47.1.x} coordinates, which is handled as a special case.
 */
public final class NeoForgeVersionResolver extends MavenInstallerVersionResolver {

    private static final String METADATA_URL =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String INSTALLER_URL =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/%1$s/neoforge-%1$s-installer.jar";

    public NeoForgeVersionResolver(IVersionResolver vanillaResolver, HttpFetcher http) {
        super(PlatformRegistry.NEOFORGE_ID, vanillaResolver, http, METADATA_URL);
    }

    @Override
    protected Optional<String> matchLoaderVersion(String minecraftVersion, String mavenVersion) {
        String legacyPrefix = minecraftVersion + "-";
        if (mavenVersion.startsWith(legacyPrefix)) {
            String loaderVersion = mavenVersion.substring(legacyPrefix.length()).trim();
            return loaderVersion.isBlank() ? Optional.empty() : Optional.of(loaderVersion);
        }

        String expected = neoForgePrefix(minecraftVersion);
        boolean matches = mavenVersion.equals(expected)
                || mavenVersion.startsWith(expected + ".")
                || mavenVersion.startsWith(expected + "-");
        return matches ? Optional.of(mavenVersion) : Optional.empty();
    }

    @Override
    protected String installerUrl(String minecraftVersion, String loaderVersion) {
        return INSTALLER_URL.formatted(loaderVersion);
    }

    @Override
    protected String installerCacheName(String minecraftVersion, String loaderVersion) {
        return "neoforge-" + loaderVersion;
    }

    /** {@code 1.21.1} → {@code 21.1}; {@code 1.21} → {@code 21.0}. */
    private String neoForgePrefix(String minecraftVersion) {
        if (!minecraftVersion.startsWith("1.")) return minecraftVersion;
        String tail = minecraftVersion.substring(2);
        return tail.contains(".") ? tail : tail + ".0";
    }
}
