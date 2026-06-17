package app.voltlauncher.voltlauncher;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class LauncherDiagnostics {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOG_LOCK = new Object();

    private LauncherDiagnostics() {}

    static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable == null) {
                System.err.println("[Launcher] Uncaught exception without throwable in thread: " + thread.getName());
                return;
            }
            logUncaught(thread, throwable);
        });
    }

    static void logException(String prefix, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        synchronized (LOG_LOCK) {
            System.err.println(prefix + "\n" + sw);
        }
    }

    private static void logUncaught(Thread thread, Throwable throwable) {
        String payload = buildDump(thread, throwable);
        synchronized (LOG_LOCK) {
            System.err.println(payload);
            writeToFile(payload);
        }
    }

    private static String buildDump(Thread thread, Throwable throwable) {
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
        return dump.toString();
    }

    private static void writeToFile(String payload) {
        try {
            Path logsDir = AppPaths.logsDirectory();
            Files.createDirectories(logsDir);
            Files.writeString(logsDir.resolve("launcher-uncaught.log"), payload + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException io) {
            System.err.println("[Launcher] Konnte Uncaught-Logdatei nicht schreiben: " + io.getMessage());
        }
    }
}
