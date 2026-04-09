package app.voltlauncher.voltlauncher.launcher.platform.impl;

import app.voltlauncher.voltlauncher.launcher.platform.PlatformRegistry;
import app.voltlauncher.voltlauncher.launcher.platform.base.AbstractPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

public final class FabricPlatform extends AbstractPlatform {

	public FabricPlatform(IVersionResolver versionResolver) {
		super(PlatformRegistry.FABRIC_ID, "Fabric", versionResolver);
	}
}
