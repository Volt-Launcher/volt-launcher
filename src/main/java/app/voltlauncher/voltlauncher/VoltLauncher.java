package app.voltlauncher.voltlauncher;

import app.voltlauncher.voltlauncher.auth.OAuthClient;
import app.voltlauncher.voltlauncher.rest.RestServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class VoltLauncher {

    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);
    private static final Object DIAG_LOCK = new Object();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static volatile RestServer restServer;
    private static Process electronProcess;

    public static void main(String[] args) {
        installDiagnostics();
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer( 7070, VoltLauncher::minimizeMainWindow, VoltLauncher::toggleMaximizeMainWindow, VoltLauncher::requestCloseMainWindow, url -> {} );
        restServer.start();

        System.out.println("[Launcher] Starte Electron Frontend...");
        startElectronProcess();
    }

    private static void startElectronProcess() {
        try {
            // Find bundled electron binary from resources if packaged, or use npm run if dev
            String osDir = "";
            String executable = "";
            if (isMac()) {
                // Determine arch logic as needed, assuming arm64 or x64.
                // In electron-builder "dir" output, mac is mac-arm64 or mac
                osDir = isArm() ? "mac-arm64" : "mac";
                executable = "VoltLauncherUI.app/Contents/MacOS/VoltLauncherUI";
            } else if (isLinux()) {
                osDir = "linux-unpacked";
                executable = "voltlauncherui";
            } else {
                osDir = "win-unpacked";
                executable = "VoltLauncherUI.exe";
            }

            Path devUiDir = Path.of(System.getProperty("user.dir"), "ui");
            ProcessBuilder pb;
            if (Files.exists(devUiDir)) {
                // Development mode
                if (isMac() || isLinux()) {
                    pb = new ProcessBuilder("npm", "run", "electron:start");
                } else {
                    pb = new ProcessBuilder("cmd.exe", "/c", "npm run electron:start");
                }
                pb.directory(devUiDir.toFile());
            } else {
                // Prod mode (running from Jar) - extracting or running from resources
                // Typically you need to extract the electron-bin directory from the JAR to a temp location first
                // Let's assume we extract it to AppPaths.baseDirectory().resolve("electron")
                Path electronBaseDir = app.voltlauncher.voltlauncher.AppPaths.baseDirectory().resolve("electron");

                // For simplicity, assuming the deployment mechanism copies it there.
                // Alternatively, run the specific binary:
                Path binaryPath = electronBaseDir.resolve(osDir).resolve(executable);

                if (!Files.exists(binaryPath)) {
                    System.err.println("[Launcher] Electron Binary not found at " + binaryPath);
                    // attempt to run system electron if fallback needed or try to extract from Resources here
                }

                pb = new ProcessBuilder(binaryPath.toString());
                pb.directory(electronBaseDir.toFile());
            }

            pb.inheritIO();

            electronProcess = pb.start();
            electronProcess.onExit().thenRun(() -> {
                System.out.println("[Launcher] Electron Prozess wurde beendet. Schließe Backend...");
                shutdown();
            });
        } catch (IOException e) {
            System.err.println("[Launcher] Konnte Electron Prozess nicht starten: " + e.getMessage());
            logException("[Launcher] Fehler beim Starten von Electron", e);
            shutdown();
        }
    }

    private static void minimizeMainWindow() {
        // Send to electron if needed, or let electron handle it
    }

    private static void toggleMaximizeMainWindow() {
        // Send to electron if needed, or let electron handle it
    }

    private static void requestCloseMainWindow() {
        Thread.ofVirtual().start(() -> {
            try { Thread.sleep(75); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            shutdown();
        });
    }


    private static void shutdown() { shutdown(true); }

    private static void shutdown(boolean exitJvm) {
        if (!SHUTDOWN_STARTED.compareAndSet(false, true)) return;

        if (electronProcess != null) {
            electronProcess.destroy();
        }

        try { if (restServer != null) restServer.stop(); } catch (Exception ignored) {}

        if (exitJvm) { System.exit(0); }
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(false), "launcher-shutdown-hook"));
    }

    private static long resolveParentPid(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--parent-pid=")) { continue; }
            try { return Long.parseLong(arg.substring("--parent-pid=".length())); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
    }

    private static void startParentExitWatcher(long parentPid) {
        if (parentPid <= 0) return;
        ProcessHandle parent = ProcessHandle.of(parentPid).orElse(null);
        if (parent == null) return;
        parent.onExit().thenRun(VoltLauncher::shutdown);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static boolean isArm() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return arch.contains("aarch64") || arch.contains("arm");
    }


    private static void installDiagnostics() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable == null) {
                System.err.println("[Launcher] Uncaught exception without throwable in thread: " + thread.getName());
                return;
            }
            logUncaught(thread, throwable);
        });
    }

    private static void logUncaught(Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        StringBuilder dump = new StringBuilder();
        dump.append('[').append(TS_FORMAT.format(LocalDateTime.now())).append("] Uncaught in ")
            .append(thread.getName()).append(" (#").append(thread.threadId()).append(")\n")
            .append(sw)
            .append("\n--- Thread dump ---\n");

        for (var entry : Thread.getAllStackTraces().entrySet()) {
            Thread t = entry.getKey();
            dump.append('"').append(t.getName()).append('"')
                .append(" id=").append(t.threadId())
                .append(" state=").append(t.getState())
                .append('\n');
            for (StackTraceElement ste : entry.getValue()) {
                dump.append("    at ").append(ste).append('\n');
            }
        }

        String payload = dump.toString();
        synchronized (DIAG_LOCK) {
            System.err.println(payload);
            try {
                Path logsDir = AppPaths.logsDirectory();
                Files.createDirectories(logsDir);
                Path file = logsDir.resolve("launcher-uncaught.log");
                Files.writeString(file, payload + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException io) {
                System.err.println("[Launcher] Konnte Uncaught-Logdatei nicht schreiben: " + io.getMessage());
            }
        }
    }

    private static void logException(String prefix, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        synchronized (DIAG_LOCK) {
            System.err.println(prefix + "\n" + sw);
        }
    }
}
