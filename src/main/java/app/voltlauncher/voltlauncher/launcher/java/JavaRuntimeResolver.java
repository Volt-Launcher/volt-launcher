package app.voltlauncher.voltlauncher.launcher.java;

import app.voltlauncher.voltlauncher.AppPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class JavaRuntimeResolver {

    public record JavaRuntime(int majorVersion, Path javaExecutable, String source) {}

    private static final Pattern VERSION_PATTERN = Pattern.compile("version \"([^\"]+)\"");

    private final ConcurrentHashMap<Path, Integer> detectedMajorVersions = new ConcurrentHashMap<>();
    private final TemurinJdkDownloader temurinJdkDownloader = new TemurinJdkDownloader();

    public JavaRuntime resolveRuntime(int requiredMajorVersion) throws Exception {
        List<JavaRuntime> candidates = discoverRuntimes(requiredMajorVersion);
        for (JavaRuntime candidate : candidates) {
            if (candidate.majorVersion() == requiredMajorVersion) {
                return candidate;
            }
        }
        Path downloadedRuntime = temurinJdkDownloader.ensureDownloadedRuntime(requiredMajorVersion);
        return new JavaRuntime(requiredMajorVersion, downloadedRuntime, "temurin-downloaded");
    }

    private List<JavaRuntime> discoverRuntimes(int requiredMajorVersion) throws Exception {
        List<JavaRuntime> runtimes = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();

        for (String envVar : new String[]{
                "JAVA_" + requiredMajorVersion + "_HOME",
                "JDK_" + requiredMajorVersion + "_HOME",
                "JRE_" + requiredMajorVersion + "_HOME"}) {
            addHomeCandidate(runtimes, seen, System.getenv(envVar), envVar);
        }
        addHomeCandidate(runtimes, seen, System.getProperty("java.home"), "java.home");
        addHomeCandidate(runtimes, seen, System.getenv("JAVA_HOME"), "JAVA_HOME");

        for (Path p : discoverManagedRuntimeCandidates()) {
            addExecutableCandidate(runtimes, seen, p, "managed-runtime");
        }
        for (Path p : discoverCommandCandidates()) {
            addExecutableCandidate(runtimes, seen, p, "PATH");
        }
        for (Path p : discoverCommonInstallCandidates()) {
            addExecutableCandidate(runtimes, seen, p, "standard-location");
        }
        return runtimes;
    }

    private void addHomeCandidate(
            List<JavaRuntime> runtimes, Set<Path> seen, String home, String source) throws Exception {
        if (home == null || home.isBlank()) {
            return;
        }
        addExecutableCandidate(runtimes, seen, resolveJavaExecutable(Path.of(home)), source);
    }

    private void addExecutableCandidate(
            List<JavaRuntime> runtimes, Set<Path> seen, Path executable, String source) throws Exception {
        if (executable == null || !Files.exists(executable)) {
            return;
        }
        Path normalized = executable.toAbsolutePath().normalize();
        if (!seen.add(normalized)) {
            return;
        }
        try {
            if (!Files.isExecutable(normalized)) {
                return;
            }
            runtimes.add(new JavaRuntime(detectMajorVersion(normalized), normalized, source));
        } catch (IOException _) {}
    }

    private Path resolveJavaExecutable(Path javaHome) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
    }

    private List<Path> discoverCommandCandidates() {
        List<Path> result = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = windows
                ? List.of("cmd", "/c", "where java")
                : List.of("bash", "-lc", "which -a java");
        try {
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank()) {
                        result.add(Path.of(trimmed));
                    }
                }
            }
            process.waitFor();
        } catch (Exception _) {}
        return result;
    }

    private List<Path> discoverManagedRuntimeCandidates() {
        List<Path> result = new ArrayList<>();
        Path runtimesDir = AppPaths.temurinRuntimesDirectory();
        if (!Files.isDirectory(runtimesDir)) {
            return result;
        }
        String execName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        try (Stream<Path> stream = Files.list(runtimesDir)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().endsWith(".tmp"))
                    .map(p -> p.resolve("bin").resolve(execName))
                    .filter(Files::exists)
                    .forEach(result::add);
        } catch (IOException _) {}
        return result;
    }

    private List<Path> discoverCommonInstallCandidates() {
        List<Path> result = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            addExecutablesInDirectory(result, Path.of("C:/Program Files/Java"), false);
            addExecutablesInDirectory(result, Path.of("C:/Program Files/Eclipse Adoptium"), false);
            addExecutablesInDirectory(result, Path.of("C:/Program Files/Microsoft"), false);
            addExecutablesInDirectory(result, Path.of("C:/Program Files/Azul"), false);
            return result;
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            addExecutablesInDirectory(result, Path.of("/Library/Java/JavaVirtualMachines"), true);
            addExecutablesInDirectory(result, Path.of("/System/Library/Java/JavaVirtualMachines"), true);
            return result;
        }
        addExecutablesInDirectory(result, Path.of("/usr/lib/jvm"), false);
        addExecutablesInDirectory(result, Path.of("/usr/java"), false);
        addExecutablesInDirectory(result, Path.of("/opt/java"), false);
        addExecutablesInDirectory(result, Path.of("/opt/jdks"), false);
        return result;
    }

    private void addExecutablesInDirectory(List<Path> target, Path base, boolean macBundle) {
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                Path exec = macBundle
                        ? dir.resolve("Contents/Home/bin/java")
                        : resolveJavaExecutable(dir);
                if (Files.exists(exec)) {
                    target.add(exec);
                }
            });
        } catch (IOException _) {}
    }

    private int detectMajorVersion(Path javaExecutable) throws Exception {
        Integer cached = detectedMajorVersions.get(javaExecutable);
        if (cached != null) {
            return cached;
        }
        StringBuilder output = new StringBuilder();
        Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
                .redirectErrorStream(true)
                .start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        process.waitFor();
        int major = parseMajorVersion(output.toString());
        detectedMajorVersions.put(javaExecutable, major);
        return major;
    }

    private int parseMajorVersion(String output) {
        Matcher matcher = VERSION_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not determine Java version from: " + output);
        }
        String version = matcher.group(1);
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (!Character.isDigit(c)) {
                break;
            }
            digits.append(c);
        }
        if (!digits.isEmpty()) {
            return Integer.parseInt(digits.toString());
        }
        throw new IllegalStateException("Could not parse Java major version from: " + version);
    }
}