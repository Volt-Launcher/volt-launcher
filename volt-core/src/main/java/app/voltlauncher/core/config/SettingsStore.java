package app.voltlauncher.core.config;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.core.util.JsonUtil;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.UnaryOperator;

/**
 * Reads and writes {@link LauncherSettings}, caching the current value in memory so the UI can
 * poll it cheaply. Writes go through a temporary file so a crash mid-save cannot leave a
 * truncated settings file behind.
 */
public final class SettingsStore {

    private final Path path;
    private volatile LauncherSettings cached;

    public SettingsStore() {
        this(AppPaths.settingsPath());
    }

    public SettingsStore(Path path) {
        this.path = path;
    }

    public synchronized LauncherSettings get() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    public synchronized LauncherSettings save(LauncherSettings settings) throws Exception {
        LauncherSettings normalized = settings == null ? LauncherSettings.defaults() : settings;
        writeAtomically(normalized.toJson());
        cached = normalized;
        return normalized;
    }

    /** Applies a change to the current settings and persists the result. */
    public synchronized LauncherSettings update(UnaryOperator<LauncherSettings> change) throws Exception {
        return save(change.apply(get()));
    }

    /**
     * Merges a partial JSON patch into the stored settings, so the UI can save one section
     * without having to send every field back.
     */
    public synchronized LauncherSettings patch(JSONObject partial) throws Exception {
        JSONObject merged = get().toJson();
        if (partial != null) {
            for (String key : partial.keySet()) {
                merged.put(key, partial.get(key));
            }
        }
        return save(LauncherSettings.fromJson(merged));
    }

    private LauncherSettings load() {
        try {
            if (!Files.exists(path)) return LauncherSettings.defaults();
            return LauncherSettings.fromJson(JsonUtil.readFile(path));
        } catch (Exception e) {
            System.err.println("[Settings] Could not read " + path + ", falling back to defaults: " + e.getMessage());
            return LauncherSettings.defaults();
        }
    }

    private void writeAtomically(JSONObject json) throws Exception {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "settings-", ".tmp");
        try {
            Files.writeString(temp, json.toString(2), StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
