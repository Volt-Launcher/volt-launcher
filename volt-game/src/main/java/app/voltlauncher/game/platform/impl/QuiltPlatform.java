package app.voltlauncher.game.platform.impl;

import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.AbstractPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class QuiltPlatform extends AbstractPlatform {

    public QuiltPlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.QUILT_ID, "Quilt", versionResolver);
    }
}

