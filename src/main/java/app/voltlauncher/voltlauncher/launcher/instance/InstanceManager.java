package app.voltlauncher.voltlauncher.launcher.instance;

import app.voltlauncher.voltlauncher.launcher.platform.version.resolver.VanillaVersionResolver;
import app.voltlauncher.voltlauncher.storage.LauncherInstanceStore;
import app.voltlauncher.voltlauncher.util.async.NamedLock;
import org.json.JSONObject;

import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InstanceManager {

    private static final int MAX_NAME = 64;
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
        String normalized = normalizeName(name);
        validateName(normalized);
        VanillaVersionResolver.ManifestEntry entry = versionResolver.findEntry(versionId);
        JSONObject meta = versionResolver.resolveMetadata(entry.id());
        return createInstance(normalized, entry.id(), entry.type(), meta);
    }

    public Instance createInstance(String name, String versionId, String versionType, JSONObject meta) throws Exception {
        String normalized = normalizeName(name);
        validateName(normalized);
        RequiredJava req = resolveRequiredJava(meta, versionId);

        return lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                if (instances.stream().anyMatch(i -> i.name().equalsIgnoreCase(normalized))) {
                    throw new IllegalStateException("An instance with this name already exists");
                }
                String slug = uniqueSlug(normalized, instances);
                long now = System.currentTimeMillis();
                Instance instance = new Instance(
                        normalized, slug, versionId, versionType,
                        now, 0L, req.majorVersion(), req.component());
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
        String normalized = normalizeName(name);
        lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                List<Instance> updated = new ArrayList<>(instances.size());
                for (Instance i : instances) {
                    if (!i.name().equalsIgnoreCase(normalized)) {
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

    public void markPlayed(Instance launched, long startedAt) throws Exception {
        lock.withLock(STORE_KEY, () -> {
            try {
                List<Instance> instances = listInstances();
                List<Instance> updated = new ArrayList<>(instances.size());
                for (Instance i : instances) {
                    if (i.name().equalsIgnoreCase(launched.name())) {
                        updated.add(new Instance(
                                i.name(), i.slug(), i.versionId(), i.versionType(),
                                i.createdAt(), startedAt, i.javaMajorVersion(), i.javaComponent()));
                        continue;
                    }
                    updated.add(i);
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

    public record RequiredJava(int majorVersion, String component) {}

    public RequiredJava resolveRequiredJava(JSONObject meta, String versionId) {
        JSONObject jv = meta.optJSONObject("javaVersion");
        if (jv != null) {
            int major = jv.optInt("majorVersion", 0);
            if (major > 0) {
                return new RequiredJava(major, jv.optString("component", ""));
            }
        }
        return new RequiredJava(guessLegacyJava(versionId), "legacy-runtime");
    }

    private List<Instance> upgradeJavaRequirements(List<Instance> instances) throws Exception {
        boolean changed = false;
        List<Instance> upgraded = new ArrayList<>(instances.size());
        for (Instance i : instances) {
            Instance up = ensureJavaReq(i);
            upgraded.add(up);
            if (!up.equals(i)) {
                changed = true;
            }
        }
        if (changed) {
            store.saveInstances(upgraded);
        }
        return upgraded;
    }

    private Instance ensureJavaReq(Instance i) throws Exception {
        if (i.hasJavaRequirement()) {
            return i;
        }
        JSONObject meta = versionResolver.resolveMetadata(i.versionId());
        RequiredJava req = resolveRequiredJava(meta, i.versionId());
        return new Instance(
                i.name(), i.slug(), i.versionId(), i.versionType(),
                i.createdAt(), i.lastPlayedAt(), req.majorVersion(), req.component());
    }

    private void validateName(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Instance name is required");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Instance name must be 64 characters or shorter");
        }
        if (hasUnsupportedChar(name)) {
            throw new IllegalArgumentException(
                    "Instance name contains unsupported characters: / \\ : * ? \" < > | or control characters");
        }
    }

    private int guessLegacyJava(String id) {
        if (id == null || id.isBlank()) {
            return 8;
        }
        if (id.startsWith("25w") || id.startsWith("26.")) {
            return 21;
        }
        if (id.startsWith("1.21") || id.startsWith("1.20") || id.startsWith("1.19")
                || id.startsWith("1.18") || id.startsWith("24w")) {
            return 17;
        }
        if (id.startsWith("1.17") || id.startsWith("21w")) {
            return 16;
        }
        return 8;
    }

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }

    private String uniqueSlug(String name, List<Instance> existing) {
        String base = slugify(name);
        if (base.isBlank()) {
            base = "instance";
        }
        Set<String> used = new HashSet<>();
        for (Instance i : existing) {
            used.add(i.slug().toLowerCase(Locale.ROOT));
        }
        String slug = base;
        int counter = 2;
        while (used.contains(slug.toLowerCase(Locale.ROOT))) {
            slug = base + "-" + counter;
            counter++;
        }
        return slug;
    }

    private String slugify(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private boolean hasUnsupportedChar(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|'
                    || Character.isISOControl(c)) {
                return true;
            }
        }
        return false;
    }
}