package app.voltlauncher.game.platform.version.processor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Executes the one-time post-install steps declared in a Forge or NeoForge
 * {@code install_profile.json}: deobfuscating, remapping and binary-patching the vanilla client
 * into the artifacts the loader's own libraries expect to find on disk.
 *
 * <p>Processors are idempotent — each declares the hashes of the files it produces, so a run that
 * is already complete is skipped. This matters because the whole sequence takes minutes.
 */
public final class ForgeProcessorRunner {

    /** Placeholders resolved from the runner rather than the profile's own data map. */
    private static final String VAR_LIBRARY_DIR = "LIBRARY_DIR";
    private static final String VAR_INSTALLER = "INSTALLER";
    private static final String VAR_MINECRAFT_JAR = "MINECRAFT_JAR";
    private static final String VAR_SIDE = "SIDE";
    private static final String VAR_ROOT = "ROOT";

    private static final Pattern DATA_TOKEN = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern GAV_TOKEN = Pattern.compile("\\[([^\\[\\]]+)]");

    /** Remapping large jars is memory hungry; the default heap is not always enough. */
    private static final String PROCESSOR_HEAP = "-Xmx2G";
    private static final long PROCESSOR_TIMEOUT_MINUTES = 15;

    private final JSONObject installProfile;
    private final Path installerJar;
    private final Path librariesDir;
    private final Path minecraftClientJar;
    private final String javaExecutable;

    public ForgeProcessorRunner(JSONObject installProfile, Path installerJar, Path librariesDir,
                                Path minecraftClientJar, String javaExecutable) {
        this.installProfile = installProfile;
        this.installerJar = installerJar;
        this.librariesDir = librariesDir;
        this.minecraftClientJar = minecraftClientJar;
        this.javaExecutable = javaExecutable;
    }

    public void runIfNeeded() throws Exception {
        JSONArray processors = installProfile.optJSONArray("processors");
        if (processors == null || processors.isEmpty()) return;

        for (int i = 0; i < processors.length(); i++) {
            JSONObject processor = processors.getJSONObject(i);

            JSONArray sides = processor.optJSONArray("sides");
            if (sides != null && !containsSide(sides, "client")) continue;

            if (isProcessorDone(processor)) {
                System.out.printf("[Forge] Processor %d/%d already applied%n", i + 1, processors.length());
                continue;
            }

            System.out.printf("[Forge] Running processor %d/%d: %s%n",
                    i + 1, processors.length(), processor.getString("jar"));
            runProcessor(processor);
        }
    }

    /** A processor is complete when every declared output exists and matches its expected hash. */
    private boolean isProcessorDone(JSONObject processor) {
        JSONObject outputs = processor.optJSONObject("outputs");
        if (outputs == null || outputs.isEmpty()) return false;

        for (String outputToken : outputs.keySet()) {
            Path target = Path.of(resolveToken(outputToken));
            if (!Files.exists(target)) return false;

            String expectedHash = resolveToken(outputs.getString(outputToken));
            if (!expectedHash.isBlank() && !sha1Matches(target, expectedHash)) return false;
        }
        return true;
    }

    private void runProcessor(JSONObject processor) throws Exception {
        String jarCoordinate = processor.getString("jar");
        Path processorJar = resolveGav(jarCoordinate);
        if (!Files.exists(processorJar)) {
            throw new IllegalStateException("Processor JAR was not installed: " + jarCoordinate);
        }

        boolean fatJar = jarCoordinate.endsWith(":all") || jarCoordinate.endsWith(":fatjar");
        List<String> classpath = new ArrayList<>();
        classpath.add(processorJar.toAbsolutePath().toString());

        JSONArray declaredClasspath = processor.optJSONArray("classpath");
        if (declaredClasspath != null) {
            for (int i = 0; i < declaredClasspath.length(); i++) {
                String dependency = declaredClasspath.getString(i);
                // Shaded tools such as ForgeAutoRenamingTool:all already bundle ASM; adding it
                // again splits the package across the classpath and breaks the tool at startup.
                if (fatJar && dependency.startsWith("org.ow2.asm:")) continue;
                classpath.add(resolveGav(dependency).toAbsolutePath().toString());
            }
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add(PROCESSOR_HEAP);
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(readMainClass(processorJar));

        JSONArray args = processor.optJSONArray("args");
        if (args != null) {
            for (int i = 0; i < args.length(); i++) {
                command.add(resolveToken(args.getString(i)));
            }
        }

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes());
        }
        if (!process.waitFor(PROCESSOR_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Mod loader processor timed out after " + PROCESSOR_TIMEOUT_MINUTES + " minutes (jar="
                            + jarCoordinate + ")");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Mod loader processor failed with exit code " + process.exitValue()
                            + " (jar=" + jarCoordinate + ", java=" + javaExecutable + "):\n" + output);
        }
    }

    /**
     * Expands every {@code {DATA_KEY}} and {@code [maven:coordinate]} placeholder in a value,
     * including ones embedded in a longer string such as {@code --output={PATCHED}}.
     */
    private String resolveToken(String token) {
        if (token == null || token.isBlank()) return "";

        Matcher gav = GAV_TOKEN.matcher(token);
        StringBuilder afterGav = new StringBuilder();
        while (gav.find()) {
            gav.appendReplacement(afterGav,
                    Matcher.quoteReplacement(resolveGav(gav.group(1)).toAbsolutePath().toString()));
        }
        gav.appendTail(afterGav);

        Matcher data = DATA_TOKEN.matcher(afterGav.toString());
        StringBuilder result = new StringBuilder();
        while (data.find()) {
            data.appendReplacement(result, Matcher.quoteReplacement(resolveDataKey(data.group(1))));
        }
        data.appendTail(result);
        return result.toString();
    }

    private String resolveDataKey(String key) {
        switch (key) {
            case VAR_LIBRARY_DIR -> { return librariesDir.toAbsolutePath().toString(); }
            case VAR_INSTALLER -> { return installerJar.toAbsolutePath().toString(); }
            case VAR_MINECRAFT_JAR -> { return minecraftClientJar.toAbsolutePath().toString(); }
            // This runner only ever installs the client side.
            case VAR_SIDE -> { return "client"; }
            case VAR_ROOT -> {
                Path root = librariesDir.getParent();
                return (root != null ? root : librariesDir).toAbsolutePath().toString();
            }
            default -> { /* fall through to the profile's data map */ }
        }

        JSONObject dataMap = installProfile.optJSONObject("data");
        if (dataMap == null || !dataMap.has(key)) {
            System.err.println("[Forge] Unknown install profile data key: " + key);
            return "{" + key + "}";
        }

        JSONObject entry = dataMap.optJSONObject(key);
        String raw = entry != null ? entry.optString("client", "") : dataMap.optString(key, "");

        if (raw.startsWith("[") && raw.endsWith("]")) {
            return resolveGav(raw.substring(1, raw.length() - 1)).toAbsolutePath().toString();
        }
        // A leading slash refers to a file packaged inside the installer JAR itself.
        if (raw.startsWith("/")) {
            return extractFromInstaller(raw).toAbsolutePath().toString();
        }
        return raw;
    }

    private Path resolveGav(String gav) {
        String[] parts = gav.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + gav);
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? parts[3] : "";
        String extension = "jar";

        int versionAt = version.indexOf('@');
        if (versionAt >= 0) {
            String ext = version.substring(versionAt + 1).trim();
            if (!ext.isBlank()) extension = ext;
            version = version.substring(0, versionAt);
        }
        int classifierAt = classifier.indexOf('@');
        if (classifierAt >= 0) {
            String ext = classifier.substring(classifierAt + 1).trim();
            if (!ext.isBlank()) extension = ext;
            classifier = classifier.substring(0, classifierAt);
        }

        String fileName = artifact + "-" + version + (classifier.isBlank() ? "" : "-" + classifier) + "." + extension;
        return librariesDir.resolve(group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName);
    }

    private Path extractFromInstaller(String installerEntryPath) {
        try {
            String entryName = installerEntryPath.startsWith("/")
                    ? installerEntryPath.substring(1)
                    : installerEntryPath;

            Path extractDir = installerJar.getParent()
                    .resolve("extracted")
                    .resolve(installerJar.getFileName().toString());
            Files.createDirectories(extractDir);
            Path destination = extractDir.resolve(entryName.replace('/', '_').replace('\\', '_'));
            if (Files.exists(destination)) return destination;

            try (ZipFile zip = new ZipFile(installerJar.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new IllegalStateException(
                            "Entry '" + entryName + "' not found in installer JAR: " + installerJar);
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return destination;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract '" + installerEntryPath + "' from the installer JAR", e);
        }
    }

    private String readMainClass(Path jar) throws Exception {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new IllegalStateException("No MANIFEST.MF in processor JAR: " + jar);
            }
            String mainClass = manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass == null || mainClass.isBlank()) {
                throw new IllegalStateException("No Main-Class attribute in processor JAR: " + jar);
            }
            return mainClass.trim();
        }
    }

    private boolean containsSide(JSONArray sides, String side) {
        for (int i = 0; i < sides.length(); i++) {
            if (side.equalsIgnoreCase(sides.getString(i))) return true;
        }
        return false;
    }

    private boolean sha1Matches(Path file, String expectedHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65_536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHex);
        } catch (Exception e) {
            return false;
        }
    }
}
