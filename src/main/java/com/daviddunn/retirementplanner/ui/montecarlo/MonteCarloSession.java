package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * UI-thread lifecycle with injected worker and dispatcher; no JavaFX controls or financial logic.
 */
public final class MonteCarloSession implements AutoCloseable {
    public enum State {IDLE, RUNNING, CANCELLING, COMPLETED, CANCELLED, FAILED, CLOSED}

    @FunctionalInterface
    public interface Work {
        MonteCarloRun run(AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    private final Executor worker;
    private final Consumer<Runnable> dispatcher;
    private State state = State.IDLE;
    private long generation;
    private long revision;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private AnalysisProgress progress;
    private MonteCarloRun result;
    private Throwable failure;
    private boolean stale;
    private Runnable changed = () -> {
    };

    public MonteCarloSession(Executor worker, Consumer<Runnable> dispatcher) {
        this.worker = Objects.requireNonNull(worker);
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    public void onChanged(Runnable listener) {
        changed = Objects.requireNonNull(listener);
    }

    public State state() {
        return state;
    }

    public boolean busy() {
        return state == State.RUNNING || state == State.CANCELLING;
    }

    public boolean stale() {
        return stale;
    }

    public MonteCarloRun result() {
        return result;
    }

    public Throwable failure() {
        return failure;
    }

    public AnalysisProgress progress() {
        return progress;
    }

    public boolean start(Work work) {
        if (busy() || state == State.CLOSED) {
            return false;
        }
        long id = ++generation;
        long capturedRevision = revision;
        AtomicBoolean token = new AtomicBoolean();
        cancellation = token;
        result = null;
        failure = null;
        stale = false;
        progress = null;
        state = State.RUNNING;
        changed.run();
        try {
            worker.execute(() -> {
                try {
                    AnalysisCancellationToken cancellationToken = token::get;
                    cancellationToken.throwIfCancellationRequested();
                    var completed = work.run(update -> dispatcher.accept(() -> {
                        if (generation == id && state == State.RUNNING && revision == capturedRevision
                                && (progress == null || update.completedWork() >= progress.completedWork())) {
                            progress = update;
                            changed.run();
                        }
                    }), cancellationToken);
                    cancellationToken.throwIfCancellationRequested();
                    dispatcher.accept(() -> finish(id, capturedRevision, completed, null, token));
                } catch (Throwable exception) {
                    dispatcher.accept(() -> finish(id, capturedRevision, null, exception, token));
                }
            });
        } catch (RuntimeException exception) {
            finish(id, capturedRevision, null, exception, token);
        }
        return true;
    }

    private void finish(long id, long capturedRevision, MonteCarloRun completed, Throwable error, AtomicBoolean token) {
        if (id != generation || state == State.CLOSED) {
            return;
        }
        if (token.get() || error instanceof AnalysisCancelledException || revision != capturedRevision) {
            state = State.CANCELLED;
        } else if (error != null) {
            failure = error;
            state = State.FAILED;
        } else {
            result = Objects.requireNonNull(completed);
            state = State.COMPLETED;
        }
        changed.run();
    }

    public void cancel() {
        if (state != State.RUNNING) {
            return;
        }
        cancellation.set(true);
        state = State.CANCELLING;
        changed.run();
    }

    public void invalidate() {
        revision++;
        stale = result != null || busy();
        cancel();
        changed.run();
    }

    @Override
    public void close() {
        cancellation.set(true);
        generation++;
        state = State.CLOSED;
        changed = () -> {
        };
    }
}
