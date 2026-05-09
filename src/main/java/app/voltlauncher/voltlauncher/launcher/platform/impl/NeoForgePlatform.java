package app.voltlauncher.voltlauncher.launcher.platform.impl;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.base.AbstractPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

public final class NeoForgePlatform extends AbstractPlatform {

    public NeoForgePlatform(IVersionResolver versionResolver) {
        super(PlatformRegistry.NEOFORGE_ID, "NeoForge", versionResolver);
    }
}

