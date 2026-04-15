package app.voltlauncher.voltlauncher.launcher.platform.version.resolver;

import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UnavailableVersionResolver implements IVersionResolver {

    private final String platformId;

    public UnavailableVersionResolver(String platformId) {
        this.platformId = Objects.requireNonNull(platformId, "platformId");
    }

    @Override
    public List<AvailableVersion> listAvailableVersions() {
        return Collections.emptyList();
    }

    @Override
    public JSONObject resolveMetadata(String versionId) {
        throw new UnsupportedOperationException("Resolver for platform '" + platformId + "' is not implemented yet");
    }
}

