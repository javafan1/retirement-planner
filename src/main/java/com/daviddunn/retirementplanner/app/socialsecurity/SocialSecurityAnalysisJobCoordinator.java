package com.daviddunn.retirementplanner.app.socialsecurity;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Application-owned admission; a lease outlives cancellation and all service cleanup. */
public final class SocialSecurityAnalysisJobCoordinator implements AutoCloseable {

    private final ExecutorService executor;
    private Lease active;
    private boolean closed;

    public SocialSecurityAnalysisJobCoordinator() {
        this(Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, "social-security-analysis");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public SocialSecurityAnalysisJobCoordinator(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    public synchronized Lease tryAcquire() {
        if (closed || active != null) {
            return null;
        }
        active = new Lease();
        return active;
    }

    public synchronized boolean isBusy() {
        return active != null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (active != null) {
            active.cancel();
        }
        // Do not interrupt or wait on the JavaFX thread. Accepted work drains normally.
        executor.shutdown();
    }

    public final class Lease {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Lease() { }

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void execute(Runnable execution, Runnable terminated) {
            try {
                executor.execute(() -> {
                    try {
                        execution.run();
                    } finally {
                        release();
                        terminated.run();
                    }
                });
            } catch (RuntimeException exception) {
                release();
                throw exception;
            }
        }

        private void release() {
            synchronized (SocialSecurityAnalysisJobCoordinator.this) {
                if (active == this) {
                    active = null;
                }
            }
        }
    }
}
