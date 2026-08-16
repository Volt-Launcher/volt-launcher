package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.version.IVersionResolver;

import java.util.Optional;

/**
 * Minecraft Forge. Maven versions are {@code <minecraftVersion>-<forgeVersion>}, occasionally with
 * a trailing branch suffix (e.g. {@code 1.12.2-14.23.5.2860-1.12.2}), and the installer path
 * repeats the full coordinate.
 */
public final class ForgeVersionResolver extends MavenInstallerVersionResolver {

    private static final String METADATA_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String INSTALLER_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/%1$s/forge-%1$s-installer.jar";

    public ForgeVersionResolver(IVersionResolver vanillaResolver, HttpFetcher http) {
        super(PlatformRegistry.FORGE_ID, vanillaResolver, http, METADATA_URL);
    }

    @Override
    protected Optional<String> matchLoaderVersion(String minecraftVersion, String mavenVersion) {
        String prefix = minecraftVersion + "-";
        if (!mavenVersion.startsWith(prefix)) return Optional.empty();
        String loaderVersion = mavenVersion.substring(prefix.length()).trim();
        return loaderVersion.isBlank() ? Optional.empty() : Optional.of(loaderVersion);
    }

    @Override
    protected String installerUrl(String minecraftVersion, String loaderVersion) {
        return INSTALLER_URL.formatted(minecraftVersion + "-" + loaderVersion);
    }
}
