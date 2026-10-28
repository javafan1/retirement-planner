package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.*;

/** UI-dispatched lifecycle; all decoding, digests, reconstruction and currency work use the existing store worker. */
public final class MonteCarloResultLifecycle<R> implements AutoCloseable {
    public record Binding(UUID planId, Path file, AnalysisKind kind) {
        public Binding { if (file != null) file = file.toAbsolutePath().normalize(); }
    }
    private final AnalysisResultStore store;
    private final Consumer<Runnable> dispatch;
    private final Function<MonteCarloSnapshot, R> reconstruct;
    private final Consumer<R> attach;
    private final Runnable clear;
    private final Runnable changed;
    private final BooleanSupplier busy;
    private R pendingAttachment;
    private Binding binding;
    private AnalysisSnapshot snapshot;
    private AnalysisCompatibilityPolicy.Assessment assessment;
    private long generation;
    private long assessmentGeneration;
    private boolean closed;
    private boolean replacing;
    private boolean loading;
    private boolean assessing;
    private Supplier<Supplier<MonteCarloFingerprintService.Inputs>> pendingInputs;
    private String problem;
    private AcceptedAnalysisCompletion unqueued;
    private AcceptedAnalysisCompletion displayedCompletion;
    private Function<AnalysisSnapshot, AnalysisResultFreshness.Association> association = saved -> AnalysisResultFreshness.Association.UNRECORDED;

    public MonteCarloResultLifecycle(AnalysisResultStore store, Consumer<Runnable> dispatch,
            Function<MonteCarloSnapshot, R> reconstruct, Consumer<R> attach, Runnable clear, Runnable changed) {
        this(store, dispatch, reconstruct, attach, clear, changed, () -> false);
    }
    public MonteCarloResultLifecycle(AnalysisResultStore store, Consumer<Runnable> dispatch,
            Function<MonteCarloSnapshot, R> reconstruct, Consumer<R> attach, Runnable clear, Runnable changed, BooleanSupplier busy) {
        this.store = store; this.dispatch = dispatch; this.reconstruct = reconstruct;
        this.attach = attach; this.clear = clear; this.changed = changed;
        this.busy = busy;
    }
    public Binding binding() { return binding; }
    public void associationResolver(Function<AnalysisSnapshot, AnalysisResultFreshness.Association> resolver) {
        association = Objects.requireNonNull(resolver);
    }
    public AnalysisResultFreshness freshness() {
        return new AnalysisResultFreshness(assessment, snapshot == null
                ? AnalysisResultFreshness.Association.UNRECORDED : association.apply(snapshot));
    }
    /** Invalidate publication immediately; the controller assigns the new file later in its open path. */
    public void unbind() {
        displayedCompletion = null;
        generation++; assessmentGeneration++; binding = null;
        snapshot = null; assessment = null; replacing = false; loading = false; problem = null; unqueued = null; pendingAttachment = null;
        clear.run();
    }
    private AnalysisResultStore.Slot slot() { return new AnalysisResultStore.Slot(binding.planId(), binding.kind()); }
    public void bind(Binding next) {
        if (closed || Objects.equals(next, binding)) return;
        displayedCompletion = null;
        generation++; assessmentGeneration++; binding = next;
        snapshot = null; assessment = null; replacing = false; loading = false; problem = null; unqueued = null; pendingAttachment = null;
        clear.run();
        load();
    }
    public void load() {
        if (closed || binding == null || loading || replacing || snapshot != null) return;
        var captured = binding; long id = generation;
        var memory = store.inMemory(slot());
        if (captured.file() == null && memory.isEmpty()) { changed.run(); return; }
        loading = true; changed.run();
        var read = captured.file() == null
                ? store.background(() -> Optional.of(memory.orElseThrow().presentationSnapshot(new AnalysisSnapshotCodec())))
                : store.load(slot(), captured.file());
        read.thenCompose(found -> store.background(() -> {
            if (found.isEmpty()) return new Loaded<R>(null, null);
            var saved = found.orElseThrow();
            var compatibility = new AnalysisCompatibilityPolicy().assess(saved,
                    new AnalysisFingerprintService.Assessment(saved.inputFingerprint(), saved.contextFingerprint(), null),
                    AnalysisVersions.algorithms(captured.kind()), AnalysisVersions.references(captured.kind()));
            if (compatibility.state() == AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE)
                throw new IllegalArgumentException("Unsupported saved financial meaning.");
            return new Loaded<>(saved, reconstruct.apply((MonteCarloSnapshot) saved.payload()));
        })).whenComplete((loaded, error) -> dispatch.accept(() -> {
            if (!valid(id)) return;
            loading = false;
            var latest = store.inMemory(slot()).orElse(null);
            if (error == null && loaded.snapshot() != null && latest != null
                    && !latest.runId().equals(loaded.snapshot().runId())) {
                load(); return;
            }
            if (error != null) {
                problem = "Incompatible or Unavailable. The historical snapshot was preserved; a new run remains available.";
            } else if (loaded.snapshot() != null) {
                snapshot = loaded.snapshot(); pendingAttachment = loaded.result(); publish();
                refreshAssessment();
            }
            changed.run();
        }));
    }
    private record Loaded<R>(AnalysisSnapshot snapshot, R result) { }

    public void beginReplacement() {
        generation++; loading = false; replacing = true; pendingAttachment = null; changed.run();
    }
    public void finishReplacement() {
        if (closed) return;
        publish();
        if (!replacing) return;
        replacing = false;
        if (snapshot == null) load();
        changed.run();
    }
    private void publish() {
        if (pendingAttachment != null && !busy.getAsBoolean()) {
            var result = pendingAttachment; pendingAttachment = null;
            attach.accept(result);
        }
    }

    /** Called only by the session's eligible-completion branch, never by passive restore. */
    public void accept(AcceptedAnalysisCompletion completion) {
        if (closed || !completion.planId().equals(binding.planId()) || completion.kind() != binding.kind()
                || !Objects.equals(completion.planFile(), binding.file())) return;
        generation++; long id = generation; replacing = false; loading = false; problem = null;
        displayedCompletion = completion;
        snapshot = null; assessment = null; pendingAttachment = null;
        try { store.accept(completion); unqueued = null; }
        catch (RuntimeException unavailable) {
            unqueued = completion;
            problem = "Save Failed — storage admission unavailable. The completed result remains visible; Retry Save does not recalculate.";
            changed.run(); return;
        }
        observeSave(id);
        store.background(() -> completion.presentationSnapshot(new AnalysisSnapshotCodec()))
                .whenComplete((saved, error) -> dispatch.accept(() -> {
                    if (!valid(id)) return;
                    if (error == null) { snapshot = saved; refreshAssessment(); }
                    changed.run();
                }));
        changed.run();
    }

    /** Capture is worker-only and independent of later edits to the controller or view. */
    public static AcceptedAnalysisCompletion completion(Binding binding, MonteCarloSnapshot payload) {
        var inputs = new MonteCarloFingerprintService().storedV1(payload);
        if (!inputs.valid()) throw new IllegalArgumentException("Cannot fingerprint accepted Monte Carlo inputs.");
        var context = new SnapshotContext(binding.kind().shape().name(), payload.display().primaryName(),
                payload.display().spouseName(), payload.startedAt(), List.of(), List.of(), List.of(), Map.of());
        return new AcceptedAnalysisCompletion(binding.planId(), binding.file(), binding.kind(), UUID.randomUUID(),
                payload.completedAt(), inputs.fingerprint(), inputs.contextFingerprint(), AnalysisVersions.algorithms(binding.kind()),
                AnalysisVersions.references(binding.kind()), payload, context);
    }

    public void assess(Supplier<Supplier<MonteCarloFingerprintService.Inputs>> inputs) {
        pendingInputs = inputs; assessmentGeneration++; assessment = null; refreshAssessment(); changed.run();
    }
    private void refreshAssessment() {
        if (closed || snapshot == null || pendingInputs == null || assessing) return;
        var saved = snapshot; var captured = binding; long id = generation; long edit = assessmentGeneration;
        Supplier<MonteCarloFingerprintService.Inputs> inputs;
        try { inputs = pendingInputs.get(); }
        catch (RuntimeException invalid) {
            assessment = new AnalysisCompatibilityPolicy.Assessment(AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION,
                    "Correct the pending analysis inputs. Previous results remain available.");
            changed.run(); return;
        }
        assessing = true;
        store.background(() -> new MonteCarloFingerprintService().compatibility(saved, inputs.get()))
                .whenComplete((next, error) -> dispatch.accept(() -> {
                    assessing = false;
                    if (valid(id) && edit == assessmentGeneration && snapshot == saved) {
                        assessment = error == null ? next : new AnalysisCompatibilityPolicy().unavailable("Cannot assess saved compatibility.");
                        changed.run();
                    } else refreshAssessment();
                }));
    }
    public boolean current() { return assessment != null && assessment.state() == AnalysisCompatibilityPolicy.State.CURRENT; }
    /** Safe historical export is separate from financial currency and durable save success. */
    public boolean exportable() {
        return !closed && binding != null && (snapshot != null || displayedCompletion != null)
                && (assessment == null || assessment.state() != AnalysisCompatibilityPolicy.State.INCOMPATIBLE_OR_UNAVAILABLE);
    }
    public com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport report(
            com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport original) {
        if (!exportable()) throw new IllegalStateException("No compatible completed result is available for export.");
        var frozen = (MonteCarloSnapshot) (snapshot == null ? displayedCompletion.payload() : snapshot.payload());
        var currency = current() ? MonteCarloHistoricalPdf.Currency.CURRENT : assessment == null
                ? MonteCarloHistoricalPdf.Currency.UNASSESSED
                : assessment.state() == AnalysisCompatibilityPolicy.State.INPUTS_NEED_ATTENTION
                ? MonteCarloHistoricalPdf.Currency.INPUTS_NEED_ATTENTION : MonteCarloHistoricalPdf.Currency.OUTDATED;
        var status = store.status(slot()).map(s -> s.durability().name().replace('_', ' '))
                .orElse(snapshot == null ? "MEMORY ONLY" : "SAVED");
        if (unqueued != null) status = "SAVE FAILED";
        return MonteCarloHistoricalPdf.from(original, frozen, currency, status,
                snapshot != null && snapshot.originPlanId() != null, freshness().status());
    }
    public boolean retryAvailable() { return unqueued != null || binding != null && store.status(slot())
            .map(s -> s.durability() == AnalysisResultStore.Durability.SAVE_FAILED).orElse(false); }
    public void retry() {
        if (unqueued != null) { accept(unqueued); return; }
        if (retryAvailable()) { store.retry(slot()); observeSave(generation); changed.run(); }
    }
    private void observeSave(long id) {
        store.whenSettled(slot()).whenComplete((status, error) -> dispatch.accept(() -> { if (valid(id)) changed.run(); }));
    }
    public String status() {
        String text = loading ? "Loading previous results…" : problem != null ? problem : assessment == null
                ? snapshot == null && displayedCompletion == null ? "No Saved Results" : "Assessing completed result inputs…"
                : freshness().status();
        if (freshness().mismatch()) text = "Previous Results — Outdated\n" + text;
        if (snapshot != null) {
            text += "\nAnalysis completed: " + snapshot.completedAt();
            if (snapshot.originPlanId() != null) text += "\nHistorical copy from another plan; original assumptions retained.";
        }
        if (binding != null) {
            var saved = store.status(slot()).orElse(null);
            if (saved != null) text += "\n" + saved.durability().name().replace('_', ' ') + ": " + saved.explanation();
            else if (snapshot != null) text += "\nSaved";
            if (binding.file() == null) text += "\nMemory Only. Save the retirement plan and rerun to persist results. No plan is autosaved.";
        }
        if (replacing && snapshot != null) text += "\nReplacement running — the previous accepted result remains displayed.";
        return text;
    }
    private boolean valid(long id) { return !closed && generation == id; }
    @Override public void close() { closed = true; generation++; pendingAttachment = null; }
}
