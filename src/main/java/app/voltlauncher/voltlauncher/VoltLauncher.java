package app.voltlauncher.voltlauncher;

import app.voltlauncher.voltlauncher.rest.RestServer;

import java.util.concurrent.atomic.AtomicBoolean;

public class VoltLauncher {

    private static final AtomicBoolean SHUTDOWN_STARTED = new AtomicBoolean(false);
    private static volatile RestServer restServer;
    private static ElectronProcessManager electronManager;

    public static void main(String[] args) {
        LauncherDiagnostics.install();
        registerShutdownHook();
        startParentExitWatcher(resolveParentPid(args));

        restServer = new RestServer(
                45938,
                () -> {},
                () -> {},
                () -> Thread.ofVirtual().start(() -> {
                    try { Thread.sleep(75); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    shutdown();
                }),
                url -> {});
        restServer.start();

        System.out.println("[Launcher] Starte Electron Frontend...");
        electronManager = new ElectronProcessManager(VoltLauncher::shutdown);
        electronManager.start();
    }

    static void shutdown() {
        shutdown(true);
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
