package app.voltlauncher.voltlauncher;

import app.voltlauncher.voltlauncher.rest.RestServer;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
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
            String osDir = "";
            String executable = "";
            if (isMac()) {
                osDir = isArm() ? "mac-arm64" : "mac";
                executable = "VoltLauncher.app/Contents/MacOS/VoltLauncher";
            } else if (isLinux()) {
                osDir = "linux-unpacked";
                executable = "voltlauncher";
            } else {
                osDir = "win-unpacked";
                executable = "VoltLauncher.exe";
            }

            Path electronBaseDir = app.voltlauncher.voltlauncher.AppPaths.baseDirectory().resolve("electron");
            Path binaryPath = electronBaseDir.resolve(osDir).resolve(executable);

            if (!Files.exists(binaryPath)) {
                System.out.println("[Launcher] Electron Binary nicht gefunden unter " + binaryPath + ", entpacke Ressourcen...");
                extractElectronResources(electronBaseDir, osDir);

                if (!Files.exists(binaryPath)) {
                    // Try lower-cased defaults just in case
                    if (isMac()) {
                        executable = "voltlauncher-ui.app/Contents/MacOS/voltlauncher-ui";
                    } else if (isLinux()) {
                        executable = "voltlauncher-ui";
                    } else {
                        executable = "voltlauncher-ui.exe";
                    }
                    binaryPath = electronBaseDir.resolve(osDir).resolve(executable);
                }
            }

            if (!Files.exists(binaryPath)) {
                System.err.println("[Launcher] FEHLER: Electron Binary weiterhin nicht gefunden! Bitte Build prüfen. Erwarte: " + binaryPath);
            }

            ProcessBuilder pb = new ProcessBuilder(binaryPath.toString());
            pb.directory(electronBaseDir.toFile());
            pb.inheritIO();

            electronProcess = pb.start();
            electronProcess.onExit().thenRun(() -> {
                System.out.println("[Launcher] Electron Prozess wurde beendet. Schließe Backend...");
                shutdown();
            });
        } catch (Exception e) {
            System.err.println("[Launcher] Konnte Electron Prozess nicht starten: " + e.getMessage());
            logException("[Launcher] Fehler beim Starten von Electron", e);
            shutdown();
        }
    }

    private static void extractElectronResources(Path targetDir, String osDir) throws IOException {
        java.net.URL resource = VoltLauncher.class.getResource("/electron-bin/" + osDir + ".tar.gz");
        if (resource == null) {
            System.err.println("[Launcher] /electron-bin/" + osDir + ".tar.gz not found in resources! Was the UI built correctly?");
            return;
        }

        Files.createDirectories(targetDir);
        Path tarPath = targetDir.resolve(osDir + ".tar.gz");

        try (InputStream in = resource.openStream()) {
            Files.copy(in, tarPath, StandardCopyOption.REPLACE_EXISTING);
        }

        System.out.println("[Launcher] Extracting " + tarPath + " to " + targetDir + " ...");
        extractTarGz(tarPath, targetDir);
        Files.deleteIfExists(tarPath);
    }

    private static void extractTarGz(Path tarGzPath, Path targetDir) throws IOException {
        boolean isPosix = !System.getProperty("os.name", "").toLowerCase().contains("win");

        try (InputStream fis = Files.newInputStream(tarGzPath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir.normalize())) {
                    throw new IOException("Tar entry outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else if (entry.isSymbolicLink()) {
                    Files.createDirectories(entryPath.getParent());
                    Files.deleteIfExists(entryPath);
                    Files.createSymbolicLink(entryPath, Path.of(entry.getLinkName()));
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(tais, entryPath, StandardCopyOption.REPLACE_EXISTING);

                    if (isPosix) {
                        int mode = entry.getMode();
                        if ((mode & 0111) != 0) {
                            Set<PosixFilePermission> perms = modeToPosixPermissions(mode);
                            try { Files.setPosixFilePermissions(entryPath, perms); }
                            catch (UnsupportedOperationException ignored) {}
                        }
                    }
                }
            }
        }
    }

    private static Set<PosixFilePermission> modeToPosixPermissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        return perms;
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
