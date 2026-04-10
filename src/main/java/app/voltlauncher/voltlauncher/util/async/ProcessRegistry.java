package app.voltlauncher.voltlauncher.util.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ProcessRegistry {

    private final ConcurrentHashMap < String, ProcessHandle> handles = new ConcurrentHashMap <> ();
    private final ExitListener listener;
    public ProcessRegistry(ExitListener listener) {
        this.listener = listener;
    }

    public void register(String key, ProcessHandle handle) {
        purgeExited();
        ProcessHandle existing = handles.get(key);
        if (existing != null && existing.isAlive()) {
            throw new IllegalStateException("Process already registered for key: " + key);
        }
        handles.put(key, handle);
        handle.onExit().thenRun(() -> {
            handles.remove(key, handle);
            listener.onExit(key, handle);
        });
    }

    public ProcessHandle get(String key) {
        purgeExited();
        ProcessHandle handle = handles.get(key);
        if (handle == null) {
            return null;
        }
        if (!handle.isAlive()) {
            handles.remove(key, handle);
            return null;
        }
        return handle;
    }

    public boolean isAlive(String key) {
        return get(key) != null;
    }

    public ProcessHandle remove(String key) {
        return handles.remove(key);
    }

    public List < String> liveKeys() {
        purgeExited();
        return new ArrayList <> (handles.keySet());
    }

    private void purgeExited() {
        handles.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }

    public interface ExitListener {
        void onExit(String key, ProcessHandle handle);
    }
}
