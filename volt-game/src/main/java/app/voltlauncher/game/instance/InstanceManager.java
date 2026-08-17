package app.voltlauncher.game.instance;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.core.util.async.NamedLock;
import app.voltlauncher.game.platform.IPlatform;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.game.store.LauncherInstanceStore;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class InstanceManager {

    private static final String STORE_KEY = "instance-store";

    private final LauncherInstanceStore store;
    private final VanillaVersionResolver versionResolver;
    private final PlatformRegistry platformRegistry;
    private final NamedLock lock = new NamedLock();

    public InstanceManager(LauncherInstanceStore store, VanillaVersionResolver versionResolver,
                           PlatformRegistry platformRegistry) {
        this.store = store;
        this.versionResolver = versionResolver;
        this.platformRegistry = platformRegistry;
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
        });
    }

    /** Removes the instance from the registry and deletes its files from disk. */
    public void removeInstance(String name) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        lock.withLock(STORE_KEY, () -> {
            List<Instance> instances = listInstances();
            Instance removed = instances.stream()
                    .filter(i -> i.name().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(null);
            if (removed == null) return null;

            instances.removeIf(i -> i.name().equalsIgnoreCase(normalized));
            store.saveInstances(instances);
            deleteRecursively(AppPaths.instanceDirectory(removed.slug()));
            return null;
        });
    }

    public Instance renameInstance(String name, String newName) throws Exception {
        String normalizedOld = InstanceNamePolicy.normalize(name);
        String normalizedNew = InstanceNamePolicy.normalize(newName);
        InstanceNamePolicy.validate(normalizedNew);
        return lock.withLock(STORE_KEY, () -> {
            List<Instance> instances = listInstances();
            if (instances.stream().noneMatch(i -> i.name().equalsIgnoreCase(normalizedOld))) {
                throw new IllegalStateException("Instance not found: " + normalizedOld);
            }
            if (instances.stream().anyMatch(i -> !i.name().equalsIgnoreCase(normalizedOld)
                    && i.name().equalsIgnoreCase(normalizedNew))) {
                throw new IllegalStateException("An instance with this name already exists");
            }

            // The slug is intentionally left alone so the game directory (and everything a user
            // has put into it) stays where it is.
            List<Instance> updated = new ArrayList<>(instances.size());
            Instance renamed = null;
            for (Instance i : instances) {
                if (i.name().equalsIgnoreCase(normalizedOld)) {
                    renamed = i.withName(normalizedNew);
                    updated.add(renamed);
                } else {
                    updated.add(i);
                }
            }
            store.saveInstances(updated);
            return renamed;
        });
    }

    public Instance updateSettings(String name, InstanceSettings settings) throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        return lock.withLock(STORE_KEY, () -> {
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
        });
    }

    /**
     * Re-points a profile at another game version. Used when a modpack update moves the pack to a
     * new Minecraft or loader release; the slug — and therefore the game directory with the user's
     * worlds in it — is deliberately untouched.
     */
    public Instance updateVersion(String name, String versionId, String versionType, JSONObject meta)
            throws Exception {
        String normalized = InstanceNamePolicy.normalize(name);
        RequiredJava req = resolveRequiredJava(meta, versionId);

        return lock.withLock(STORE_KEY, () -> {
            List<Instance> instances = listInstances();
            List<Instance> updated = new ArrayList<>(instances.size());
            Instance result = null;
            for (Instance i : instances) {
                if (i.name().equalsIgnoreCase(normalized)) {
                    result = i.withVersion(versionId, versionType, req.majorVersion(), req.component());
                    updated.add(result);
                } else {
                    updated.add(i);
                }
            }
            if (result == null) throw new IllegalStateException("Instance not found: " + normalized);
            store.saveInstances(updated);
            return result;
        });
    }

    public void markPlayed(Instance launched, long startedAt) throws Exception {
        lock.withLock(STORE_KEY, () -> {
            List<Instance> instances = listInstances();
            List<Instance> updated = new ArrayList<>(instances.size());
            for (Instance i : instances) {
                updated.add(i.name().equalsIgnoreCase(launched.name()) ? i.withLastPlayedAt(startedAt) : i);
            }
            store.saveInstances(updated);
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

    /**
     * Backfills the Java requirement for instances stored before it was recorded. Resolution goes
     * through the instance's own platform, because a modded version id such as
     * {@code fabric:1.21.1:0.16.9} is meaningless to the vanilla manifest. Network failures fall
     * back to the version-name heuristic rather than making the whole instance list unreadable.
     */
    private Instance ensureJavaReq(Instance i) {
        if (i.hasJavaRequirement()) return i;
        RequiredJava req;
        try {
            IPlatform platform = platformRegistry.resolvePlatformForVersion(
                    i.versionId(), platformRegistry.requireDefault());
            JSONObject meta = platform.versionResolver().resolveMetadata(i.versionId());
            req = resolveRequiredJava(meta, i.versionId());
        } catch (Exception e) {
            req = new RequiredJava(guessLegacyJava(i.versionId()), "legacy-runtime");
        }
        return i.withJavaRequirement(req.majorVersion(), req.component());
    }

    private int guessLegacyJava(String id) {
        String base = PlatformRegistry.extractBaseMinecraftVersionId(id);
        if (base == null || base.isBlank()) return 8;
        if (base.startsWith("1.21") || base.startsWith("1.22") || base.startsWith("25w") || base.startsWith("26w")) return 21;
        if (base.startsWith("1.20") || base.startsWith("1.19") || base.startsWith("1.18")
                || base.startsWith("23w") || base.startsWith("24w") || base.startsWith("22w")) return 17;
        if (base.startsWith("1.17") || base.startsWith("21w")) return 16;
        return 8;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record RequiredJava(int majorVersion, String component) {}
}
