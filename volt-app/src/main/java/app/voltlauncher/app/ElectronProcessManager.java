package app.voltlauncher.app;

import app.voltlauncher.core.AppPaths;

import java.nio.file.Files;
import java.nio.file.Path;

final class ElectronProcessManager {

    private Process process;
    private final Runnable onExit;

    ElectronProcessManager(Runnable onExit) {
        this.onExit = onExit;
    }

    void start() {
        try {
            Path binaryPath = resolveBinary();
            if (binaryPath == null) {
                System.err.println("[Launcher] ERROR: Electron binary not found");
                onExit.run();
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(binaryPath.toString());
            pb.directory(resolveBaseDir().toFile());
            pb.inheritIO();

            process = pb.start();
            process.onExit().thenRun(() -> {
                System.out.println("[Launcher] Electron process exited. Shutting down backend...");
                onExit.run();
            });
        } catch (Exception e) {
            System.err.println("[Launcher] Could not start Electron process: " + e.getMessage());
            onExit.run();
        }
    }

    void stop() {
        if (process != null) process.destroy();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Path resolveBinary() {
        Path base = resolveBaseDir();
        String osDir = osDir();
        String executable = executableName();
        Path primary = base.resolve(osDir).resolve(executable);
        if (Files.exists(primary)) return primary;

        // Fallback to lower-cased names
        String fallback = fallbackExecutableName();
        Path secondary = base.resolve(osDir).resolve(fallback);
        return Files.exists(secondary) ? secondary : null;
    }

    private Path resolveBaseDir() {
        return ProcessHandle.current().info().command()
                .map(cmd -> Path.of(cmd).toAbsolutePath().getParent().resolve("electron"))
                .orElseGet(() -> AppPaths.baseDirectory().resolve("electron"));
    }

    private String osDir() {
        if (isMac()) return isArm() ? "mac-arm64" : "mac";
        if (isLinux()) return "linux-unpacked";
        return "win-unpacked";
    }

    private String executableName() {
        if (isMac()) return "VoltLauncher.app/Contents/MacOS/VoltLauncher";
        if (isLinux()) return "voltlauncher";
        return "VoltLauncher.exe";
    }

    private String fallbackExecutableName() {
        if (isMac()) return "voltlauncher-ui.app/Contents/MacOS/voltlauncher-ui";
        if (isLinux()) return "voltlauncher-ui";
        return "voltlauncher-ui.exe";
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static boolean isArm() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.contains("aarch64") || arch.contains("arm");
    }
}
