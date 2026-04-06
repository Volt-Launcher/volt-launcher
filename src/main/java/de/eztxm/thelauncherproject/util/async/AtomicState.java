package de.eztxm.thelauncherproject.util.async;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class AtomicState<T> {

    private final AtomicReference<T> ref;

    public AtomicState(T initial) {
        this.ref = new AtomicReference<>(initial);
    }

    public T get() {
        return ref.get();
    }

    public boolean is(T value) {
        return ref.get() == value;
    }

    public void set(T value) {
        ref.set(value);
    }

    public boolean compareAndSet(T expected, T next) {
        return ref.compareAndSet(expected, next);
    }

    public T getAndUpdate(UnaryOperator<T> operator) {
        return ref.getAndUpdate(operator);
    }

    public void ifPresent(Consumer<T> action) {
        T snapshot = ref.get();
        if (snapshot != null) {
            action.accept(snapshot);
        }
    }

}
