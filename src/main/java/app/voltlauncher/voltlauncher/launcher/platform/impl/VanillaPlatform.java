package app.voltlauncher.voltlauncher.launcher.platform.impl;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.base.AbstractPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

public final class VanillaPlatform extends AbstractPlatform {

	public VanillaPlatform(IVersionResolver versionResolver) {
		super(PlatformRegistry.VANILLA_ID, "Vanilla", versionResolver);
	}
}
