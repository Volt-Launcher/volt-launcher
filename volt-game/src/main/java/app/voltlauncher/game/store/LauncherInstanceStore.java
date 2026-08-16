package app.voltlauncher.game.store;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.game.instance.InstanceSettings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class LauncherInstanceStore {

    private final Path storagePath = AppPaths.instancesMetadataPath();

    public synchronized List < Instance> listInstances() throws Exception {
        if (!Files.exists(storagePath)) {
            return new ArrayList <> ();
        }

        JSONObject root = new JSONObject(Files.readString(storagePath, StandardCharsets.UTF_8));
        JSONArray instances = root.optJSONArray("instances");
        if (instances == null) {
            return new ArrayList <> ();
        }

        List < Instance> result = new ArrayList <> ();
        for (int i = 0; i < instances.length(); i++) {
            JSONObject instance = instances.getJSONObject(i);
            result.add(new Instance( instance.getString("name"), instance.getString("slug"), instance.getString("versionId"), instance.optString("versionType", "release"), instance.getLong("createdAt"), instance.optLong("lastPlayedAt", 0L), instance.optInt("javaMajorVersion", 0), instance.optString("javaComponent", ""), InstanceSettings.fromJson(instance.optJSONObject("settings"))));
        }

        result.sort(Comparator .comparingLong((Instance instance) -> instance.lastPlayedAt() > 0 ? instance.lastPlayedAt() : instance.createdAt()) .reversed() .thenComparing(Instance::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized Optional < Instance> findByName(String name) throws Exception {
        return listInstances().stream()
        .filter(instance -> instance.name().equalsIgnoreCase(name))
        .findFirst();
    }

    public synchronized void saveInstances(List < Instance> instances) throws Exception {
        Files.createDirectories(storagePath.getParent());

        JSONArray array = new JSONArray();
        for (Instance instance : instances) {
            JSONObject json = new JSONObject();
            json.put("name", instance.name());
            json.put("slug", instance.slug());
            json.put("versionId", instance.versionId());
            json.put("versionType", instance.versionType());
            json.put("createdAt", instance.createdAt());
            json.put("lastPlayedAt", instance.lastPlayedAt());
            json.put("javaMajorVersion", instance.javaMajorVersion());
            json.put("javaComponent", instance.javaComponent());
            json.put("settings", instance.settings().toJson());
            array.put(json);
        }

        JSONObject root = new JSONObject();
        root.put("instances", array);
        Files.writeString(storagePath, root.toString(2), StandardCharsets.UTF_8);
    }
}

