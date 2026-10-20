package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotSaveException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Application lifetime, bounded background I/O. Never schedules financial work. */
public final class AnalysisResultStore {
    private static final Logger LOG = Logger.getLogger(AnalysisResultStore.class.getName());
    public record Slot(UUID planId, AnalysisKind kind) { }
    public enum Durability { MEMORY_ONLY, PENDING, SAVED, SAVE_FAILED }
    public record Status(long sequence, Durability durability, String explanation) { }
    public record Drain(boolean complete, List<String> failures) {
        public Drain { failures = List.copyOf(failures); }
        public boolean safeToExit() { return complete && failures.isEmpty(); }
    }
    private static final class Pending {
        final long sequence;
        final AcceptedAnalysisCompletion completion;
        Status status;
        AnalysisSnapshotSaveException failure;
        CompletableFuture<Status> settled = new CompletableFuture<>();
        Pending(long sequence, AcceptedAnalysisCompletion completion) {
            this.sequence = sequence; this.completion = completion;
            status = new Status(sequence, Durability.PENDING, "Analysis completed; saving results.");
        }
    }
    private final AnalysisSnapshotRepository repository;
    private final ThreadPoolExecutor executor;
    private final LinkedHashMap<Slot, Pending> latest = new LinkedHashMap<>();
    private final List<String> operationFailures = new ArrayList<>();
    private final Map<Slot, AnalysisCompatibilityPolicy.Assessment> copyAssessments = new HashMap<>();
    private long sequence;
    private boolean accepting = true;
    public AnalysisResultStore(AnalysisSnapshotRepository repository) {
        this.repository = Objects.requireNonNull(repository);
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), task -> {
            Thread thread = new Thread(task, "analysis-snapshot-io");
            thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized Status accept(AcceptedAnalysisCompletion completion) {
        if (!accepting) throw new IllegalStateException("Result storage is draining; retry after exit is cancelled.");
        var slot = new Slot(completion.planId(), completion.kind());
        if (!latest.containsKey(slot) && latest.size() >= 64) {
            var saved = latest.entrySet().stream().filter(e -> e.getValue().status.durability() == Durability.SAVED).findFirst();
            if (saved.isEmpty()) throw new IllegalStateException("Too many unsaved result slots; retry saving pending results first.");
            latest.remove(saved.orElseThrow().getKey());
        }
        var pending = new Pending(++sequence, completion);
        latest.put(slot, pending);
        if (completion.planFile() == null) {
            pending.status = new Status(pending.sequence, Durability.MEMORY_ONLY,
                    "This analysis is available in memory only until application exit. Save the retirement plan and rerun to persist it; no plan was autosaved.");
            pending.settled.complete(pending.status);
        } else enqueue(slot, pending);
        return pending.status;
    }
    private void enqueue(Slot slot, Pending pending) {
        try { executor.execute(() -> persist(slot, pending)); }
        catch (RejectedExecutionException rejected) {
            recordFailure(slot, pending, AnalysisSnapshotSaveException.at(AnalysisSnapshotSaveException.Stage.QUEUE, -1, -1, rejected));
        }
    }
    private void persist(Slot slot, Pending pending) {
        synchronized (this) {
            if (latest.get(slot) != pending) {
                pending.settled.complete(new Status(pending.sequence, Durability.PENDING, "Superseded by a newer accepted result."));
                return;
            }
        }
        var stage = AnalysisSnapshotSaveException.Stage.PLAN_BINDING;
        try {
            repository.claimPlan(pending.completion.planId(), pending.completion.planFile());
            stage = AnalysisSnapshotSaveException.Stage.SNAPSHOT_CONSTRUCTION;
            var snapshot = pending.completion.snapshot(new AnalysisSnapshotCodec());
            stage = AnalysisSnapshotSaveException.Stage.ATOMIC_WRITE;
            repository.save(snapshot);
            synchronized (this) {
                pending.failure = null;
                if (latest.get(slot) == pending) pending.status = new Status(pending.sequence, Durability.SAVED, "Results saved.");
                pending.settled.complete(new Status(pending.sequence, Durability.SAVED, "Results saved."));
            }
        } catch (Exception failure) {
            recordFailure(slot, pending, AnalysisSnapshotSaveException.at(stage, -1, -1, failure));
        }
    }
    private void recordFailure(Slot slot, Pending pending, AnalysisSnapshotSaveException failure) {
        // Never log plan contents, exception scalar values or filesystem paths. Keep the original throwable
        // separately for debugger inspection; the log retains every original frame, cause and suppressed failure.
        LOG.log(Level.WARNING, "Analysis snapshot save failed: kind=" + slot.kind() + " stage=" + failure.stage()
                + " rawBytes=" + failure.rawBytes() + " compressedBytes=" + failure.compressedBytes()
                + " maxRawBytes=" + AnalysisSnapshotCodec.MAX_RAW_BYTES
                + " maxCompressedBytes=" + AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES
                + " maxTokens=" + AnalysisSnapshotCodec.MAX_TOKENS + " maxArrays=" + AnalysisSnapshotCodec.MAX_ARRAYS
                + " maxDepth=" + AnalysisSnapshotCodec.MAX_DEPTH + " maxStringLength=" + AnalysisSnapshotCodec.MAX_STRING_LENGTH
                + " maxNumberLength=" + AnalysisSnapshotCodec.MAX_NUMBER_LENGTH + " (-1 means size not yet available)",
                diagnosticTrace(failure, new IdentityHashMap<>()));
        var status = new Status(pending.sequence, Durability.SAVE_FAILED,
                "Analysis completed, but results could not be saved. Failure category: " + failure.stage().description()
                        + ". The previous saved result was retained. Retry Save is available. Diagnostic details were logged.");
        synchronized (this) {
            pending.failure = failure;
            if (latest.get(slot) == pending) pending.status = status;
            pending.settled.complete(status);
        }
    }
    private static Throwable diagnosticTrace(Throwable original, IdentityHashMap<Throwable, Throwable> seen) {
        if (seen.containsKey(original)) return seen.get(original);
        String detail = original instanceof AnalysisSnapshotSaveException ? original.getMessage()
                : original instanceof com.fasterxml.jackson.core.exc.StreamConstraintsException ? original.getMessage()
                : original instanceof com.fasterxml.jackson.databind.JsonMappingException mapping ? "JSON field path: " + mapping.getPathReference()
                : auditedMessage(original) ? original.getMessage()
                : "exception message withheld to protect plan data; original retained in AnalysisResultStore.failure";
        var trace = new Exception(original.getClass().getName() + ": " + detail);
        seen.put(original, trace);
        trace.setStackTrace(original.getStackTrace());
        if (original.getCause() != null) trace.initCause(diagnosticTrace(original.getCause(), seen));
        for (var suppressed : original.getSuppressed()) trace.addSuppressed(diagnosticTrace(suppressed, seen));
        return trace;
    }
    private static boolean auditedMessage(Throwable failure) {
        if (failure.getStackTrace().length == 0) return false;
        String origin = failure.getStackTrace()[0].getClassName();
        // These validators/writers use fixed messages, never user values or serialized content.
        return Set.of("com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec",
                "com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository",
                "com.daviddunn.retirementplanner.persistence.AtomicFileWriter",
                "com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot",
                "com.daviddunn.retirementplanner.app.socialsecurity.results.WeightedSnapshots$Couple",
                "com.daviddunn.retirementplanner.app.socialsecurity.results.WeightedSnapshots$Individual",
                "com.daviddunn.retirementplanner.app.socialsecurity.results.WeightedSnapshots$Payload").contains(origin);
    }
    /** Full original exception, including nested causes, retained until a successful retry or replacement. */
    public synchronized Optional<AnalysisSnapshotSaveException> failure(Slot slot) {
        return Optional.ofNullable(latest.get(slot)).map(p -> p.failure);
    }
    public synchronized Optional<Status> status(Slot slot) {
        return Optional.ofNullable(latest.get(slot)).map(p -> p.status);
    }
    public synchronized Optional<AcceptedAnalysisCompletion> inMemory(Slot slot) {
        return Optional.ofNullable(latest.get(slot)).map(p -> p.completion);
    }
    /** Reads on the storage worker, without admitting a financial job or modifying the saved slot. */
    public synchronized CompletionStage<Optional<AnalysisSnapshot>> load(Slot slot, Path planFile) {
        var result = new CompletableFuture<Optional<AnalysisSnapshot>>();
        if (!accepting) {
            result.completeExceptionally(new IllegalStateException("Result storage is draining."));
            return result.minimalCompletionStage();
        }
        try {
            executor.execute(() -> {
                try {
                    repository.claimPlan(slot.planId(), Objects.requireNonNull(planFile));
                    // A newly accepted result takes precedence over an older disk version, including on save failure.
                    AcceptedAnalysisCompletion memory;
                    synchronized (this) { memory = inMemory(slot).orElse(null); }
                    result.complete(memory == null ? repository.load(slot.planId(), slot.kind())
                            : Optional.of(memory.presentationSnapshot(new AnalysisSnapshotCodec())));
                } catch (Exception failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException full) { result.completeExceptionally(full); }
        return result.minimalCompletionStage();
    }
    public synchronized CompletionStage<Status> whenSettled(Slot slot) {
        return Objects.requireNonNull(latest.get(slot)).settled.minimalCompletionStage();
    }
    /** Pure restoration/compatibility work shares the bounded application-owned I/O lifecycle. */
    public synchronized <T> CompletionStage<T> background(Callable<T> operation) {
        var result = new CompletableFuture<T>();
        if (!accepting) {
            result.completeExceptionally(new IllegalStateException("Result storage is draining."));
        } else try {
            executor.execute(() -> {
                try { result.complete(operation.call()); }
                catch (Exception failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException full) { result.completeExceptionally(full); }
        return result.minimalCompletionStage();
    }
    public synchronized boolean needsDrain() {
        return !executor.getQueue().isEmpty() || executor.getActiveCount() > 0 || !operationFailures.isEmpty()
                || latest.values().stream().anyMatch(p -> p.status.durability() == Durability.PENDING || p.status.durability() == Durability.SAVE_FAILED);
    }
    public synchronized void retryFailed() {
        for (var slot : List.copyOf(latest.keySet())) retry(slot);
    }
    public synchronized void retry(Slot slot) {
        if (!accepting) throw new IllegalStateException("Storage is draining.");
        var p = Objects.requireNonNull(latest.get(slot), "No accepted result to retry.");
        if (p.status.durability() != Durability.SAVE_FAILED) return;
        p.settled = new CompletableFuture<>();
        p.status = new Status(p.sequence, Durability.PENDING, "Retrying result save."); enqueue(slot, p);
    }
    /** Must follow the new plan commit. Clone failure is independent of plan save success. */
    public synchronized CompletableFuture<List<String>> cloneSnapshots(UUID source, UUID target, Path targetFile) {
        return cloneSnapshots(source, target, targetFile, null);
    }
    public synchronized CompletableFuture<List<String>> cloneSnapshots(UUID source, UUID target, Path targetFile,
            com.daviddunn.retirementplanner.domain.model.RetirementPlan isolatedNewPlan) {
        if (!accepting) throw new IllegalStateException("Storage is draining.");
        var result = new CompletableFuture<List<String>>();
        try { executor.execute(() -> {
            List<String> problems = new ArrayList<>();
            try {
                for (AnalysisKind kind : AnalysisKind.values()) {
                    try {
                        var original = repository.load(source, kind);
                        if (original.isPresent()) {
                            repository.claimPlan(target, targetFile);
                            var clone = original.orElseThrow().clonedTo(target);
                            boolean copied = repository.saveIfAbsent(clone);
                            if (!copied) clone = repository.load(target, kind).orElseThrow();
                            var assessment = isolatedNewPlan == null
                                    ? new AnalysisCompatibilityPolicy.Assessment(AnalysisCompatibilityPolicy.State.OUTDATED,
                                            copied ? "Historical copy; current inputs have not been assessed."
                                                    : "Existing target results retained; current inputs have not been assessed.")
                                    : new AnalysisCompatibilityPolicy().assessCopy(clone, isolatedNewPlan);
                            synchronized (this) { copyAssessments.put(new Slot(target, kind), assessment); }
                        }
                    } catch (Exception failure) { problems.add("The new plan was saved, but " + kind + " historical results were not copied."); }
                }
            } catch (Exception failure) { problems.add("The new plan was saved, but historical results could not be copied."); }
            synchronized (this) { operationFailures.addAll(problems); }
            result.complete(List.copyOf(problems));
        }); } catch (RejectedExecutionException rejected) {
            var warning = "The new plan was saved, but historical result copying was not queued. Retry copying is required.";
            operationFailures.add(warning); result.complete(List.of(warning));
        }
        return result;
    }
    public synchronized Optional<AnalysisCompatibilityPolicy.Assessment> copyAssessment(Slot slot) {
        return Optional.ofNullable(copyAssessments.get(slot));
    }
    /** Barrier contract, called off FX. It does not silently discard failures or close the executor. */
    public Drain drain(Duration timeout) {
        synchronized (this) { accepting = false; }
        executor.prestartCoreThread();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            // Queue may be full; wait for worker progress without admitting more work.
            var barrier = new FutureTask<Void>(() -> null);
            if (!executor.getQueue().offer(barrier, Math.max(0, timeout.toMillis()), TimeUnit.MILLISECONDS))
                return new Drain(false, List.of("Result writes are still pending."));
            barrier.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            synchronized (this) {
                var errors = new ArrayList<>(operationFailures);
                latest.values().stream().filter(p -> p.status.durability() == Durability.PENDING || p.status.durability() == Durability.SAVE_FAILED)
                        .forEach(p -> errors.add(p.status.explanation()));
                return new Drain(true, errors);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return new Drain(false, List.of("Result save drain interrupted.")); }
        catch (ExecutionException | TimeoutException e) { return new Drain(false, List.of("Result writes are still pending.")); }
    }
    public synchronized void resumeAfterCancelledExit() { accepting = true; }
    public synchronized List<String> takeOperationWarnings() {
        var result = List.copyOf(operationFailures); operationFailures.clear(); return result;
    }
    public void shutdownAfterSuccessfulDrain() {
        synchronized (this) {
            if (accepting || latest.values().stream().anyMatch(p -> p.status.durability() == Durability.PENDING || p.status.durability() == Durability.SAVE_FAILED)
                    || !operationFailures.isEmpty() || !executor.getQueue().isEmpty())
                throw new IllegalStateException("Drain all accepted result writes before shutdown.");
        }
        executor.shutdown();
    }
    /** Normal application stop only, after the window's asynchronous drain guard. */
    public synchronized void shutdownIfIdle() {
        if (needsDrain()) throw new IllegalStateException("Accepted analysis results still require saving.");
        accepting = false; executor.shutdown();
    }
}
