package app.voltlauncher.game.platform.impl;

import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.AbstractPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class NeoForgePlatform extends AbstractPlatform {

    public NeoForgePlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.NEOFORGE_ID, "NeoForge", versionResolver);
    }
}

