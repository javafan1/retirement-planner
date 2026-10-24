package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Application lifetime, bounded metadata I/O only. No simulation/projection dependencies. */
public final class AnalysisParameterCoordinator {
    public enum Status { PENDING, SAVED, SAVE_FAILED, MEMORY_ONLY }
    public record State(Status status, UUID runId, String message) { }
    public record Binding(RetirementPlan plan, UUID planId, Path path, long epoch) { }
    public record Drain(boolean complete, int pending, int failed) { }
    private record Slot(RetirementPlan plan, AnalysisKind kind) { }
    private record Work(AcceptedAnalysisParameters event, Slot slot) { }
    private final JsonRetirementPlanRepository repository;
    private final ThreadPoolExecutor executor;
    private final Map<Slot, Work> latest = new HashMap<>();
    private final Map<Slot, State> states = new HashMap<>();
    private final Map<Slot, Exception> failures = new HashMap<>();
    private Binding binding;
    private long epoch;
    private final java.util.concurrent.atomic.AtomicLong acceptanceSequence = new java.util.concurrent.atomic.AtomicLong();
    private boolean closing;
    private boolean exitDrain;
    private int pendingIo;
    private final java.util.List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Dispatchers must enqueue UI work; accepted writes remain independent of dialog lifetime. */
    public Runnable addListener(java.util.function.Consumer<Runnable> dispatcher, Runnable listener) {
        Runnable notification = () -> dispatcher.accept(listener);
        listeners.add(notification);
        return () -> listeners.remove(notification);
    }
    private void changed() { listeners.forEach(Runnable::run); }

    public AnalysisParameterCoordinator(JsonRetirementPlanRepository repository) {
        this.repository = repository;
        executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "analysis-parameter-save"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
    }
    /** Reserve at run capture, so later completion order cannot make an older run newer. */
    public long nextAcceptanceSequence() { return acceptanceSequence.incrementAndGet(); }

    /** UI/application owner binds explicitly on Open/New/Save As; binding performs no I/O. */
    public synchronized Binding bind(RetirementPlan plan, Path path) {
        UUID identity = plan.getPlanId() == null ? null : RetirementPlan.validatedPlanId(plan.getPlanId());
        Path normalized = path == null ? null : path.toAbsolutePath().normalize();
        if (binding == null || binding.plan() != plan || !Objects.equals(binding.path(), normalized)
                || !Objects.equals(binding.planId(), identity)) {
            binding = new Binding(plan, identity, normalized, ++epoch);
            // Reopening the exact same UUID/path must not lose a newer accepted-but-unsaved event.
            for (AnalysisKind kind : AnalysisKind.values()) {
                Work retained = retained(plan, kind);
                if (retained != null) {
                    var configurations = plan.getAnalyzerConfigurations();
                    if (configurations == null) configurations = AnalysisParameterConfigurations.empty();
                    try { plan.setAnalyzerConfigurations(configurations.with(retained.event().configuration())); }
                    catch (RuntimeException protectedMetadata) { /* Retained memory remains available; never replace future metadata. */ }
                }
            }
        }
        return binding;
    }

    public synchronized boolean accept(AcceptedAnalysisParameters event) {
        if (closing || exitDrain || !event.binding().equals(binding) || !Objects.equals(binding.plan().getPlanId(),
                binding.planId() == null ? null : binding.planId().toString())) return false;
        var configuration = event.configuration();
        if ((configuration.shape() == AnalysisKind.Shape.COUPLE) != binding.plan().getHousehold().hasSpouse()) return false;
        Slot slot = new Slot(binding.plan(), configuration.kind());
        Work prior = latest.get(slot);
        if (prior != null && event.acceptanceSequence() <= prior.event().acceptanceSequence()) return false;
        var configurations = binding.plan().getAnalyzerConfigurations();
        if (configurations == null) configurations = AnalysisParameterConfigurations.empty();
        Work work = new Work(event, slot); latest.put(slot, work);
        try {
            // Do not overwrite future/oversized metadata, but retain the newly accepted inputs in memory.
            configurations = configurations.with(configuration);
            binding.plan().setAnalyzerConfigurations(configurations);
        } catch (RuntimeException blocked) {
            failures.put(slot, blocked);
            states.put(slot, new State(Status.SAVE_FAILED, configuration.acceptedRunId(),
                    "Accepted parameters remain in memory; existing configuration metadata could not be safely updated."));
            notifyAll(); changed(); return true;
        }
        if (repository == null || binding.planId() == null || binding.path() == null) {
            states.put(slot, new State(Status.MEMORY_ONLY, configuration.acceptedRunId(), "Save the plan explicitly to persist accepted parameters."));
        } else enqueue(work);
        notifyAll();
        changed();
        return true;
    }

    private void enqueue(Work work) {
        var event = work.event();
        repository.registerParameters(event.binding().path(), event.configuration());
        states.put(work.slot(), new State(Status.PENDING, event.configuration().acceptedRunId(), "Saving accepted parameters."));
        pendingIo++;
        try { executor.execute(() -> {
            try { write(work); }
            finally { synchronized (this) { pendingIo--; notifyAll(); } }
        }); }
        catch (RejectedExecutionException busy) {
            pendingIo--;
            states.put(work.slot(), new State(Status.SAVE_FAILED, event.configuration().acceptedRunId(), "Parameter save queue is unavailable. Retry Save is available."));
        }
    }

    private void write(Work work) {
        synchronized (this) { if (latest.get(work.slot()) != work) return; }
        State outcome;
        try {
            var event = work.event();
            repository.saveParameters(event.binding().path(), event.binding().planId(), event.configuration());
            outcome = new State(Status.SAVED, event.configuration().acceptedRunId(), "Accepted parameters saved.");
        } catch (Exception failure) {
            // Retain the cause for explicit diagnostics; do not log parser messages containing plan data.
            synchronized (this) { if (latest.get(work.slot()) == work) failures.put(work.slot(), failure); }
            String message = failure.getMessage() != null && failure.getMessage().startsWith("The plan file changed externally")
                    ? "Parameter save conflict: the plan file changed externally. Reopen and review it before retrying."
                    : "Parameters could not be saved. Retry Save is available.";
            outcome = new State(Status.SAVE_FAILED, work.event().configuration().acceptedRunId(), message);
        }
        synchronized (this) {
            // An explicit Save/Save As may already have durably saved this exact run while I/O waited.
            State current = states.get(work.slot());
            boolean alreadySaved = current != null && current.status() == Status.SAVED
                    && current.runId().equals(work.event().configuration().acceptedRunId());
            if (latest.get(work.slot()) == work && !alreadySaved) states.put(work.slot(), outcome);
            notifyAll();
            changed();
        }
    }

    public synchronized Optional<State> state(RetirementPlan plan, AnalysisKind kind) {
        Work work = retained(plan, kind);
        return Optional.ofNullable(states.get(work == null ? new Slot(plan, kind) : work.slot()));
    }
    public synchronized Optional<AnalysisParameterConfiguration> lastAccepted(RetirementPlan plan, AnalysisKind kind) {
        Work work = retained(plan, kind);
        if (work == null) work = latest.get(new Slot(plan, kind));
        return work != null ? Optional.of(work.event().configuration()) : plan.getAnalyzerConfigurations() == null
                ? Optional.empty() : plan.getAnalyzerConfigurations().get(kind);
    }
    public synchronized Optional<Exception> failure(RetirementPlan plan, AnalysisKind kind) {
        return Optional.ofNullable(failures.get(new Slot(plan, kind)));
    }
    public synchronized boolean retry(RetirementPlan plan, AnalysisKind kind) {
        Slot slot = new Slot(plan, kind); Work work = retained(plan, kind);
        if (work == null) work = latest.get(slot);
        if (work != null) slot = work.slot();
        if (closing || work == null || states.get(slot).status() != Status.SAVE_FAILED || repository == null
                || work.event().binding().path() == null || work.event().binding().planId() == null) return false;
        enqueue(work); return true;
    }
    private Work retained(RetirementPlan plan, AnalysisKind kind) {
        if (binding == null || binding.plan() != plan || binding.planId() == null || binding.path() == null) return null;
        var existing = plan.getAnalyzerConfigurations() == null ? Optional.<AnalysisParameterConfiguration>empty() : plan.getAnalyzerConfigurations().get(kind);
        return latest.values().stream().filter(work -> work.slot().kind() == kind
                && binding.planId().equals(work.event().binding().planId()) && binding.path().equals(work.event().binding().path())
                && (existing.isEmpty() || !existing.orElseThrow().acceptedAt().isAfter(work.event().configuration().acceptedAt())))
                .max(Comparator.comparingLong(work -> work.event().acceptanceSequence())).orElse(null);
    }
    public synchronized boolean needsDrain() {
        return pendingIo > 0 || states.values().stream().anyMatch(s -> s.status() == Status.PENDING || s.status() == Status.SAVE_FAILED);
    }
    public synchronized void beginExit() { exitDrain = true; }
    public synchronized void resumeAfterCancelledExit() { if (!closing) exitDrain = false; }
    public synchronized void retryFailed() {
        for (var entry : List.copyOf(latest.values())) {
            if (!closing) retry(entry.slot().plan(), entry.slot().kind());
        }
    }

    /** Called only after ordinary Save/Save As has committed all accepted in-memory entries. */
    public synchronized void saved(RetirementPlan plan, Path path) {
        bind(plan, path);
        var configurations = repository == null ? plan.getAnalyzerConfigurations() : repository.committedConfigurations(path).orElse(null);
        if (configurations != null) for (AnalysisKind kind : AnalysisKind.values()) {
            configurations.get(kind).ifPresent(configuration -> {
                Slot slot = new Slot(plan, kind); Work work = latest.get(slot);
                if (work == null || work.event().configuration().acceptedRunId().equals(configuration.acceptedRunId()))
                    states.put(slot, new State(Status.SAVED, configuration.acceptedRunId(), "Accepted parameters saved with plan."));
                for (var prior : latest.values()) {
                    if (prior.slot().kind() == kind && Objects.equals(prior.event().binding().planId(), binding.planId())
                            && Objects.equals(prior.event().binding().path(), binding.path())
                            && prior.event().configuration().acceptedRunId().equals(configuration.acceptedRunId()))
                        states.put(prior.slot(), new State(Status.SAVED, configuration.acceptedRunId(), "Accepted parameters saved with plan."));
                }
            });
        }
        notifyAll();
        changed();
    }

    /** Does not discard queued work. Call off FX; failure counts remain visible after drain. */
    public Drain drain(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (this) {
            while (pendingIo > 0 || states.values().stream().anyMatch(state -> state.status() == Status.PENDING)) {
                long remaining = deadline - System.nanoTime(); if (remaining <= 0) break;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            int pending = Math.max(pendingIo, (int) states.values().stream().filter(s -> s.status() == Status.PENDING).count());
            int failed = (int) states.values().stream().filter(s -> s.status() == Status.SAVE_FAILED).count();
            return new Drain(pending == 0, pending, failed);
        }
    }
    public synchronized void shutdown() { closing = true; executor.shutdown(); }
}
