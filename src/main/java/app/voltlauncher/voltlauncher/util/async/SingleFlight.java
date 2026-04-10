package app.voltlauncher.voltlauncher.util.async;

import java.util.concurrent.*;

public final class SingleFlight < T> {

    private final ConcurrentHashMap < String, Future < T>> inflight = new ConcurrentHashMap <> ();

    public T call(String key, Callable < T> loader) throws Exception {
        Future < T> existing = inflight.get(key);
        if (existing != null) {
            return unwrap(existing);
        }
        FutureTask < T> task = new FutureTask <> (loader);
        Future < T> previous = inflight.putIfAbsent(key, task);
        if (previous != null) {
            return unwrap(previous);
        }
        try {
            task.run();
            return unwrap(task);
        } finally {
            inflight.remove(key, task);
        }
    }

    private T unwrap(Future < T> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

}

