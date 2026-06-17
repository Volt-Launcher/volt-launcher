package app.voltlauncher.voltlauncher.launcher.instance;

import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.storage.LauncherInstanceStore;
import app.voltlauncher.voltlauncher.util.async.NamedLock;
import org.json.JSONObject;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class InstanceManager {

    private static final String STORE_KEY = "instance-store";

    private final LauncherInstanceStore store;
    private final VanillaVersionResolver versionResolver;
    private final NamedLock lock = new NamedLock();

    public InstanceManager(LauncherInstanceStore store, VanillaVersionResolver versionResolver) {
        this.store = store;
        this.versionResolver = versionResolver;
    }

    public List<Instance> listInstances() throws Exception {
        return upgradeJavaRequirements(store.listInstances());
    }

    public Instance createInstance(String name, String versionId) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        InstanceNamePolicy.validate(normalized);
        VanillaVersionResolver.ManifestEntry entry = versionResolver.findEntry(versionId);
        JSONObject meta = versionResolver.resolveMetadata(entry.id());
        return createInstance(normalized, entry.id(), entry.type(), meta);
    }

    public Instance createInstance(String name, String versionId, String versionType, JSONObject meta) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        InstanceNamePolicy.validate(normalized);
        RequiredJava req = resolveRequiredJava(meta, versionId);

        return lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                if (instances.stream().anyMatch(i -> i.name().equalsIgnoreCase(normalized))) {
                    throw new IllegalStateException("An instance with this name already exists");
                }
                String slug = InstanceNamePolicy.uniqueSlug(normalized, instances);
                long now = System.currentTimeMillis();
                Instance instance = new Instance(
                        normalized, slug, versionId, versionType, now, 0L,
                        req.majorVersion(), req.component(), InstanceSettings.defaults());
                Files.createDirectories(instance.gameDirectory());
                instances.add(instance);
                store.saveInstances(instances);
                return instance;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void removeInstance(String name) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                List<Instance> updated = instances.stream()
                        .filter(i -> !i.name().equalsIgnoreCase(normalized))
                        .collect(java.util.stream.Collectors.toList());
                store.saveInstances(updated);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public Instance renameInstance(String name, String newName) throws Exception {
        String normalizedOld = InstanceNamePolicy.normalize(name);
        String normalizedNew = InstanceNamePolicy.normalize(newName);
        InstanceNamePolicy.validate(normalizedNew);
        return lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                if (instances.stream().noneMatch(i -> i.name().equalsIgnoreCase(normalizedOld))) {
                    throw new IllegalStateException("Instance not found: " + normalizedOld);
                }
                if (instances.stream().anyMatch(i -> !i.name().equalsIgnoreCase(normalizedOld)
                        && i.name().equalsIgnoreCase(normalizedNew))) {
                    throw new IllegalStateException("An instance with this name already exists");
                }
                List<Instance> updated = new ArrayList<>(instances.size());
                Instance renamed = null;
                for (Instance i : instances) {
                    if (i.name().equalsIgnoreCase(normalizedOld)) {
                        renamed = new Instance(normalizedNew, i.slug(), i.versionId(), i.versionType(),
                                i.createdAt(), i.lastPlayedAt(), i.javaMajorVersion(), i.javaComponent(), i.settings());
                        updated.add(renamed);
                    } else {
                        updated.add(i);
                    }
                }
                store.saveInstances(updated);
                return renamed;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public Instance updateSettings(String name, InstanceSettings settings) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        return lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                List<Instance> updated = new ArrayList<>(instances.size());
                Instance result = null;
                for (Instance i : instances) {
                    if (i.name().equalsIgnoreCase(normalized)) {
                        result = i.withSettings(settings);
                        updated.add(result);
                    } else {
                        updated.add(i);
                    }
                }
                if (result == null) throw new IllegalStateException("Instance not found: " + normalized);
                store.saveInstances(updated);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void markPlayed(Instance launched, long startedAt) throws Exception {
        lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                List<Instance> updated = new ArrayList<>(instances.size());
                for (Instance i : instances) {
                    if (i.name().equalsIgnoreCase(launched.name())) {
                        updated.add(new Instance(i.name(), i.slug(), i.versionId(), i.versionType(),
                                i.createdAt(), startedAt, i.javaMajorVersion(), i.javaComponent(), i.settings()));
                    } else {
                        updated.add(i);
                    }
                }
                store.saveInstances(updated);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public Instance findByName(String name) throws Exception {
        return listInstances().stream()
                .filter(i -> i.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Instance not found: " + name));
    }

    public RequiredJava resolveRequiredJava(JSONObject meta, String versionId) {
        JSONObject jv = meta.optJSONObject("javaVersion");
        if (jv != null) {
            int major = jv.optInt("majorVersion", 0);
            if (major > 0) return new RequiredJava(major, jv.optString("component", ""));
        }
        return new RequiredJava(guessLegacyJava(versionId), "legacy-runtime");
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private List<Instance> upgradeJavaRequirements(List<Instance> instances) throws Exception {
        boolean changed = false;
        List<Instance> upgraded = new ArrayList<>(instances.size());
        for (Instance i : instances) {
            Instance up = ensureJavaReq(i);
            upgraded.add(up);
            if (!up.equals(i)) changed = true;
        }
        if (changed) store.saveInstances(upgraded);
        return upgraded;
    }

    private Instance ensureJavaReq(Instance i) throws Exception {
        if (i.hasJavaRequirement()) return i;
        JSONObject meta = versionResolver.resolveMetadata(i.versionId());
        RequiredJava req = resolveRequiredJava(meta, i.versionId());
        return new Instance(i.name(), i.slug(), i.versionId(), i.versionType(),
                i.createdAt(), i.lastPlayedAt(), req.majorVersion(), req.component(), i.settings());
    }

    private int guessLegacyJava(String id) {
        if (id == null || id.isBlank()) return 8;
        // Strip platform prefix: "neoforge:26.1.2:loader" → "26.1.2"
        String base = id;
        int firstColon = id.indexOf(':');
        if (firstColon >= 0) {
            String afterFirst = id.substring(firstColon + 1);
            int secondColon = afterFirst.indexOf(':');
            base = secondColon >= 0 ? afterFirst.substring(0, secondColon) : afterFirst;
        }
        if (base.startsWith("25w") || base.startsWith("26.")) return 21;
        if (base.startsWith("1.21") || base.startsWith("1.20") || base.startsWith("1.19")
                || base.startsWith("1.18") || base.startsWith("24w")) return 17;
        if (base.startsWith("1.17") || base.startsWith("21w")) return 16;
        return 8;
    }

    public record RequiredJava(int majorVersion, String component) {}
}
