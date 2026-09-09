package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancelledException;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import javafx.application.Platform;

import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** One dialog session. Public lifecycle methods and dispatched callbacks run on the UI thread. */
public final class SocialSecurityAnalyzerJobController implements AutoCloseable {

    public enum Mode { SOCIAL_SECURITY, QUICK, EXHAUSTIVE, WEIGHTED }
    public enum State { IDLE, RUNNING, CANCELLING, CLOSED }
    public enum Change { PLAN, ASSUMPTIONS, QUICK_CANDIDATES, SOCIAL_SECURITY_RESULT, WEIGHTED_SETTINGS }

    @FunctionalInterface
    public interface Work<T> {
        T run(AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    private final UUID session = UUID.randomUUID();
    private final SocialSecurityAnalysisJobCoordinator coordinator;
    private final Consumer<Runnable> dispatcher;
    private final EnumMap<Mode, Long> revisions = new EnumMap<>(Mode.class);
    private long generation;
    private Job<?> current;
    private State state = State.IDLE;
    private String status = "";
    private SocialSecurityAnalysisProgressModel progress = new SocialSecurityAnalysisProgressModel();
    private Runnable changed = () -> { };

    public SocialSecurityAnalyzerJobController(SocialSecurityAnalysisJobCoordinator coordinator) {
        this(coordinator, Platform::runLater);
    }

    public SocialSecurityAnalyzerJobController(
            SocialSecurityAnalysisJobCoordinator coordinator, Consumer<Runnable> dispatcher) {
        this.coordinator = Objects.requireNonNull(coordinator);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        for (Mode mode : Mode.values()) {
            revisions.put(mode, 0L);
        }
    }

    public UUID session() { return session; }
    public long generation() { return generation; }
    public State state() { return state; }
    public String status() { return status; }
    public SocialSecurityAnalysisProgressModel progress() { return progress; }
    public Mode mode() { return current == null ? null : current.mode; }
    public void onChanged(Runnable callback) { changed = Objects.requireNonNull(callback); }

    public <T> boolean start(Mode mode, Work<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(work);
        Objects.requireNonNull(success);
        Objects.requireNonNull(failure);
        if (state != State.IDLE) {
            return false;
        }
        var lease = coordinator.tryAcquire();
        if (lease == null) {
            return false;
        }
        Job<T> job = new Job<>(++generation, mode, revisions.get(mode), lease, success, failure);
        current = job;
        state = State.RUNNING;
        progress = new SocialSecurityAnalysisProgressModel();
        status = "Preparing analysis…";
        changed.run();
        try {
            lease.execute(() -> {
                try {
                    leaseToken(job).throwIfCancellationRequested();
                    job.result = work.run(update -> report(job, update), leaseToken(job));
                    leaseToken(job).throwIfCancellationRequested();
                } catch (Throwable exception) {
                    job.failure = exception;
                }
            }, () -> dispatcher.accept(() -> finish(job)));
        } catch (RuntimeException exception) {
            job.failure = exception;
            dispatcher.accept(() -> finish(job));
        }
        return true;
    }

    private AnalysisCancellationToken leaseToken(Job<?> job) {
        return job.lease::isCancelled;
    }

    public void cancel() {
        if (state == State.RUNNING) {
            current.lease.cancel();
            state = State.CANCELLING;
            progress.cancel();
            status = "Cancelling…";
            changed.run();
        }
    }

    public void invalidate(Change change) {
        if (state == State.CLOSED) {
            return;
        }
        for (Mode mode : Mode.values()) {
            boolean affected = switch (change) {
                case PLAN -> true;
                case ASSUMPTIONS -> mode != Mode.EXHAUSTIVE;
                case QUICK_CANDIDATES, SOCIAL_SECURITY_RESULT -> mode == Mode.QUICK;
                case WEIGHTED_SETTINGS -> mode == Mode.WEIGHTED;
            };
            if (affected) {
                revisions.compute(mode, (key, value) -> value + 1);
            }
        }
    }

    private boolean owns(Job<?> job) {
        return state != State.CLOSED && current == job && generation == job.generation;
    }

    private boolean eligible(Job<?> job) {
        return owns(job) && !job.lease.isCancelled()
                && revisions.get(job.mode) == job.revision;
    }

    private void report(Job<?> job, AnalysisProgress update) {
        synchronized (job) {
            if (job.lease.isCancelled() || !SocialSecurityAnalysisProgressModel.advances(job.pending, update)) {
                return;
            }
            job.pending = update;
            if (job.progressQueued) {
                return;
            }
            job.progressQueued = true;
        }
        dispatcher.accept(() -> {
            AnalysisProgress latest;
            synchronized (job) {
                latest = job.pending;
                job.progressQueued = false;
            }
            if (eligible(job) && state == State.RUNNING) {
                progress.accept(latest);
                status = progress.text();
                changed.run();
            }
        });
    }

    private <T> void finish(Job<T> job) {
        if (!owns(job)) {
            return;
        }
        boolean publish = eligible(job);
        // This callback is enqueued only after the lease has been released.
        state = State.IDLE;
        if (job.lease.isCancelled() || job.failure instanceof AnalysisCancelledException) {
            status = "Analysis cancelled";
        } else if (!publish) {
            status = "Inputs changed - run analysis again.";
        } else if (job.failure != null) {
            status = "Analysis failed before completion";
        } else {
            progress.complete();
            status = "Complete";
        }
        changed.run();
        // Recheck after the state observer, which may close or invalidate the dialog.
        if (publish && eligible(job)) {
            if (job.failure == null) {
                job.success.accept(job.result);
            } else if (!(job.failure instanceof AnalysisCancelledException)) {
                job.onFailure.accept(job.failure);
            }
        }
    }

    @Override
    public void close() {
        if (state == State.CLOSED) {
            return;
        }
        if (current != null) {
            current.lease.cancel();
        }
        state = State.CLOSED;
        generation++;
        changed = () -> { };
    }

    private static final class Job<T> {
        final long generation;
        final Mode mode;
        final long revision;
        final SocialSecurityAnalysisJobCoordinator.Lease lease;
        final Consumer<T> success;
        final Consumer<Throwable> onFailure;
        T result;
        Throwable failure;
        AnalysisProgress pending;
        boolean progressQueued;

        Job(long generation, Mode mode, long revision, SocialSecurityAnalysisJobCoordinator.Lease lease,
                Consumer<T> success, Consumer<Throwable> onFailure) {
            this.generation = generation;
            this.mode = mode;
            this.revision = revision;
            this.lease = lease;
            this.success = success;
            this.onFailure = onFailure;
        }
    }
}
