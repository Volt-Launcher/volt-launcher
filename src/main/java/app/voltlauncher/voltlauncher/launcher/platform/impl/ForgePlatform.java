package app.voltlauncher.voltlauncher.launcher.platform.impl;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.base.AbstractPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

public final class ForgePlatform extends AbstractPlatform {

	public ForgePlatform(IVersionResolver versionResolver) {
		super(PlatformRegistry.FORGE_ID, "Forge", versionResolver);
	}
}
