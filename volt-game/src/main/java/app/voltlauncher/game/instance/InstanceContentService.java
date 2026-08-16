package app.voltlauncher.game.instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Manages files inside an instance's game directory content folders
 * (mods, resourcepacks, shaderpacks, datapacks). A file is "disabled" by giving
 * it a trailing {@code .disabled} suffix, which Minecraft loaders ignore.
 */
public final class InstanceContentService {

    private static final String DISABLED_SUFFIX = ".disabled";

    public enum ContentType {
        MODS("mods"),
        RESOURCEPACKS("resourcepacks"),
        SHADERPACKS("shaderpacks"),
        DATAPACKS("datapacks");

        private final String folder;

        ContentType(String folder) {
            this.folder = folder;
        }

        public String folder() {
            return folder;
        }

        public static ContentType fromId(String id) {
            if (id == null) throw new IllegalArgumentException("Content type is required");
            for (ContentType t : values()) {
                if (t.folder.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
            }
            throw new IllegalArgumentException("Unknown content type: " + id);
        }
    }

    public record ContentEntry(String fileName, long size, boolean enabled) {}

    private final InstanceManager instanceManager;

    public InstanceContentService(InstanceManager instanceManager) {
        this.instanceManager = instanceManager;
    }

    public List<ContentEntry> list(String instanceName, ContentType type) throws Exception {
        Path dir = contentDir(instanceName, type);
        List<ContentEntry> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) return result;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                boolean enabled = !name.endsWith(DISABLED_SUFFIX);
                String displayName = enabled ? name : name.substring(0, name.length() - DISABLED_SUFFIX.length());
                long size;
                try { size = Files.size(p); } catch (Exception e) { size = 0L; }
                result.add(new ContentEntry(displayName, size, enabled));
            });
        }
        result.sort(Comparator.comparing(e -> e.fileName().toLowerCase(Locale.ROOT)));
        return result;
    }

    public ContentEntry add(String instanceName, ContentType type, Path source) throws Exception {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Source file not found");
        }
        Path dir = contentDir(instanceName, type);
        Files.createDirectories(dir);
        String fileName = source.getFileName().toString();
        Path target = uniqueTarget(dir, fileName);
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        return new ContentEntry(target.getFileName().toString(), Files.size(target), true);
    }

    public void remove(String instanceName, ContentType type, String fileName) throws Exception {
        Path resolved = resolveExisting(instanceName, type, fileName);
        Files.deleteIfExists(resolved);
    }

    public ContentEntry toggle(String instanceName, ContentType type, String fileName) throws Exception {
        Path current = resolveExisting(instanceName, type, fileName);
        String name = current.getFileName().toString();
        Path target;
        boolean nowEnabled;
        if (name.endsWith(DISABLED_SUFFIX)) {
            target = current.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
            nowEnabled = true;
        } else {
            target = current.resolveSibling(name + DISABLED_SUFFIX);
            nowEnabled = false;
        }
        Files.move(current, target, StandardCopyOption.REPLACE_EXISTING);
        return new ContentEntry(fileName, Files.size(target), nowEnabled);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Path contentDir(String instanceName, ContentType type) throws Exception {
        Instance instance = instanceManager.findByName(instanceName);
        return instance.gameDirectory().resolve(type.folder());
    }

    private Path resolveExisting(String instanceName, ContentType type, String fileName) throws Exception {
        String safe = sanitize(fileName);
        Path dir = contentDir(instanceName, type);
        Path enabled = dir.resolve(safe);
        if (Files.exists(enabled)) return enabled;
        Path disabled = dir.resolve(safe + DISABLED_SUFFIX);
        if (Files.exists(disabled)) return disabled;
        // fileName may already carry the .disabled suffix
        Path asGiven = dir.resolve(safe);
        if (Files.exists(asGiven)) return asGiven;
        throw new IllegalStateException("File not found: " + fileName);
    }

    private Path uniqueTarget(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target) && !Files.exists(dir.resolve(fileName + DISABLED_SUFFIX))) return target;
        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) { base = fileName.substring(0, dot); ext = fileName.substring(dot); }
        for (int i = 1; i < 1000; i++) {
            Path candidate = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate)) return candidate;
        }
        return dir.resolve(base + "-" + System.currentTimeMillis() + ext);
    }

    private String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("File name is required");
        String name = fileName.replace("\\", "/");
        if (name.contains("/") || name.contains("..")) throw new IllegalArgumentException("Invalid file name");
        return name;
    }
}
