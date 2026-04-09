package app.voltlauncher.voltlauncher.launcher.platform.impl;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.base.AbstractPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

public final class QuiltPlatform extends AbstractPlatform {

	public QuiltPlatform(IVersionResolver versionResolver) {
		super(PlatformRegistry.QUILT_ID, "Quilt", versionResolver);
	}
}
