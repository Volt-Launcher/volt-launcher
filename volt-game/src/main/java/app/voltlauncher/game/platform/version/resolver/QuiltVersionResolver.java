package app.voltlauncher.game.platform.version.resolver;

import app.voltlauncher.core.util.HttpFetcher;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class QuiltVersionResolver extends MetaApiVersionResolver {

    private static final String LOADERS_URL = "https://meta.quiltmc.org/v3/versions/loader/";
    private static final String PROFILE_URL = "https://meta.quiltmc.org/v3/versions/loader/%s/%s/profile/json";

    public QuiltVersionResolver(IVersionResolver vanillaResolver, HttpFetcher http) {
        super(PlatformRegistry.QUILT_ID, vanillaResolver, http, LOADERS_URL, PROFILE_URL);
    }
}
