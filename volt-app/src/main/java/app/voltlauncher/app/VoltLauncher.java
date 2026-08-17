package app.voltlauncher.app;

import app.voltlauncher.server.RestServer;

import java.util.concurrent.atomic.AtomicBoolean;

public class VoltLauncher {

    private static final int API_PORT = 45938;
    /** Gives the HTTP response a moment to reach the UI before the JVM exits. */
    private static final long SHUTDOWN_DELAY_MS = 75L;

    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);
    private static volatile RestServer restServer;
    private static ElectronProcessManager electronManager;

    public static void main(String[] args) {
        LauncherDiagnostics.install();
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer(
                API_PORT,
                () -> {},
                () -> {},
                VoltLauncher::requestShutdown,
                VoltLauncher::openInBrowser);
        restServer.start();

        System.out.println("[Launcher] Starting the Electron front end…");
        electronManager = new ElectronProcessManager(VoltLauncher::shutdown);
        electronManager.start();
    }

    static void shutdown() {
        shutdown(true);
    }

    /** Closes the launcher from a request handler without cutting that request's response short. */
    private static void requestShutdown() {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(SHUTDOWN_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            shutdown();
        });
    }

    /** Opens the Microsoft sign-in page in the user's browser as a fallback for the Electron window. */
    private static void openInBrowser(String url) {
        try {
            app.voltlauncher.core.util.SystemOpener.openUrl(url);
        } catch (Exception e) {
            System.err.println("[Launcher] Could not open the sign-in page: " + e.getMessage());
        }
    }

    private static void shutdown(boolean exitJvm) {
        if (!SHUTDOWN_STARTED.compareAndSet(false, true)) return;
        if (electronManager != null) electronManager.stop();
        try { if (restServer != null) restServer.stop(); } catch (Exception ignored) {}
        if (exitJvm) System.exit(0);
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(false), "launcher-shutdown-hook"));
    }

    private static long resolveParentPid(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--parent-pid=")) continue;
            try { return Long.parseLong(arg.substring("--parent-pid=".length())); }
            catch (NumberFormatException ignored) { return -1; }
        }
        return ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(-1L);
    }

    private static void startParentExitWatcher(long parentPid) {
        if (parentPid <= 0) return;
        ProcessHandle.of(parentPid).ifPresent(parent -> parent.onExit().thenRun(VoltLauncher::shutdown));
    }
}
