package app.voltlauncher.providers.content;

import app.voltlauncher.core.AppPaths;
import app.voltlauncher.game.MinecraftLauncherService;
import app.voltlauncher.game.instance.ContentSource;
import app.voltlauncher.game.instance.Instance;
import app.voltlauncher.game.instance.InstanceContentService;
import app.voltlauncher.game.platform.PlatformRegistry;
import app.voltlauncher.game.store.ContentManifestStore;
import app.voltlauncher.providers.model.ProviderId;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports a profile as a shareable modpack, either a Modrinth {@code .mrpack} or a CurseForge
 * {@code .zip}.
 *
 * <p>Both formats reference their mods by id and expect the launcher to download them, but each
 * only understands its own platform: an {@code .mrpack} lists Modrinth files, a CurseForge zip
 * lists CurseForge project/file pairs. Anything that cannot be expressed that way — files from the
 * other platform, files the user added by hand, configs — is carried inside {@code overrides/}
 * instead. That keeps the export self-contained and importable everywhere, at the cost of size.
 */
public final class PackExportService {

    public enum Format {
        MRPACK("mrpack", ".mrpack"),
        CURSEFORGE("curseforge", ".zip");

        private final String id;
        private final String extension;

        Format(String id, String extension) {
            this.id = id;
            this.extension = extension;
        }

        public String id() {
            return id;
        }

        public static Format fromId(String value) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Export format is required");
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Format format : values()) {
                if (format.id.equals(normalized)) return format;
            }
            throw new IllegalArgumentException("Unknown export format: " + value);
        }
    }

    /**
     * @param bundled files carried inside overrides because the target platform cannot reference them
     */
    public record ExportResult(Path file, int referenced, int bundled, List<String> notes) {}

    /**
     * Directories worth shipping with a pack. Deliberately not the whole game directory — that
     * would sweep in worlds, logs and crash reports, which nobody wants in a shared pack.
     */
    private static final List<String> EXTRA_DIRECTORIES = List.of(
            "config", "defaultconfigs", "kubejs", "scripts", "openloader", "global_packs", "patchouli_books");

    private static final List<String> EXTRA_FILES = List.of("options.txt");

    private final MinecraftLauncherService launcher;

    public PackExportService(MinecraftLauncherService launcher) {
        this.launcher = launcher;
    }

    public ExportResult export(String instanceName, Format format, String packVersion) throws Exception {
        launcher.busyRegistry().requireIdle(instanceName);
        Instance instance = launcher.findInstance(instanceName);
        ContentManifestStore.Manifest manifest = launcher.contentManifests().read(instance.slug());

        String version = packVersion == null || packVersion.isBlank() ? "1.0.0" : packVersion.trim();
        Path target = AppPaths.exportsDirectory().resolve(instance.slug() + "-" + sanitize(version) + format.extension);
        Files.createDirectories(target.getParent());

        launcher.busyRegistry().begin(instance.name(), "exporting");
        try {
            return format == Format.MRPACK
                    ? writeMrpack(instance, manifest, version, target)
                    : writeCurseForgePack(instance, manifest, version, target);
        } finally {
            launcher.busyRegistry().end(instance.name());
        }
    }

    // ── Modrinth .mrpack ──────────────────────────────────────────────────────

    private ExportResult writeMrpack(Instance instance, ContentManifestStore.Manifest manifest,
                                     String version, Path target) throws Exception {
        JSONArray files = new JSONArray();
        Set<String> referenced = new HashSet<>();
        List<String> notes = new ArrayList<>();

        for (Installed installed : installedFiles(instance)) {
            ContentSource source = manifest.find(installed.contentType(), installed.fileName());
            // Only Modrinth-hosted files may be referenced: the format's consumers validate the
            // download host, so a CurseForge CDN link would make the pack unimportable.
            if (source == null || !ProviderId.MODRINTH.id().equals(source.provider())
                    || source.downloadUrl().isBlank()) {
                continue;
            }

            String relative = installed.contentType() + "/" + installed.fileName();
            files.put(new JSONObject()
                    .put("path", relative)
                    .put("hashes", new JSONObject()
                            .put("sha1", hash(installed.path(), "SHA-1"))
                            .put("sha512", hash(installed.path(), "SHA-512")))
                    .put("env", new JSONObject().put("client", "required").put("server", "required"))
                    .put("downloads", new JSONArray().put(source.downloadUrl()))
                    .put("fileSize", Files.size(installed.path())));
            referenced.add(relative);
        }

        JSONObject index = new JSONObject()
                .put("formatVersion", 1)
                .put("game", "minecraft")
                .put("versionId", version)
                .put("name", instance.name())
                .put("summary", "Exported from VoltLauncher")
                .put("files", files)
                .put("dependencies", mrpackDependencies(instance));

        int bundled;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            writeEntry(zip, "modrinth.index.json", index.toString(2).getBytes(StandardCharsets.UTF_8));
            bundled = writeOverrides(zip, instance, referenced);
        }

        if (bundled > 0) {
            notes.add(bundled + " file(s) are not on Modrinth and were bundled into overrides/ instead.");
        }
        return new ExportResult(target, files.length(), bundled, notes);
    }

    private JSONObject mrpackDependencies(Instance instance) {
        JSONObject dependencies = new JSONObject()
                .put("minecraft", PlatformRegistry.extractBaseMinecraftVersionId(instance.versionId()));

        String loaderVersion = PlatformRegistry.extractLoaderVersion(instance.versionId());
        String loader = loaderOf(instance);
        if (loader == null || loaderVersion.isBlank()) return dependencies;

        return switch (loader) {
            case PlatformRegistry.FABRIC_ID -> dependencies.put("fabric-loader", loaderVersion);
            case PlatformRegistry.QUILT_ID -> dependencies.put("quilt-loader", loaderVersion);
            case PlatformRegistry.FORGE_ID -> dependencies.put("forge", loaderVersion);
            case PlatformRegistry.NEOFORGE_ID -> dependencies.put("neoforge", loaderVersion);
            default -> dependencies;
        };
    }

    // ── CurseForge .zip ───────────────────────────────────────────────────────

    private ExportResult writeCurseForgePack(Instance instance, ContentManifestStore.Manifest manifest,
                                             String version, Path target) throws Exception {
        JSONArray files = new JSONArray();
        Set<String> referenced = new HashSet<>();
        List<String> notes = new ArrayList<>();

        for (Installed installed : installedFiles(instance)) {
            ContentSource source = manifest.find(installed.contentType(), installed.fileName());
            if (source == null || !ProviderId.CURSEFORGE.id().equals(source.provider())) continue;

            // CurseForge addresses files by numeric id; anything else cannot go in the manifest.
            Integer projectId = asInt(source.projectId());
            Integer fileId = asInt(source.versionId());
            if (projectId == null || fileId == null) continue;

            files.put(new JSONObject()
                    .put("projectID", projectId.intValue())
                    .put("fileID", fileId.intValue())
                    .put("required", true));
            referenced.add(installed.contentType() + "/" + installed.fileName());
        }

        JSONObject manifestJson = new JSONObject()
                .put("minecraft", new JSONObject()
                        .put("version", PlatformRegistry.extractBaseMinecraftVersionId(instance.versionId()))
                        .put("modLoaders", curseForgeLoaders(instance)))
                .put("manifestType", "minecraftModpack")
                .put("manifestVersion", 1)
                .put("name", instance.name())
                .put("version", version)
                .put("author", "")
                .put("files", files)
                .put("overrides", "overrides");

        int bundled;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            writeEntry(zip, "manifest.json", manifestJson.toString(2).getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "modlist.html", modList(manifest).getBytes(StandardCharsets.UTF_8));
            bundled = writeOverrides(zip, instance, referenced);
        }

        if (bundled > 0) {
            notes.add(bundled + " file(s) are not on CurseForge and were bundled into overrides/ instead.");
        }
        return new ExportResult(target, files.length(), bundled, notes);
    }

    private JSONArray curseForgeLoaders(Instance instance) {
        JSONArray loaders = new JSONArray();
        String loader = loaderOf(instance);
        String loaderVersion = PlatformRegistry.extractLoaderVersion(instance.versionId());
        if (loader != null && !loaderVersion.isBlank()) {
            loaders.put(new JSONObject().put("id", loader + "-" + loaderVersion).put("primary", true));
        }
        return loaders;
    }

    private String modList(ContentManifestStore.Manifest manifest) {
        StringBuilder html = new StringBuilder("<ul>\n");
        for (ContentSource source : manifest.content()) {
            if (!"mods".equals(source.contentType())) continue;
            String label = source.projectName().isBlank() ? source.fileName() : source.projectName();
            html.append("<li>").append(escapeHtml(label));
            if (!source.versionNumber().isBlank()) {
                html.append(" (").append(escapeHtml(source.versionNumber())).append(')');
            }
            html.append("</li>\n");
        }
        return html.append("</ul>\n").toString();
    }

    // ── shared ────────────────────────────────────────────────────────────────

    private record Installed(String contentType, String fileName, Path path) {}

    /** Every enabled content file on disk. Disabled files are deliberately left out of exports. */
    private List<Installed> installedFiles(Instance instance) throws Exception {
        List<Installed> result = new ArrayList<>();
        for (InstanceContentService.ContentType type : InstanceContentService.ContentType.values()) {
            Path dir = instance.gameDirectory().resolve(type.folder());
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().endsWith(".disabled"))
                        .forEach(p -> result.add(new Installed(type.folder(), p.getFileName().toString(), p)));
            }
        }
        return result;
    }

    /**
     * Writes everything the manifest could not reference into {@code overrides/}: the content files
     * left over, plus the configuration directories a pack needs to behave the same on import.
     *
     * @return how many content files had to be bundled
     */
    private int writeOverrides(ZipOutputStream zip, Instance instance, Set<String> referenced) throws Exception {
        int bundled = 0;
        for (Installed installed : installedFiles(instance)) {
            String relative = installed.contentType() + "/" + installed.fileName();
            if (referenced.contains(relative)) continue;
            writeEntry(zip, "overrides/" + relative, installed.path());
            bundled++;
        }

        Path gameDir = instance.gameDirectory();
        for (String directory : EXTRA_DIRECTORIES) {
            Path dir = gameDir.resolve(directory);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    writeEntry(zip, "overrides/" + slashes(gameDir.relativize(file)), file);
                }
            }
        }
        for (String fileName : EXTRA_FILES) {
            Path file = gameDir.resolve(fileName);
            if (Files.isRegularFile(file)) writeEntry(zip, "overrides/" + fileName, file);
        }
        return bundled;
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private void writeEntry(ZipOutputStream zip, String name, Path source) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream in = Files.newInputStream(source)) {
            in.transferTo((OutputStream) zip);
        }
        zip.closeEntry();
    }

    private String hash(Path file, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65_536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private String loaderOf(Instance instance) {
        String versionId = instance.versionId();
        if (versionId == null) return null;
        int separator = versionId.indexOf(':');
        if (separator <= 0) return null;
        String loader = versionId.substring(0, separator).toLowerCase(Locale.ROOT);
        return PlatformRegistry.VANILLA_ID.equals(loader) ? null : loader;
    }

    private Integer asInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String slashes(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private String sanitize(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "-");
        return cleaned.isBlank() ? "export" : cleaned;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
