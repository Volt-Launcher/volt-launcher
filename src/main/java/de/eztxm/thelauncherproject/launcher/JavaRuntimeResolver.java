package de.eztxm.thelauncherproject.launcher;

import de.eztxm.thelauncherproject.AppPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JavaRuntimeResolver {

    public record JavaRuntime(int majorVersion, Path javaExecutable, String source) {
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("version \"([^\"]+)\"");

    private final Map<Path, Integer> detectedMajorVersions = new ConcurrentHashMap<>();
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
        Set<Path> seenExecutables = new LinkedHashSet<>();

        String[] preferredEnvVars = {
                "JAVA_" + requiredMajorVersion + "_HOME",
                "JDK_" + requiredMajorVersion + "_HOME",
                "JRE_" + requiredMajorVersion + "_HOME"
        };

        for (String envVar : preferredEnvVars) {
            addHomeCandidate(runtimes, seenExecutables, System.getenv(envVar), envVar);
        }

        addHomeCandidate(runtimes, seenExecutables, System.getProperty("java.home"), "java.home");
        addHomeCandidate(runtimes, seenExecutables, System.getenv("JAVA_HOME"), "JAVA_HOME");

        for (Path executable : discoverManagedRuntimeCandidates()) {
            addExecutableCandidate(runtimes, seenExecutables, executable, "managed-runtime");
        }

        for (Path executable : discoverCommandCandidates()) {
            addExecutableCandidate(runtimes, seenExecutables, executable, "PATH");
        }

        for (Path executable : discoverCommonInstallCandidates()) {
            addExecutableCandidate(runtimes, seenExecutables, executable, "standard-location");
        }

        return runtimes;
    }

    private void addHomeCandidate(
            List<JavaRuntime> runtimes,
            Set<Path> seenExecutables,
            String home,
            String source) throws Exception {
        if (home == null || home.isBlank()) {
            return;
        }

        Path executable = resolveJavaExecutable(Path.of(home));
        addExecutableCandidate(runtimes, seenExecutables, executable, source);
    }

    private void addExecutableCandidate(
            List<JavaRuntime> runtimes,
            Set<Path> seenExecutables,
            Path executable,
            String source) throws Exception {
        if (executable == null || !Files.exists(executable)) {
            return;
        }

        Path normalized = executable.toAbsolutePath().normalize();
        if (!seenExecutables.add(normalized)) {
            return;
        }

        try {
            if (!Files.isExecutable(normalized)) {
                return;
            }
            runtimes.add(new JavaRuntime(detectMajorVersion(normalized), normalized, source));
        } catch (IOException e) {
            return;
        }
    }

    private Path resolveJavaExecutable(Path javaHome) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
    }

    private List<Path> discoverCommandCandidates() {
        List<Path> result = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = osName.contains("win")
                ? List.of("cmd", "/c", "where java")
                : List.of("bash", "-lc", "which -a java");

        try {
            Process process = new ProcessBuilder(command).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank()) {
                        result.add(Path.of(trimmed));
                    }
                }
                process.waitFor();
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private List<Path> discoverManagedRuntimeCandidates() {
        List<Path> result = new ArrayList<>();
        Path runtimesDirectory = AppPaths.temurinRuntimesDirectory();
        if (!Files.isDirectory(runtimesDirectory)) {
            return result;
        }

        String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";

        try (Stream<Path> stream = Files.list(runtimesDirectory)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                    .map(path -> path.resolve("bin").resolve(executableName))
                    .filter(Files::exists)
                    .forEach(result::add);
        } catch (IOException ignored) {
        }

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

    private void addExecutablesInDirectory(List<Path> target, Path baseDirectory, boolean macBundleLayout) {
        if (!Files.isDirectory(baseDirectory)) {
            return;
        }

        try (Stream<Path> stream = Files.list(baseDirectory)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                Path executable = macBundleLayout
                        ? path.resolve("Contents/Home").resolve("bin/java")
                        : resolveJavaExecutable(path);
                if (Files.exists(executable)) {
                    target.add(executable);
                }
            });
        } catch (IOException ignored) {
        }
    }

    private int detectMajorVersion(Path javaExecutable) throws Exception {
        Integer cached = detectedMajorVersions.get(javaExecutable);
        if (cached != null) {
            return cached;
        }

        StringBuilder output = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder(javaExecutable.toString(), "-version");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            process.waitFor();
        }

        int majorVersion = parseMajorVersion(output.toString());
        detectedMajorVersions.put(javaExecutable, majorVersion);
        return majorVersion;
    }

    private int parseMajorVersion(String output) {
        Matcher matcher = VERSION_PATTERN.matcher(output);
        if (matcher.find()) {
            String version = matcher.group(1);
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2, 3));
            }

            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < version.length(); i++) {
                char current = version.charAt(i);
                if (Character.isDigit(current)) {
                    digits.append(current);
                } else {
                    break;
                }
            }
            if (!digits.isEmpty()) {
                return Integer.parseInt(digits.toString());
            }
        }

        throw new IllegalStateException("Could not determine Java version from output: " + output);
    }
}



