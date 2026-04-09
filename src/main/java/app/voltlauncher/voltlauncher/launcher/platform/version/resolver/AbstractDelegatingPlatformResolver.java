package app.voltlauncher.voltlauncher.launcher.platform.version.resolver;

import app.voltlauncher.voltlauncher.launcher.platform.version.AvailableVersion;
import app.voltlauncher.voltlauncher.launcher.platform.version.IVersionResolver;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class AbstractDelegatingPlatformResolver implements IVersionResolver {

    private final String platformId;
    private final IVersionResolver vanillaResolver;

    protected AbstractDelegatingPlatformResolver(String platformId, IVersionResolver vanillaResolver) {
        this.platformId = Objects.requireNonNull(platformId, "platformId").toLowerCase(Locale.ROOT);
        this.vanillaResolver = Objects.requireNonNull(vanillaResolver, "vanillaResolver");
    }

    @Override
    public List<AvailableVersion> listAvailableVersions() throws Exception {
        List<AvailableVersion> mapped = new ArrayList<>();
        for (AvailableVersion version : vanillaResolver.listAvailableVersions()) {
            mapped.add(new AvailableVersion(qualify(version.id()), version.type(), version.releaseTime()));
        }
        return mapped;
    }

    @Override
    public List<AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        String baseVersionId = extractBaseVersion(minecraftVersionId);
        return List.of(new AvailableVersion(qualify(baseVersionId), "release", releaseTimeOf(baseVersionId)));
    }

    @Override
    public JSONObject resolveMetadata(String versionId) throws Exception {
        String baseVersionId = extractBaseVersion(versionId);
        JSONObject merged = new JSONObject(vanillaResolver.resolveMetadata(baseVersionId).toString());
        merged.put("id", qualify(baseVersionId));
        merged.put("jar", baseVersionId);
        merged.put("voltPlatform", platformId);
        return merged;
    }

    private String qualify(String versionId) {
        return platformId + ":" + versionId;
    }

    private String extractBaseVersion(String versionId) {
        String raw = Objects.requireNonNull(versionId, "versionId").trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("versionId is required");
        }

        String expectedPrefix = platformId + ":";
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.startsWith(expectedPrefix)) {
            String base = raw.substring(expectedPrefix.length()).trim();
            if (base.isBlank()) {
                throw new IllegalArgumentException("Missing base version id after prefix '" + platformId + "'");
            }
            int nextSep = base.indexOf(':');
            return nextSep >= 0 ? base.substring(0, nextSep).trim() : base;
        }
        return raw;
    }

    private String releaseTimeOf(String versionId) throws Exception {
        for (AvailableVersion version : vanillaResolver.listAvailableVersions()) {
            if (version.id().equals(versionId)) {
                return version.releaseTime();
            }
        }
        return "";
    }
}

