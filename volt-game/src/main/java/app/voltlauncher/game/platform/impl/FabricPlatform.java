package app.voltlauncher.game.platform.impl;

import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.AbstractPlatform;
import app.voltlauncher.game.platform.version.IVersionResolver;

public final class FabricPlatform extends AbstractPlatform {

    public FabricPlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.FABRIC_ID, "Fabric", versionResolver);
    }
}

