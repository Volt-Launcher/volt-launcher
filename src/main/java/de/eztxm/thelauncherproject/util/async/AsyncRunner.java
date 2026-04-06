package de.eztxm.thelauncherproject.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class AsyncRunner {

    private static final Logger LOG = Logger.getLogger(AsyncRunner.class.getName());
    private static final ExecutorService EXECUTOR = buildExecutor();

    private AsyncRunner() {}

    public static void run(Runnable task) {
        EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                LOG.severe("Unhandled error in async task: " + exception.getMessage());
            }
        });
    }

    public static <T>CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR);
    }

    public static void shutdown(int timeoutSeconds) {
        EXECUTOR.shutdown();
        try {
            if(!EXECUTOR.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException exception) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService buildExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}
