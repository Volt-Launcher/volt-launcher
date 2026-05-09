package app.voltlauncher.voltlauncher.launcher.platform.version;

import org.json.JSONObject;

import java.util.List;

public interface IVersionResolver {

    List < AvailableVersion> listAvailableVersions() throws Exception;

    default List < AvailableVersion> listLoaderVersions(String minecraftVersionId) throws Exception {
        return listAvailableVersions().stream()
        .filter(v -> v.id().equals(minecraftVersionId))
        .toList();
    }

    JSONObject resolveMetadata(String versionId) throws Exception;
}

