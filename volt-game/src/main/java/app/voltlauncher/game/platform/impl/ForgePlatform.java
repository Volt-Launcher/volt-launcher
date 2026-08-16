package app.voltlauncher.game.platform.impl;

import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.AbstractPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class ForgePlatform extends AbstractPlatform {

    public ForgePlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.FORGE_ID, "Forge", versionResolver);
    }
}

