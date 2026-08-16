package app.voltlauncher.game.platform.impl;

import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.AbstractPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class VanillaPlatform extends AbstractPlatform {

    public VanillaPlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.VANILLA_ID, "Vanilla", versionResolver);
    }
}

