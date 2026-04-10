package app.voltlauncher.voltlauncher.launcher.platform.version.runner;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NeoForgeProcessorRunner {

    private static final String VAR_LIBRARY_DIR = "LIBRARY_DIR";
    private static final String VAR_INSTALLER = "INSTALLER";
    private static final String VAR_MINECRAFT_JAR = "MINECRAFT_JAR";

    private final JSONObject installProfile;
    private final Path installerJar;
    private final Path librariesDir;
    private final Path minecraftClientJar;
    private final String javaExecutable;

    public NeoForgeProcessorRunner(JSONObject installProfile, Path installerJar, Path librariesDir, Path minecraftClientJar, String javaExecutable) {
        this.installProfile = installProfile;
        this.installerJar = installerJar;
        this.librariesDir = librariesDir;
        this.minecraftClientJar = minecraftClientJar;
        this.javaExecutable = javaExecutable;
    }

    public void runIfNeeded() throws Exception {
        JSONArray processors = installProfile.optJSONArray("processors");
        if (processors == null || processors.isEmpty()) {
            return;
        }

        for (int i = 0; i < processors.length(); i++) {
            JSONObject proc = processors.getJSONObject(i);

            JSONArray sides = proc.optJSONArray("sides");
            if (sides != null && !containsSide(sides, "client")) {
                continue;
            }

            if (isProcessorDone(proc)) {
                System.out.printf("[NeoForge] Skipping processor %d/%d (outputs already valid)%n", i + 1, processors.length());
                continue;
            }

            System.out.printf("[NeoForge] Running processor %d/%d: %s%n", i + 1, processors.length(), proc.getString("jar"));
            runProcessor(proc);
        }
    }

    private boolean isProcessorDone(JSONObject proc) {
        JSONObject outputs = proc.optJSONObject("outputs");
        if (outputs == null || outputs.isEmpty()) {
            return false;
        }

        for (String outputToken : outputs.keySet()) {
            String resolvedPath = resolveToken(outputToken);
            String expectedHash = resolveToken(outputs.getString(outputToken));
            Path target = Path.of(resolvedPath);

            if (!Files.exists(target)) {
                return false;
            }

            if (!expectedHash.isBlank() && !checkSha1(target, expectedHash)) {
                return false;
            }
        }

        return true;
    }

    private void runProcessor(JSONObject proc) throws Exception {
        Path processorJar = resolveGav(proc.getString("jar"));
        String mainClass = readMainClass(processorJar);

        List<String> classpath = new ArrayList<>();
        classpath.add(processorJar.toAbsolutePath().toString());

        JSONArray cpArr = proc.optJSONArray("classpath");
        if (cpArr != null) {
            for (int i = 0; i < cpArr.length(); i++) {
                classpath.add(resolveGav(cpArr.getString(i)).toAbsolutePath().toString());
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable);
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, classpath));
        cmd.add(mainClass);

        JSONArray argsArr = proc.optJSONArray("args");
        if (argsArr != null) {
            for (int i = 0; i < argsArr.length(); i++) {
                cmd.add(resolveToken(argsArr.getString(i)));
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IllegalStateException(
                    "NeoForge processor exited with code " + exitCode
                            + " (jar=" + proc.getString("jar") + "):\n" + output
            );
        }
    }

    private String resolveToken(String token) {
        if (token == null) {
            return "";
        }

        if (token.startsWith("{") && token.endsWith("}")) {
            String key = token.substring(1, token.length() - 1);
            return resolveDataKey(key);
        }

        if (token.startsWith("[") && token.endsWith("]")) {
            return resolveGav(token.substring(1, token.length() - 1)).toAbsolutePath().toString();
        }

        return token;
    }

    private String resolveDataKey(String key) {
        if (VAR_LIBRARY_DIR.equals(key)) {
            return librariesDir.toAbsolutePath().toString();
        }
        if (VAR_INSTALLER.equals(key)) {
            return installerJar.toAbsolutePath().toString();
        }
        if (VAR_MINECRAFT_JAR.equals(key)) {
            return minecraftClientJar.toAbsolutePath().toString();
        }

        JSONObject dataMap = installProfile.optJSONObject("data");
        if (dataMap == null || !dataMap.has(key)) {
            System.err.println("[NeoForge] Unknown data key: " + key);
            return "{" + key + "}";
        }

        String raw;
        JSONObject entry = dataMap.optJSONObject(key);
        if (entry != null) {
            raw = entry.optString("client", "");
        } else {
            raw = dataMap.optString(key, "");
        }

        if (raw.startsWith("[") && raw.endsWith("]")) {
            return resolveGav(raw.substring(1, raw.length() - 1)).toAbsolutePath().toString();
        }

        if (raw.startsWith("/")) {
            return extractFromInstaller(raw).toAbsolutePath().toString();
        }

        return raw;
    }

    private Path resolveGav(String gav) {
        String[] parts = gav.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven GAV: " + gav);
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? parts[3] : "";
        String ext = "jar";

        int atSign = classifier.indexOf('@');
        if (atSign >= 0) {
            ext = classifier.substring(atSign + 1);
            classifier = classifier.substring(0, atSign);
        }

        String fileName = artifact + "-" + version
                + (classifier.isBlank() ? "" : "-" + classifier)
                + "." + ext;

        String relPath = group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName;
        return librariesDir.resolve(relPath);
    }

    private Path extractFromInstaller(String installerEntryPath) {
        try {
            String entryName = installerEntryPath.startsWith("/") ? installerEntryPath.substring(1) : installerEntryPath;

            String safeName = entryName.replace('/', '_').replace('\\', '_');
            Path extractDir = installerJar.getParent().resolve("extracted");
            Files.createDirectories(extractDir);
            Path dest = extractDir.resolve(safeName);

            if (Files.exists(dest)) {
                return dest;
            }

            try (ZipFile zip = new ZipFile(installerJar.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    throw new IllegalStateException(
                            "Entry '" + entryName + "' not found in installer JAR: " + installerJar
                    );
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            return dest;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract '" + installerEntryPath + "' from installer JAR", e);
        }
    }

    private String readMainClass(Path jar) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile())) {
            java.util.jar.Manifest manifest = jf.getManifest();
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
            if (side.equalsIgnoreCase(sides.getString(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSha1(Path file, String expectedHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65_536];
                int n;
                while ((n = in.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            byte[] raw = md.digest();
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(expectedHex);
        } catch (Exception e) {
            return false;
        }
    }
}