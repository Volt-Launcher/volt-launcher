package de.eztxm.thelauncherproject.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class NamedLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap();

    public void withLock(String key, Runnable action) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    public <T> T withLock(String key, Supplier<T> supplier) {
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public void release(String key) {
        locks.computeIfPresent(key, (k, lock) -> lock.isLocked() ? lock : null);
    }

    private ReentrantLock lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}
