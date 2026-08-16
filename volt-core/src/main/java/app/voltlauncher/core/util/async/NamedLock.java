package app.voltlauncher.core.util.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serialises work per string key. Locks are fair, so callers queued behind a long download are
 * served in arrival order, and are dropped once the last holder leaves to keep the map bounded.
 */
public final class NamedLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <V> V withLock(String key, Callable<V> task) throws Exception {
        ReentrantLock lock = locks.computeIfAbsent(key, unused -> new ReentrantLock(true));
        lock.lock();
        try {
            return task.call();
        } finally {
            unlockAndMaybeEvict(key, lock);
        }
    }

    public void withLock(String key, ThrowingRunnable action) throws Exception {
        withLock(key, () -> {
            action.run();
            return null;
        });
    }

    private void unlockAndMaybeEvict(String key, ReentrantLock lock) {
        boolean contended = lock.hasQueuedThreads();
        lock.unlock();
        if (!contended && !lock.isLocked()) {
            locks.remove(key, lock);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
