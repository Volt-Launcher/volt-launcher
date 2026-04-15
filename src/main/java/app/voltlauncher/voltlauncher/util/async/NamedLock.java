package app.voltlauncher.voltlauncher.util.async;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class NamedLock {

    private final ConcurrentHashMap < String, ReentrantLock> locks = new ConcurrentHashMap();

    public void withLock(String key, Runnable action) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    public <V> V withLock(String key, Callable < V> task) throws Exception {
        ReentrantLock lock = locks.computeIfAbsent(key, (String _) -> new ReentrantLock(true));
        lock.lock();
        try {
            return task.call();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads() && !lock.isLocked()) {
                locks.remove(key, lock);
            }
        }
    }

    public void release(String key) {
        locks.computeIfPresent(key, (k, lock) -> lock.isLocked() ? lock : null);
    }

    private ReentrantLock lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}

