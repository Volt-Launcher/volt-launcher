package app.voltlauncher.voltlauncher.launcher.platform.base;

import app.voltlauncher.voltlauncher.launcher.platform.IPlatform;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;

import java.util.Objects;

public abstract class AbstractPlatform implements IPlatform {

    private final String id;
    private final String displayName;
    private final IVersionResolver versionResolver;

    protected AbstractPlatform(String id, String displayName, IVersionResolver versionResolver) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.versionResolver = Objects.requireNonNull(versionResolver, "versionResolver");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public IVersionResolver versionResolver() {
        return versionResolver;
    }
}

