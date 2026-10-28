package com.daviddunn.retirementplanner.ui.startup;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** One exclusive startup task. No financial logic, JavaFX nodes, timers or general job scheduling. */
public final class ApplicationStartupCoordinator implements AutoCloseable {
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                var thread = new Thread(task, "application-startup");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Consumer<Runnable> dispatch;
    private boolean started;
    private boolean closed;

    public ApplicationStartupCoordinator(Consumer<Runnable> dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch);
    }

    public synchronized <T> void start(Supplier<T> prepare, Consumer<T> ready, Consumer<Throwable> failed) {
        if (started || closed) throw new IllegalStateException("Startup is already started or stopped.");
        started = true;
        worker.execute(() -> {
            try {
                T prepared = prepare.get();
                publish(() -> ready.accept(prepared));
            } catch (Throwable failure) {
                publish(() -> failed.accept(failure));
            } finally {
                worker.shutdown();
            }
        });
    }

    private void publish(Runnable callback) {
        synchronized (this) {
            if (closed) return;
            dispatch.accept(() -> {
                synchronized (ApplicationStartupCoordinator.this) {
                    if (!closed) callback.run();
                }
            });
        }
    }

    @Override public synchronized void close() {
        closed = true;
        worker.shutdownNow();
    }
}
