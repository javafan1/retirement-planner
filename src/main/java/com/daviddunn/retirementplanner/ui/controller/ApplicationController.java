package com.daviddunn.retirementplanner.ui.controller;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettings;
import com.daviddunn.retirementplanner.domain.baseline.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult;
import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.ProjectionReadiness;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummaryService;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummaryService;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.RetirementPlanRepository;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import com.daviddunn.retirementplanner.app.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;


public class ApplicationController {

    private volatile long sourcePlanRevision;
    private final java.util.List<Runnable> sourcePlanListeners = new java.util.ArrayList<>();
    private final java.util.List<Runnable> analysisBindingListeners = new java.util.ArrayList<>();

    /** File/UUID changes are distinct from financial revisions, including successful first Save and Save As. */
    public Runnable addAnalysisBindingListener(Runnable listener) {
        analysisBindingListeners.add(java.util.Objects.requireNonNull(listener));
        return () -> analysisBindingListeners.remove(listener);
    }
    private void analysisBindingChanged() {
        analysisParameters.bind(currentPlan, currentFile);
        java.util.List.copyOf(analysisBindingListeners).forEach(Runnable::run);
    }

    public long getSourcePlanRevision() {
        return sourcePlanRevision;
    }

    /** UI-thread notification; the returned removal action belongs to the subscriber. */
    public Runnable addSourcePlanRevisionListener(Runnable listener) {
        sourcePlanListeners.add(java.util.Objects.requireNonNull(listener));
        return () -> sourcePlanListeners.remove(listener);
    }

    private void sourcePlanChanged() {
        sourcePlanRevision++;
        java.util.List.copyOf(sourcePlanListeners).forEach(Runnable::run);
    }

    private volatile RetirementPlan currentPlan;
    private ProjectionSummary currentProjectionSummary;
    private Path currentFile;
    private volatile Projection currentProjection;
    private volatile boolean restoredProjectionMode;
    private volatile String projectionSnapshotNotice = "";
    private volatile java.util.concurrent.CompletionStage<Void> projectionRestoration = java.util.concurrent.CompletableFuture.completedFuture(null);
    private com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel currentDetailsChart;

    public java.util.concurrent.CompletionStage<Void> getProjectionRestoration() { return projectionRestoration; }
    public boolean isRestoredProjectionMode() { return restoredProjectionMode; }
    public String getProjectionSnapshotNotice() { return projectionSnapshotNotice; }
    public CompleteProjectionSnapshot getAcceptedProjectionSnapshot() {
        var state = currentPlan == null ? null : currentPlan.getProjectionState();
        return state == null || state.current() == null ? null : currentPlan.getProjectionComponents().get(state.current().id());
    }
    private final java.util.Map<com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind, java.util.UUID> analyzerAdmissions = new java.util.EnumMap<>(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.class);
    private record AssociationNotice(java.util.UUID owner, String text) { }
    private final java.util.Map<com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind, AssociationNotice> associationNotices = new java.util.EnumMap<>(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.class);
    public String getAnalyzerAssociationNotice(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind) {
        var notice = associationNotices.get(kind);
        if (notice != null && notice.owner().equals(getAnalysisSessionId()) && notice.text().startsWith("Historical association accepted")
                && currentPlan.getPendingAnalyzerAssociations().values().stream().noneMatch(c -> c.kind() == kind))
            return "Historical association saved with its original projection.";
        return notice != null && notice.owner().equals(getAnalysisSessionId()) ? notice.text() : "";
    }
    public synchronized AnalyzerAdmission prepareAnalyzerAssociation(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind) {
        return prepareAnalyzerAssociation(kind, null);
    }
    public synchronized AnalyzerAdmission prepareAnalyzerAssociation(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind kind, String requestFingerprint) {
        var ticket = java.util.UUID.randomUUID(); analyzerAdmissions.put(kind, ticket);
        var projection = getAcceptedProjectionSnapshot();
        if (projection == null || !currentProjectionInputsMatch() || (kind.shape() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.Shape.COUPLE) != currentPlan.getHousehold().hasSpouse()) return null;
        return new AnalyzerAdmission(getAnalysisSessionId(), projection.id(), projection.inputDigest(), kind,
                projectionEpoch.get(), sourcePlanRevision, ticket, requestFingerprint);
    }
    /** Called only at an eligible result acceptance, never passive restoration. Save commits the immutable pin. */
    public synchronized boolean associateAnalyzer(AnalyzerAdmission admission, com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion completion) {
        if (admission == null) {
            if (completion.planId().equals(getAnalysisSessionId())) associationNotices.put(completion.kind(), new AssociationNotice(completion.planId(),
                    "Last-success result has no unchanged accepted projection admission. Existing historical pins are retained independently."));
            return false;
        }
        if (admission.kind() != completion.kind() || !admission.planId().equals(completion.planId())
                || !completion.inputFingerprint().equals(admission.requestFingerprint())
                || !admission.ticket().equals(completion.runId())
                || !admission.planId().equals(getAnalysisSessionId()) || admission.projectionGeneration() != projectionEpoch.get()
                || admission.sourceRevision() != sourcePlanRevision || !admission.ticket().equals(analyzerAdmissions.get(admission.kind()))
                || !currentProjectionInputsMatch() || !admission.projectionId().equals(getAcceptedProjectionSnapshot().id())
                || !admission.projectionInputDigest().equals(getAcceptedProjectionSnapshot().inputDigest())) return false;
        var state = currentPlan.getProjectionState();
        var refs = new java.util.ArrayList<>(state.associations());
        refs.removeIf(a -> a.projectionId().equals(admission.projectionId()) && a.kind() == admission.kind());
        if (refs.size() >= 30) {
            associationNotices.put(completion.kind(), new AssociationNotice(completion.planId(),
                    "Historical association capacity reached (30). Existing pins retained; this last-success result is not historically pinned."));
            return false;
        }
        refs.add(new AnalyzerAssociation(1, admission.planId(), admission.projectionId(), admission.projectionInputDigest(),
                completion.kind(), completion.runId(), completion.inputFingerprint(), completion.contextFingerprint(),
                "0".repeat(64), "0".repeat(64), completion.completedAt()));
        var pending = new java.util.LinkedHashMap<>(currentPlan.getPendingAnalyzerAssociations());
        pending.put(completion.runId(), completion);
        pending.keySet().removeIf(id -> refs.stream().noneMatch(a -> a.runId().equals(id)));
        currentPlan.installPendingAnalyzerAssociations(pending);
        var projectionPins = new java.util.LinkedHashMap<>(currentPlan.getPendingPinnedProjections());
        projectionPins.put(admission.projectionId(), getAcceptedProjectionSnapshot());
        currentPlan.installPendingPinnedProjections(projectionPins);
        currentPlan.setProjectionState(new ProjectionRoleManifest(2, state.owner(), state.current(), state.baseline(), refs, state.pinnedProjections()));
        analyzerAdmissions.remove(admission.kind());
        associationNotices.put(completion.kind(), new AssociationNotice(completion.planId(), "Historical association accepted in memory. Ordinary plan Save commits the immutable pin."));
        return true;
    }
    public java.util.List<AnalyzerAssociation> getAnalyzerAssociations(java.util.UUID projectionId) {
        var state = currentPlan.getProjectionState();
        return state == null ? java.util.List.of() : state.associations().stream().filter(a -> a.projectionId().equals(projectionId)).toList();
    }
    public java.util.concurrent.CompletionStage<com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot> loadAssociatedAnalyzer(AnalyzerAssociation reference) {
        var owner = currentPlan; var state = owner.getProjectionState(); var file = owner.getProjectionComponentSource();
        if (state == null || !state.associations().contains(reference)) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("Association is not attached to this plan."));
        var pending = owner.getPendingAnalyzerAssociations().get(reference.runId());
        var memory = owner.getAssociatedAnalyzerResults().get(reference.runId());
        var projection = owner.getProjectionComponents().containsKey(reference.projectionId()) ? owner.getProjectionComponents().get(reference.projectionId())
                : owner.getPendingPinnedProjections().get(reference.projectionId());
        var projectionReference = state.current() != null && state.current().id().equals(reference.projectionId()) ? state.current()
                : state.baseline() != null && state.baseline().id().equals(reference.projectionId()) ? state.baseline()
                : state.pinnedProjections().stream().filter(p -> p.id().equals(reference.projectionId())).findFirst().orElseThrow();
        return analysisResults.background(() -> {
            var store = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
            var original = projection != null ? projection : store.read(file, reference.owner(), projectionReference);
            if (!original.id().equals(reference.projectionId()) || !original.owner().equals(reference.owner())
                    || !original.inputDigest().equals(reference.projectionInputDigest())) throw new IllegalArgumentException("Analyzer projection provenance mismatch.");
            if ((reference.kind().shape() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.Shape.COUPLE)
                    != original.restoreInputs().getHousehold().hasSpouse()) throw new IllegalArgumentException("Analyzer projection household mismatch.");
            return store.resolveAnalyzer(reference, pending, memory, file);
        });
    }
    public boolean currentProjectionInputsMatch() {
        var snapshot = getAcceptedProjectionSnapshot();
        return snapshot != null && currentProjection != null && snapshot.matches(currentPlan);
    }

    /** Read-only presentation of recorded provenance, independent of execution revisions and durability. */
    public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultFreshness.Association analysisAssociation(
            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSnapshot saved) {
        var projection = getAcceptedProjectionSnapshot();
        var manifest = currentPlan.getProjectionState();
        return com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultFreshness.association(saved,
                getAnalysisSessionId(), projection == null ? null : projection.id(),
                projection == null ? null : projection.inputDigest(), currentProjectionInputsMatch(),
                manifest == null ? java.util.List.of() : manifest.associations());
    }

    private long swapSequence;
    private boolean swapPreparing;
    private boolean breakEvenExplicitOnly;
    private boolean projectionSwapNeedsSave;

    private void projectionRolesSaved() {
        if (projectionSwapNeedsSave) {
            projectionSwapNeedsSave = false;
            projectionSnapshotNotice = "Current and baseline projection roles saved with their original results. Break-Even requires an explicit request.";
        }
    }

    /** Cache-only readiness; component validation is performed on the bounded worker. */
    public synchronized String projectionSwapUnavailableReason() {
        var state = currentPlan == null ? null : currentPlan.getProjectionState();
        if (swapPreparing) return "A projection swap is already being prepared.";
        if (!projectionRestoration.toCompletableFuture().isDone()) return "Wait for original projection restoration.";
        if (state == null || state.current() == null || state.baseline() == null || currentPlan.getBaseline() == null)
            return "Complete saved current and baseline projection snapshots are required. Legacy input-only baselines cannot be swapped.";
        if (!state.owner().equals(getAnalysisSessionId()) || !state.baseline().id().equals(currentPlan.getBaseline().getProjectionSnapshotId()))
            return "Projection ownership or baseline references are inconsistent.";
        if (!currentProjectionInputsMatch()) return "Run and accept a successful projection matching the configured inputs before swapping. Saving alone does not make a stale projection current.";
        if (!currentPlan.getProjectionComponents().containsKey(state.baseline().id())) return "Original baseline results are unavailable.";
        if (analysisParameters.needsDrain() || !snapshotCopy.toCompletableFuture().isDone())
            return "Complete or retry pending parameter saves and historical copying before swapping.";
        if (com.daviddunn.retirementplanner.ui.MainApplication.analysisJobs().isBusy()) return "Wait for the active Social Security analysis to finish or cancel.";
        return "";
    }

    /** An opaque prepared transaction; callers cannot access or mutate its detached target graphs. */
    public static final class PreparedProjectionSwap {
        private final RetirementPlan owner;
        private final ProjectionRoleManifest manifest;
        private final ProjectionBaseline baseline;
        private final java.nio.file.Path file;
        private final long epoch, revision, sequence;
        private final boolean dirty;
        private final RetirementPlan target;
        private final RestoredProjection nextCurrent, nextBaseline;
        private PreparedProjectionSwap(RetirementPlan owner, ProjectionRoleManifest manifest, ProjectionBaseline baseline,
                java.nio.file.Path file, long epoch, long revision, long sequence, boolean dirty,
                RetirementPlan target, RestoredProjection nextCurrent, RestoredProjection nextBaseline) {
            this.owner = owner; this.manifest = manifest; this.baseline = baseline; this.file = file;
            this.epoch = epoch; this.revision = revision; this.sequence = sequence; this.dirty = dirty;
            this.target = target; this.nextCurrent = nextCurrent; this.nextBaseline = nextBaseline;
        }
    }

    /** No engine work, no writes, and no publication on this worker. */
    public synchronized java.util.concurrent.CompletionStage<PreparedProjectionSwap> prepareProjectionSwap() {
        String reason = projectionSwapUnavailableReason();
        if (!reason.isEmpty()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(reason));
        if (modified) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Save the plan before swapping projections."));
        var owner = currentPlan; var manifest = owner.getProjectionState(); var baseline = owner.getBaseline();
        var file = currentFile; long epoch = projectionEpoch.get(), revision = sourcePlanRevision, sequence = ++swapSequence;
        var components = owner.getProjectionComponents();
        swapPreparing = true;
        return analysisResults.background(() -> {
            var store = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents();
            var verified = new java.util.LinkedHashMap<java.util.UUID, CompleteProjectionSnapshot>();
            for (var reference : new ProjectionRoleManifest.Reference[]{manifest.current(), manifest.baseline()}) {
                var memory = components.get(reference.id());
                var snapshot = reference.digest().equals("0".repeat(64)) ? memory : store.read(owner.getProjectionComponentSource(), manifest.owner(), reference);
                if (snapshot == null || !snapshot.owner().equals(manifest.owner()) || !snapshot.id().equals(reference.id())
                        || memory != null && !memory.equals(snapshot)) throw new IllegalStateException("Projection component validation failed.");
                if (memory != null) snapshot = memory; // Verified identical immutable data; do not retain another large DTO graph.
                if (reference.digest().equals("0".repeat(64))) store.validatePendingProjection(snapshot);
                if (!CompleteProjectionSnapshot.freeze(snapshot.restoreInputs()).equals(snapshot.inputsDocument()))
                    throw new IllegalStateException("Original projection inputs cannot be restored exactly.");
                verified.put(snapshot.id(), snapshot);
            }
            var originalCurrent = verified.get(manifest.current().id());
            var originalBaseline = verified.get(manifest.baseline().id());
            if (!originalBaseline.inputsDocument().equals(CompleteProjectionSnapshot.freeze(baseline.getSnapshot())))
                throw new IllegalStateException("Original baseline inputs and results do not match.");
            // Validate original role-associated artifacts sequentially, without retaining decoded graphs.
            for (var association : manifest.associations()) {
                var original = verified.get(association.projectionId());
                if (original == null) continue; // Earlier pinned projections do not change roles.
                if (!association.projectionInputDigest().equals(original.inputDigest())
                        || (association.kind().shape() == com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.Shape.COUPLE)
                            != original.restoreInputs().getHousehold().hasSpouse())
                    throw new IllegalStateException("Historical analyzer projection binding is inconsistent.");
                store.resolveAnalyzer(association, owner.getPendingAnalyzerAssociations().get(association.runId()),
                        owner.getAssociatedAnalyzerResults().get(association.runId()), owner.getProjectionComponentSource());
            }
            var inputs = originalBaseline.restoreInputs();
            var target = new RetirementPlan(inputs.getHousehold(), inputs.getAccountPortfolio(), inputs.getPlanningAssumptions(),
                    inputs.getRothConversionRequest(), inputs.getNonInvestableAssets(), owner.getPlanId());
            target.setBaseline(new ProjectionBaseline(originalCurrent.restoreInputs(),
                    java.time.LocalDateTime.ofInstant(originalCurrent.acceptedAt(), java.time.ZoneId.systemDefault()),
                    "Original accepted projection " + originalCurrent.id(), originalCurrent.id()));
            // Exchange references directly: neither archive new pins nor alter immutable provenance.
            target.setProjectionState(new ProjectionRoleManifest(manifest.version(), manifest.owner(), manifest.baseline(), manifest.current(),
                    manifest.associations(), manifest.pinnedProjections()));
            target.installProjectionComponents(verified);
            target.setProjectionComponentSource(owner.getProjectionComponentSource());
            if (!originalBaseline.matches(target)) throw new IllegalStateException("Restored financial inputs differ from the accepted document.");
            return new PreparedProjectionSwap(owner, manifest, baseline, file, epoch, revision, sequence, false, target,
                    RestoredProjection.prepare(originalBaseline), RestoredProjection.prepare(originalCurrent));
        }).whenComplete((prepared, failure) -> {
            if (failure != null) synchronized (ApplicationController.this) { if (swapSequence == sequence) swapPreparing = false; }
        });
    }

    public synchronized void cancelProjectionSwap() { ++swapSequence; swapPreparing = false; }

    public static final class ProjectionSwapSave {
        private final RetirementPlan owner, committed;
        private final Path file;
        private final long revision, epoch;
        private final String inputs;
        private final String planId;
        private final ProjectionRoleManifest manifest;
        private final ProjectionBaseline baseline;
        private final com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations configurations;
        private ProjectionSwapSave(RetirementPlan owner, RetirementPlan committed, Path file, long revision, long epoch,
                String inputs, String planId, ProjectionRoleManifest manifest, ProjectionBaseline baseline,
                com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfigurations configurations) {
            this.owner = owner; this.committed = committed; this.file = file; this.revision = revision; this.epoch = epoch;
            this.inputs = inputs; this.planId = planId; this.manifest = manifest; this.baseline = baseline; this.configurations = configurations;
        }
    }

    /** Same ordinary repository Save transaction, with captured binding and owner-thread finalization. */
    public synchronized java.util.concurrent.CompletionStage<ProjectionSwapSave> saveBeforeProjectionSwap() {
        if (currentFile == null) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Save the plan explicitly first."));
        var owner = currentPlan; var file = currentFile; var revision = sourcePlanRevision; var epoch = projectionEpoch.get();
        var inputs = CompleteProjectionSnapshot.freeze(owner); var baseline = owner.getBaseline();
        var baselineInputs = baseline == null ? null : CompleteProjectionSnapshot.freeze(baseline.getSnapshot());
        var manifest = owner.getProjectionState(); var configurations = owner.getAnalyzerConfigurations();
        var components = owner.getProjectionComponents(); var pending = owner.getPendingAnalyzerAssociations();
        var pinned = owner.getPendingPinnedProjections(); var analyzers = owner.getAssociatedAnalyzerResults();
        var source = owner.getProjectionComponentSource(); var identity = owner.getPlanId();
        return analysisResults.background(() -> {
            var roots = CompleteProjectionSnapshot.restoreInputs(inputs);
            var copy = new RetirementPlan(roots.getHousehold(), roots.getAccountPortfolio(), roots.getPlanningAssumptions(),
                    roots.getRothConversionRequest(), roots.getNonInvestableAssets(), identity);
            if (baseline != null) copy.setBaseline(new ProjectionBaseline(CompleteProjectionSnapshot.restoreInputs(baselineInputs),
                    baseline.getSavedAt(), baseline.getDescription(), baseline.getProjectionSnapshotId()));
            if (configurations != null) copy.setAnalyzerConfigurations(configurations);
            copy.setProjectionState(manifest); copy.installProjectionComponents(components); copy.installPendingAnalyzerAssociations(pending);
            copy.installPendingPinnedProjections(pinned); copy.installAssociatedAnalyzerResults(analyzers); copy.setProjectionComponentSource(source);
            // The ordinary atomic transaction may materialize/clear its argument's transient maps.
            // Never pass the live object: a later accepted analysis must not be cleared by this Save.
            repository.save(copy, file);
            return new ProjectionSwapSave(owner, copy, file, revision, epoch, inputs, identity, manifest, baseline, configurations);
        });
    }

    public synchronized void finishProjectionSwapSave(ProjectionSwapSave saved) {
        if (currentPlan != saved.owner || !java.util.Objects.equals(currentFile, saved.file)
                || !java.util.Objects.equals(currentPlan.getPlanId(), saved.planId)
                || sourcePlanRevision != saved.revision || projectionEpoch.get() != saved.epoch
                || currentPlan.getProjectionState() != saved.manifest || currentPlan.getBaseline() != saved.baseline
                || currentPlan.getAnalyzerConfigurations() != saved.configurations
                || !saved.inputs.equals(CompleteProjectionSnapshot.freeze(currentPlan)))
            throw new IllegalStateException("The plan changed during Save. The original file operation completed, but nothing was swapped.");
        var committed = saved.committed;
        currentPlan.assignPlanId(RetirementPlan.validatedPlanId(committed.getPlanId()));
        currentPlan.setProjectionState(committed.getProjectionState()); currentPlan.installProjectionComponents(committed.getProjectionComponents());
        currentPlan.installPendingAnalyzerAssociations(committed.getPendingAnalyzerAssociations());
        currentPlan.installPendingPinnedProjections(committed.getPendingPinnedProjections());
        currentPlan.installAssociatedAnalyzerResults(committed.getAssociatedAnalyzerResults());
        currentPlan.setProjectionComponentSource(committed.getProjectionComponentSource());
        analysisParameters.saved(currentPlan, currentFile); modified = false;
        projectionRolesSaved();
        applicationSettings.setLastOpenedPlan(currentFile.toString()); saveSettingsQuietly(); collectSaveWarnings(); analysisBindingChanged();
    }

    /** Called by the application/FX owner. All throwing reconstruction occurred before this boundary. */
    public synchronized boolean commitProjectionSwap(PreparedProjectionSwap prepared) {
        java.util.Objects.requireNonNull(prepared);
        if (prepared.sequence != swapSequence) throw new IllegalStateException("This swap preparation is obsolete. Nothing was swapped.");
        if (!swapPreparing || prepared.sequence != swapSequence || currentPlan != prepared.owner
                || !prepared.manifest.owner().equals(getAnalysisSessionId())
                || currentPlan.getProjectionState() != prepared.manifest || currentPlan.getBaseline() != prepared.baseline
                || !java.util.Objects.equals(currentFile, prepared.file) || projectionEpoch.get() != prepared.epoch
                || sourcePlanRevision != prepared.revision || modified != prepared.dirty || !currentProjectionInputsMatch()
                || analysisParameters.needsDrain() || com.daviddunn.retirementplanner.ui.MainApplication.analysisJobs().isBusy()) {
            cancelProjectionSwap(); throw new IllegalStateException("The plan, inputs, analysis, or projection changed while preparing the swap. Nothing was swapped.");
        }
        swapPreparing = false;
        if (prepared.manifest.current().id().equals(prepared.manifest.baseline().id())) return false;
        var target = prepared.target;
        // Retain plan-owned nonfinancial settings and accepted pending artifacts, not historical input copies.
        if (currentPlan.getAnalyzerConfigurations() != null) target.setAnalyzerConfigurations(currentPlan.getAnalyzerConfigurations());
        target.installPendingAnalyzerAssociations(currentPlan.getPendingAnalyzerAssociations());
        target.installAssociatedAnalyzerResults(currentPlan.getAssociatedAnalyzerResults());
        target.installPendingPinnedProjections(currentPlan.getPendingPinnedProjections());
        var current = prepared.nextCurrent; var baseline = prepared.nextBaseline;
        projectionEpoch.incrementAndGet(); analyzerAdmissions.clear();
        currentProjectionSummary = current.summary(); currentNonInvestableAssetProjections = current.nonInvestable();
        currentProjectionAssumptions = current.snapshot().people(); currentDetailsChart = current.chart();
        currentDetailsContext = new com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession.Accepted(
                current.inputs(), current.snapshot().id(), target.getPlanId(), current.snapshot().acceptedAt(), sourcePlanRevision + 1);
        currentProjection = current.projection(); currentFundingFailure = null;
        baselineProjection = baseline.projection(); baselineNonInvestableAssetProjections = baseline.nonInvestable();
        baselineProjectionAssumptions = baseline.snapshot().people(); baselineDetailsInputs = baseline.inputs(); baselineDetailsChart = baseline.chart();
        cachedBaselineIdentity = target.getBaseline();
        detailsComparison = null; detailsComparisonCurrent = null; detailsComparisonBaseline = null; detailsComparisonOwner = null;
        insightCurrentProjection = null; insightBaselineProjection = null; insightBaselineIdentity = null; breakEvenExplicitOnly = true;
        restoredProjectionMode = true; projectionRestoration = java.util.concurrent.CompletableFuture.completedFuture(null);
        projectionSnapshotNotice = "Current and baseline projections swapped from original saved results. Save the plan to keep this arrangement. Break-Even requires an explicit request.";
        currentPlan = target; modified = true;
        if (longevitySettingsOwner == prepared.owner) longevitySettingsOwner = target;
        projectionSwapNeedsSave = true;
        analysisParameters.bind(target, currentFile);
        sourcePlanChanged();
        return true;
    }

    public boolean canRequestBreakEven() {
        return currentPlan != null && currentPlan.getBaseline() != null && currentProjection != null && baselineProjection != null
                && cachedBaselineIdentity == currentPlan.getBaseline();
    }
    public BreakEvenAnalysisResult requestBreakEvenAnalysis() {
        return computeBreakEvenAnalysis();
    }
    public com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel getAcceptedProjectionChart(Projection displayed) {
        return displayed == currentProjection ? currentDetailsChart : null;
    }

    private void attachSnapshot(CompleteProjectionSnapshot snapshot) {
        var previous = currentPlan.getProjectionState();
        var baseline = previous == null ? null : previous.baseline();
        if (currentPlan.getBaseline() == null || baseline != null
                && !baseline.id().equals(currentPlan.getBaseline().getProjectionSnapshotId())) baseline = null;
        var components = new java.util.LinkedHashMap<java.util.UUID, CompleteProjectionSnapshot>();
        if (baseline != null && currentPlan.getProjectionComponents().containsKey(baseline.id()))
            components.put(baseline.id(), currentPlan.getProjectionComponents().get(baseline.id()));
        components.put(snapshot.id(), snapshot);
        currentPlan.installProjectionComponents(components);
        currentPlan.setProjectionState(previous == null ? new ProjectionRoleManifest(1, snapshot.owner(),
                new ProjectionRoleManifest.Reference(snapshot.id(), "0".repeat(64)), baseline)
                : previous.withRoles(snapshot.owner(), new ProjectionRoleManifest.Reference(snapshot.id(), "0".repeat(64)), baseline));
    }

    private record RestoredProjection(CompleteProjectionSnapshot snapshot, Projection projection,
            ProjectionSummary summary, List<NonInvestableAssetProjection> nonInvestable,
            com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel chart,
            com.daviddunn.retirementplanner.app.baseline.ProjectionInputs inputs) {
        static RestoredProjection prepare(CompleteProjectionSnapshot snapshot) {
            if (snapshot == null) return null;
            var projection = snapshot.annual().restoreProjection();
            return new RestoredProjection(snapshot, projection, snapshot.summary().restore(projection),
                    snapshot.restoreNonInvestable(), snapshot.chart(),
                    com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.capture(snapshot.restoreInputs()));
        }
    }
    private record RestoredComponents(java.util.Map<java.util.UUID, CompleteProjectionSnapshot> objects,
            RestoredProjection current, RestoredProjection baseline) { }

    private synchronized void restoreProjectionSnapshots() {
        var owner = currentPlan;
        var manifest = owner.getProjectionState();
        var selectedBaseline = owner.getBaseline();
        String baselineInputs = selectedBaseline == null ? null : CompleteProjectionSnapshot.freeze(selectedBaseline.getSnapshot());
        restoredProjectionMode = true;
        breakEvenExplicitOnly = true;
        if (manifest == null) {
            projectionRestoration = java.util.concurrent.CompletableFuture.completedFuture(null);
            projectionSnapshotNotice = "Original projection results were not recorded. Run a projection explicitly to calculate new results.";
            return;
        }
        var source = owner.getProjectionComponentSource();
        long epoch = projectionEpoch.get();
        var ready = new java.util.concurrent.CompletableFuture<Void>();
        projectionRestoration = ready;
        projectionSnapshotNotice = "Loading original projection results…";
        analysisResults.background(() -> {
            var objects = new com.daviddunn.retirementplanner.persistence.ProjectionSnapshotComponents().load(source, manifest);
            if (manifest.baseline() != null && (selectedBaseline == null
                    || !manifest.baseline().id().equals(selectedBaseline.getProjectionSnapshotId())
                    || !objects.get(manifest.baseline().id()).inputsDocument().equals(baselineInputs)))
                throw new IllegalArgumentException("Baseline input/result association is inconsistent.");
            return new RestoredComponents(objects,
                    RestoredProjection.prepare(manifest.current() == null ? null : objects.get(manifest.current().id())),
                    RestoredProjection.prepare(manifest.baseline() == null ? null : objects.get(manifest.baseline().id())));
        }).whenComplete((prepared, failure) -> {
                    try {
                        synchronized (this) {
                            if (currentPlan != owner || owner.getProjectionState() != manifest
                                    || owner.getBaseline() != selectedBaseline || projectionEpoch.get() != epoch) return;
                            if (failure != null) { projectionSnapshotNotice = "Original projection results are unavailable: component validation failed. Saved references were preserved."; return; }
                            owner.installProjectionComponents(prepared.objects());
                            var baseline = prepared.baseline();
                            if (baseline != null) {
                                cachedBaselineIdentity = selectedBaseline;
                                baselineProjection = baseline.projection();
                                baselineNonInvestableAssetProjections = baseline.nonInvestable();
                                baselineProjectionAssumptions = baseline.snapshot().people();
                                baselineDetailsInputs = baseline.inputs();
                                baselineDetailsChart = baseline.chart();
                            }
                            var current = prepared.current();
                            if (current != null) {
                                currentProjectionSummary = current.summary();
                                currentNonInvestableAssetProjections = current.nonInvestable();
                                currentProjectionAssumptions = current.snapshot().people();
                                currentDetailsChart = current.chart();
                                currentDetailsContext = new com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession.Accepted(
                                        current.inputs(), current.snapshot().id(), current.snapshot().owner().toString(),
                                        current.snapshot().acceptedAt(), sourcePlanRevision);
                                currentProjection = current.projection();
                            }
                            projectionSnapshotNotice = current == null ? "Original current projection results were not recorded."
                                    : current.snapshot().matches(owner) ? "Original projection restored from saved results."
                                    : "Outdated original projection — editable inputs differ from the accepted inputs.";
                        }
                    } finally { ready.complete(null); }
                });
    }
    private final java.util.concurrent.atomic.AtomicLong projectionEpoch = new java.util.concurrent.atomic.AtomicLong();
    private volatile com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession.Accepted currentDetailsContext;
    private com.daviddunn.retirementplanner.app.baseline.ProjectionInputs baselineDetailsInputs;
    private com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel baselineDetailsChart;
    private ProjectionComparison detailsComparison;
    private Projection detailsComparisonCurrent;
    private ProjectionBaseline detailsComparisonBaseline;
    private RetirementPlan detailsComparisonOwner;
    private FundingFailure currentFundingFailure;
    private Projection baselineProjection;
    private ProjectionBaseline cachedBaselineIdentity;
    private BreakEvenPlanSummary baselineProjectionAssumptions;
    private BreakEvenPlanSummary currentProjectionAssumptions;
    private List<NonInvestableAssetProjection> currentNonInvestableAssetProjections;
    private RetirementPlan longevitySettingsOwner;
    private com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings longevitySettings;

    public com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings getLongevitySessionSettings() {
        if (longevitySettingsOwner != currentPlan || longevitySettings == null) {
            longevitySettingsOwner = currentPlan;
            longevitySettings = com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings
                    .defaults(currentPlan.getPlanningAssumptions().getProjectionStartDate());
        }
        return longevitySettings;
    }

    public void setLongevitySessionSettings(com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings settings) {
        longevitySettingsOwner = currentPlan;
        longevitySettings = java.util.Objects.requireNonNull(settings);
    }

    public BreakEvenContext prepareBreakEvenContext(BreakEvenAnalysisResult result) {
        return new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory()
                .create(result, getLongevitySessionSettings());
    }

    private List<NonInvestableAssetProjection>
            baselineNonInvestableAssetProjections;

    private final BaselineProjectionService
            baselineProjectionService;

    private final ProjectionComparisonService
            projectionComparisonService;

    private final ProjectionEngine projectionEngine;
    private final ProjectionSummaryService projectionSummaryService;
    private final RetirementPlanRepository repository;
    private final com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator analysisParameters;

    public com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator getAnalysisParameters() {
        analysisParameters.bind(currentPlan, currentFile);
        return analysisParameters;
    }
    private final com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore analysisResults;
    private java.util.concurrent.CompletionStage<List<String>> snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of());

    public java.util.concurrent.CompletionStage<List<String>> getSnapshotCopyCompletion() { return snapshotCopy; }

    public com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore getAnalysisResults() { return analysisResults; }

    public boolean canPersistAnalysisResults() { return currentFile != null && currentPlan.getPlanId() != null; }
    private RetirementPlan analysisSessionOwner;
    private java.util.UUID legacyAnalysisSession;
    public java.util.UUID getAnalysisSessionId() {
        if (currentPlan.getPlanId() != null) return RetirementPlan.validatedPlanId(currentPlan.getPlanId());
        if (analysisSessionOwner != currentPlan) {
            analysisSessionOwner = currentPlan; legacyAnalysisSession = java.util.UUID.randomUUID();
        }
        return legacyAnalysisSession;
    }
    private boolean modified;
    private final java.util.List<String> persistenceWarnings = new java.util.ArrayList<>();
    private Path failedOpenPath;
    private Path recoveredOriginalPath;
    private Path recoveredBackupPath;

    private final JsonApplicationSettingsRepository
            applicationSettingsRepository;

    private final ApplicationSettings
            applicationSettings;

    private final NonInvestableAssetProjectionService
            nonInvestableAssetProjectionService;

    public ApplicationController() {
        this(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository());
    }

    public ApplicationController(RetirementPlanRepository repository,
            JsonApplicationSettingsRepository settingsRepository) {
        this(repository, settingsRepository,
                com.daviddunn.retirementplanner.app.socialsecurity.results.ApplicationAnalysisResults.store());
    }

    public ApplicationController(RetirementPlanRepository repository,
            JsonApplicationSettingsRepository settingsRepository,
            com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore analysisResults) {

        this.analysisResults = java.util.Objects.requireNonNull(analysisResults);

        projectionEngine =
                new ProjectionEngine();

        projectionSummaryService =
                new ProjectionSummaryService(
                        new ProjectionStatisticsService(),
                        new IncomeSummaryService());

        nonInvestableAssetProjectionService =
                new NonInvestableAssetProjectionService();

        baselineProjectionService =
                new BaselineProjectionService(
                        projectionEngine);

        projectionComparisonService =
                new ProjectionComparisonService();

        this.repository = java.util.Objects.requireNonNull(repository);
        analysisParameters = new com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterCoordinator(
                repository instanceof JsonRetirementPlanRepository json ? json : null);

        applicationSettingsRepository = java.util.Objects.requireNonNull(settingsRepository);

        applicationSettings =
                applicationSettingsRepository.load();
        persistenceWarnings.addAll(applicationSettingsRepository.takeWarnings());

        newPlan();
    }

    public List<NonInvestableAssetProjection>
    getCurrentNonInvestableAssetProjections() {

        if (currentNonInvestableAssetProjections != null) {
            return currentNonInvestableAssetProjections;
        }

        RetirementPlan plan =
                getCurrentPlan();

        Projection projection =
                getCurrentProjection();

        if (currentNonInvestableAssetProjections != null) return currentNonInvestableAssetProjections;
        if (plan == null
                || projection == null) {

            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        currentNonInvestableAssetProjections = nonInvestableAssetProjectionService
                .project(
                        plan.getNonInvestableAssets(),
                        firstYear,
                        lastYear);
        return currentNonInvestableAssetProjections;
    }

    public RetirementPlan getCurrentPlan() {
        return currentPlan;
    }

    /**
     * Existing UI can continue using this.
     */
    public Projection getCurrentProjection() {

        if (restoredProjectionMode) return currentProjection;

        if (!isCurrentPlanReadyForProjection()) {
            return null;
        }

        if (currentFundingFailure != null) {
            return null;
        }

        if (currentProjection == null) {
            var runPlan = currentPlan;
            long revision = sourcePlanRevision;
            long epoch = projectionEpoch.incrementAndGet();
            var inputs = com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.capture(runPlan);
            String inputDocument = CompleteProjectionSnapshot.freeze(runPlan);
            var frozen = CompleteProjectionSnapshot.restoreInputs(inputDocument);
            var executionPlan = new RetirementPlan(frozen.getHousehold(), frozen.getAccountPortfolio(),
                    frozen.getPlanningAssumptions(), frozen.getRothConversionRequest(), frozen.getNonInvestableAssets());
            java.util.UUID ownerIdentity = getAnalysisSessionId();
            // Preserve the established normal-result account identities. The synchronous engine
            // receives the same live graph as before; detached metadata and the post-run guards
            // prevent publishing a result whose effective input state changed during execution.
            var outcome = projectionEngine.projectWithOutcome(runPlan);
            CompleteProjectionSnapshot complete = null;
            ProjectionSummary completedSummary = null;
            Projection completedProjection = null;
            com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel completedChart = null;
            if (outcome instanceof ProjectionExecutionResult.Completed done) {
                var projection = done.projection();
                completedProjection = projection;
                completedSummary = projectionSummaryService.summarize(executionPlan, projection);
                var nonInvestable = nonInvestableAssetProjectionService.project(executionPlan.getNonInvestableAssets(), projection.getStartYear(), projection.getEndYear());
                complete = new CompleteProjectionSnapshot(1, java.util.UUID.randomUUID(), ownerIdentity, ownerIdentity, java.time.Instant.now(),
                        inputDocument, CompleteProjectionSnapshot.digest(inputDocument), "Accepted normal projection; retained schema 1; original build version not recorded",
                        RetainedProjectionSnapshot.capture(projection), nonInvestable.stream().map(CompleteProjectionSnapshot.NonInvestableYear::capture).toList(),
                        CompleteProjectionSnapshot.Summary.capture(completedSummary), BreakEvenPlanSummary.from(executionPlan.getHousehold()));
                completedChart = complete.chart(); // Validate/detach before publishing any accepted state.
            }
            // Normal projection is synchronous today. The token also rejects reentrant/late work
            // after invalidation, a newer calculation or a plan switch, without another scheduler.
            synchronized (this) {
                if (currentPlan != runPlan || sourcePlanRevision != revision || projectionEpoch.get() != epoch) return currentProjection;
                if (!inputDocument.equals(CompleteProjectionSnapshot.freeze(runPlan))) return currentProjection;
                if (outcome instanceof ProjectionExecutionResult.InsufficientFunds insufficient) {
                    currentFundingFailure = insufficient.fundingFailure();
                    return null; // Never expose a completed-year prefix as a complete projection.
                }
                currentProjectionAssumptions = complete.people();
                currentDetailsContext = new com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession.Accepted(
                        inputs, complete.id(), runPlan.getPlanId(), complete.acceptedAt(), revision);
                attachSnapshot(complete);
                currentProjectionSummary = completedSummary;
                currentNonInvestableAssetProjections = complete.restoreNonInvestable();
                currentDetailsChart = completedChart;
                currentProjection = completedProjection;
            }
        }

        return currentProjection;
    }

    /** Read-only cache lookup; never starts financial work on the JavaFX thread. */
    public Projection peekCurrentProjection() {
        return currentProjection;
    }

    /** Read-only cached failure, cleared with projection invalidation; never starts another projection. */
    public java.util.Optional<FundingFailure> getCurrentFundingFailure() {
        return java.util.Optional.ofNullable(currentFundingFailure);
    }

    public boolean isCurrentPlanReadyForProjection() {

        return ProjectionReadiness.isReady(
                currentPlan);
    }

    public Projection getBaselineProjection() {

        ProjectionBaseline selectedBaseline = currentPlan == null ? null : currentPlan.getBaseline();
        if (cachedBaselineIdentity != selectedBaseline) {
            baselineProjection = null;
            baselineNonInvestableAssetProjections = null;
            baselineProjectionAssumptions = null;
            baselineDetailsInputs = null;
            baselineDetailsChart = null;
            cachedBaselineIdentity = selectedBaseline;
        }

        if (baselineProjection == null
                && currentPlan != null
                && currentPlan.getBaseline() != null) {

            var originalId = selectedBaseline.getProjectionSnapshotId();
            if (originalId != null) {
                var original = currentPlan.getProjectionComponents().get(originalId);
                if (original == null) return null;
                baselineProjection = original.annual().restoreProjection();
                baselineNonInvestableAssetProjections = original.restoreNonInvestable();
                baselineProjectionAssumptions = original.people();
                baselineDetailsInputs = com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.capture(original.restoreInputs());
                baselineDetailsChart = original.chart();
                return baselineProjection;
            }
            if (restoredProjectionMode) return null; // Legacy results cannot be recovered by an implicit run.

            var owner = currentPlan;
            var inputs = com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.capture(selectedBaseline.getSnapshot());
            var prepared =
                    baselineProjectionService
                            .projectBaseline(
                                    selectedBaseline);
            if (currentPlan != owner || currentPlan.getBaseline() != selectedBaseline || cachedBaselineIdentity != selectedBaseline) return null;
            baselineProjection = prepared;
            baselineDetailsInputs = inputs;
            baselineProjectionAssumptions = BreakEvenPlanSummary.from(
                    selectedBaseline.getSnapshot().getHousehold());
        }

        return baselineProjection;
    }

    public List<NonInvestableAssetProjection>
    getBaselineNonInvestableAssetProjections() {

        Projection projection = getBaselineProjection();

        if (baselineNonInvestableAssetProjections != null) {
            return baselineNonInvestableAssetProjections;
        }

        if (projection == null) {
            return List.of();
        }

        List<ProjectionYear> years =
                projection.getYears();

        if (years.isEmpty()) {
            return List.of();
        }

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        baselineNonInvestableAssetProjections =
                nonInvestableAssetProjectionService
                        .project(
                                currentPlan
                                        .getBaseline()
                                        .getSnapshot()
                                        .getNonInvestableAssets(),
                                firstYear,
                                lastYear);

        // Prepare presentation values while the actual source account registry is still available.
        // Failure of optional chart presentation must not change projection/comparison behavior.
        try {
            baselineDetailsChart = com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel.from(projection.getYears(),
                    baselineNonInvestableAssetProjections, baselineProjectionAssumptions,
                    currentPlan.getBaseline().getSnapshot().getAccountPortfolio().getAccounts());
        } catch (RuntimeException unavailableChart) { baselineDetailsChart = null; }

        return baselineNonInvestableAssetProjections;
    }

    private BreakEvenAnalysisResult insightSourceResult;
    private Projection insightCurrentProjection;
    private Projection insightBaselineProjection;
    private ProjectionBaseline insightBaselineIdentity;
    private java.util.function.Supplier<BreakEvenAnalyzer> breakEvenAnalyzerFactory = BreakEvenAnalyzer::new;
    private com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight cachedBreakEvenInsight;

    /** Reads only populated result caches. Opening an analysis never projects a plan. */
    public BreakEvenAnalysisResult getCachedBreakEvenAnalysis() {
        if (breakEvenExplicitOnly) return canRequestBreakEven() && currentPlan.getBaseline() == insightBaselineIdentity
                && currentProjection == insightCurrentProjection && baselineProjection == insightBaselineProjection ? insightSourceResult : null;
        return computeBreakEvenAnalysis();
    }

    private BreakEvenAnalysisResult computeBreakEvenAnalysis() {
        if (currentPlan == null || currentPlan.getBaseline() == null
                || cachedBaselineIdentity != currentPlan.getBaseline()
                || baselineProjection == null || currentProjection == null
                || baselineProjectionAssumptions == null || currentProjectionAssumptions == null
                || baselineNonInvestableAssetProjections == null || currentNonInvestableAssetProjections == null) {
            return null;
        }
        var result = breakEvenAnalyzerFactory.get().analyze(
                new BreakEvenProjectionSnapshot(baselineProjection.getYears(),
                        baselineNonInvestableAssetProjections, baselineProjectionAssumptions),
                new BreakEvenProjectionSnapshot(currentProjection.getYears(),
                        currentNonInvestableAssetProjections, currentProjectionAssumptions));
        insightSourceResult = result;
        insightCurrentProjection = currentProjection;
        insightBaselineProjection = baselineProjection;
        insightBaselineIdentity = currentPlan.getBaseline();
        cachedBreakEvenInsight = new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService()
                .prepare(result, baselineProjection.getYears(), currentProjection.getYears());
        return result;
    }

    /** Observes the same populated projection caches; never invokes lazy projection getters. */
    public com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight prepareBreakEvenInsight(BreakEvenAnalysisResult result) {
        return result == insightSourceResult ? cachedBreakEvenInsight
                : new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(result);
    }

    public ProjectionComparison compareProjections() {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        if (currentPlan.getBaseline() == null) {
            throw new IllegalStateException(
                    "No baseline projection exists.");
        }

        var comparisonOwner = currentPlan;
        var comparisonBaseline = currentPlan.getBaseline();
        Projection baseline =
                getBaselineProjection();

        Projection current =
                getCurrentProjection();

        var comparison = projectionComparisonService.compare(
                baseline,
                getBaselineNonInvestableAssetProjections(),
                current,
                getCurrentNonInvestableAssetProjections());
        if (comparisonOwner == currentPlan && comparisonBaseline == currentPlan.getBaseline() && current == currentProjection) {
            detailsComparison = comparison;
            detailsComparisonCurrent = current;
            detailsComparisonBaseline = comparisonBaseline;
            detailsComparisonOwner = comparisonOwner;
        }
        return comparison;
    }

    /** Cache-only eligibility. Never calculates or compares editable UI drafts. */
    public synchronized boolean canViewBaselineDetails(Projection displayed) {
        return currentPlan != null && currentPlan.getBaseline() != null && displayed != null
                && currentPlan == detailsComparisonOwner
                && displayed == currentProjection && displayed == detailsComparisonCurrent
                && currentPlan.getBaseline() == detailsComparisonBaseline
                && com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession.differs(detailsComparison);
    }

    /** A detached, read-only handoff; opening a dialog does not prepare missing financial caches. */
    public synchronized com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession prepareBaselineDetails(Projection displayed) {
        if (!canViewBaselineDetails(displayed)) return null;
        var baseline = currentPlan.getBaseline();
        var context = currentDetailsContext;
        String status = context == null ? "Current projection inputs: Not recorded. Live plan values are not substituted."
                : (getAcceptedProjectionSnapshot() != null ? currentProjectionInputsMatch() : context.sourceRevision() == sourcePlanRevision) ? "Current projection — frozen effective inputs"
                : "Potentially outdated projection — plan revision changed. Only frozen result inputs are compared.";
        return new com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession(baseline.getDescription(), baseline.getSavedAt(),
                context == null ? null : context.runId(), context == null ? null : context.planIdentity(), context == null ? null : context.completedAt(),
                displayed.isEmpty() ? "Not recorded" : displayed.getStartYear() + "–" + displayed.getEndYear(), status,
                com.daviddunn.retirementplanner.app.baseline.BaselineParameterComparison.compare(baselineDetailsInputs, context == null ? null : context.inputs()),
                baseline.getProjectionSnapshotId() != null && cachedBaselineIdentity == baseline && baselineProjection != null ? baselineDetailsChart : null);
    }

    public void saveCurrentAsBaseline(
            String description) {

        if (currentPlan == null) {
            throw new IllegalStateException(
                    "No current plan.");
        }

        var accepted = getAcceptedProjectionSnapshot();
        if (accepted == null || currentProjection == null || !accepted.matches(currentPlan))
            throw new IllegalStateException("Run and accept a projection matching the current inputs before capturing a baseline. No calculation was started.");
        ProjectionBaseline baseline = new ProjectionBaseline(accepted.restoreInputs(), java.time.LocalDateTime.now(), description, accepted.id());

        currentPlan.setBaseline(
                baseline);
        var state = currentPlan.getProjectionState();
        currentPlan.setProjectionState(state.withRoles(state.owner(), state.current(), state.current()));

        baselineProjection = null;
        baselineNonInvestableAssetProjections = null;

        modified = true;
        sourcePlanChanged();
    }

    /**
     * New UI should use this.
     */
    public ProjectionSummary getCurrentProjectionSummary() {

        if (currentProjectionSummary == null) {

            Projection projection =
                    getCurrentProjection();

            if (projection == null) {
                return null;
            }

            // Acceptance already froze the summary from the same detached execution inputs.
            if (currentProjectionSummary != null) return currentProjectionSummary;
            currentProjectionSummary =
                    projectionSummaryService.summarize(
                            currentPlan,
                            projection);
        }

        return currentProjectionSummary;
    }


    public BigDecimal getNonInvestableAssetValue(
            int calendarYear) {

        List<NonInvestableAssetProjection> projections =
                getCurrentNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(NonInvestableAssetProjection::getTotalValue)
                .orElse(BigDecimal.ZERO);
    }


    public void invalidateProjection() {
        projectionChanged();
    }

    public RetirementPlan newPlan() {

        return newPlan(RetirementPlanFactory.createSinglePersonPlan());
    }

    /** Activates an isolated, completed new-plan draft using the existing New lifecycle. */
    public RetirementPlan newPlan(RetirementPlan plan) {

        java.util.Objects.requireNonNull(plan, "New plan is required.").validateHouseholdReferences();
        currentPlan = plan;
        sourcePlanChanged();

        currentFile = null;
        analysisParameters.bind(currentPlan, currentFile);
        recoveredOriginalPath = null;
        recoveredBackupPath = null;

        modified = false;

        projectionChanged();

        return currentPlan;
    }

    public RetirementPlan open(
            Path file)
            throws IOException {

        if (repository instanceof JsonRetirementPlanRepository json) {
            var original = json.originalForBackup(file);
            if (original.isPresent()) {
                openRecoveredPlan(original.orElseThrow());
                return currentPlan;
            }
        }

        currentPlan =
                repository.load(file);
        sourcePlanChanged();
//
//        System.out.println(
//                "Primary Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getPrimaryPerson()
//                        .getBirthDate());
//
//        System.out.println(
//                "Spouse Birth Date = "
//                        + currentPlan.getHousehold()
//                        .getSpouse()
//                        .getBirthDate());

        currentFile =
                file;
        analysisParameters.bind(currentPlan, currentFile);
        recoveredOriginalPath = null;
        recoveredBackupPath = null;
        failedOpenPath = null;

        modified = false;

        applicationSettings.setLastOpenedPlan(
                file.toString());

        saveSettingsQuietly();

        projectionChanged();
        restoreProjectionSnapshots();

        return currentPlan;
    }

    public boolean openLastPlan() {

//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        if (!applicationSettings.isAutomaticallyOpenLastPlan()) {
            return false;
        }

        if (!applicationSettings.hasLastOpenedPlan()) {
            return false;
        }
//
//        System.out.println(
//                "Opening plan: "
//                        + applicationSettings.getLastOpenedPlan());

        try {

            open(Path.of(
                    applicationSettings.getLastOpenedPlan()));

//            System.out.println(
//                    "Primary Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getPrimaryPerson()
//                            .getBirthDate());
//
//            System.out.println(
//                    "Spouse Birth Date = "
//                            + currentPlan.getHousehold()
//                            .getSpouse()
//                            .getBirthDate());

            return true;

        } catch (IOException | RuntimeException ex) {
            try {
                failedOpenPath = Path.of(applicationSettings.getLastOpenedPlan());
            } catch (RuntimeException invalidPath) {
                failedOpenPath = null;
            }
            persistenceWarnings.add("The last plan could not be opened. Its original file was preserved. Use Open to select a plan or review an available backup.");
            return false;
        }
    }

    public void save()
            throws IOException {

        if (currentFile == null) {

            throw new IllegalStateException(
                    "No file selected.");
        }

        repository.save(
                currentPlan,
                currentFile);
        analysisParameters.saved(currentPlan, currentFile);

        modified = false;
        projectionRolesSaved();

        applicationSettings.setLastOpenedPlan(
                currentFile.toString());

        saveSettingsQuietly();
        collectSaveWarnings();
        analysisBindingChanged();
        if (restoredProjectionMode && currentProjection == null) restoreProjectionSnapshots();
    }

    public void saveAs(
            Path file)
            throws IOException {

        if (recoveredBackupPath != null
                && recoveredBackupPath.equals(file.toAbsolutePath().normalize())) {
            throw new IOException("Choose a different filename; the recovery backup must be preserved.");
        }

        String originalId = currentPlan.getPlanId();
        Path identitySource = currentFile != null ? currentFile : recoveredOriginalPath;
        boolean fork = identitySource != null && !identitySource.toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize());
        java.util.UUID proposed = fork || originalId == null ? java.util.UUID.randomUUID() : RetirementPlan.validatedPlanId(originalId);
        if (repository instanceof JsonRetirementPlanRepository json) {
            json.saveWithIdentity(currentPlan, file, proposed);
        } else {
            // Keep alternate repositories/test seams transactional with respect to active identity too.
            var copy = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(currentPlan);
            copy.assignPlanId(proposed);
            repository.save(copy, file);
            currentPlan.assignPlanId(proposed);
        }

        currentFile =
                file;

        analysisParameters.saved(currentPlan, currentFile);

        modified = false;
        projectionRolesSaved();

        applicationSettings.setLastOpenedPlan(
                file.toString());

        recoveredOriginalPath = null;
        recoveredBackupPath = null;
        saveSettingsQuietly();
        collectSaveWarnings();
        if (fork && originalId != null) {
            try {
                var isolated = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(currentPlan);
                snapshotCopy = analysisResults.cloneSnapshots(RetirementPlan.validatedPlanId(originalId), proposed, file, isolated);
            } catch (RuntimeException failure) {
                var warning = "The new plan was saved, but historical analysis copying could not start.";
                persistenceWarnings.add(warning);
                snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of(warning));
            }
        } else snapshotCopy = java.util.concurrent.CompletableFuture.completedFuture(List.of());
        analysisBindingChanged();
        if (restoredProjectionMode && currentProjection == null) restoreProjectionSnapshots();
    }

    private void saveSettingsQuietly() {
        try {
            applicationSettingsRepository.save(applicationSettings);
            persistenceWarnings.addAll(applicationSettingsRepository.takeWarnings());
        } catch (RuntimeException failure) {
            persistenceWarnings.add("The plan operation succeeded, but application settings could not be saved. The application remains usable.");
        }
    }

    private void collectSaveWarnings() {
        if (repository instanceof JsonRetirementPlanRepository json) {
            persistenceWarnings.addAll(json.takeWarnings());
        }
    }

    public List<String> takePersistenceWarnings() {
        persistenceWarnings.addAll(analysisResults.takeOperationWarnings());
        var result = List.copyOf(persistenceWarnings);
        persistenceWarnings.clear();
        return result;
    }

    public Path getFailedOpenPath() { return failedOpenPath; }
    public Path getRecoveredOriginalPath() { return recoveredOriginalPath; }
    public Path getRecoveredBackupPath() { return recoveredBackupPath; }

    public boolean hasValidBackup(Path original) {
        if (!(repository instanceof JsonRetirementPlanRepository json)) return false;
        try {
            json.loadBackup(original);
            return true;
        } catch (IOException | RuntimeException unavailable) {
            return false;
        }
    }

    /** Explicit UI-approved recovery. Save must use Save As, never the backup source. */
    public void openRecoveredPlan(Path original) throws IOException {
        if (!(repository instanceof JsonRetirementPlanRepository json)) {
            throw new IOException("Backup recovery is unavailable.");
        }
        var recovered = json.loadBackup(original);
        newPlan(recovered);
        recoveredOriginalPath = original.toAbsolutePath().normalize();
        recoveredBackupPath = json.backupPath(original);
        modified = true;
        restoreProjectionSnapshots();
    }

    public boolean hasCurrentFile() {
        return currentFile != null;
    }

    public Path getCurrentFile() {
        return currentFile;
    }

    private synchronized void projectionChanged() {
        cancelProjectionSwap();
        breakEvenExplicitOnly = false;
        projectionSwapNeedsSave = false;
        // Preserve immutable observations for an already-open historical report, but detach the live pair.
        insightCurrentProjection = null;
        insightBaselineProjection = null;
        insightBaselineIdentity = null;
        projectionEpoch.incrementAndGet();
        restoredProjectionMode = false;
        currentDetailsChart = null;
        projectionSnapshotNotice = "";
        currentProjection = null;
        currentDetailsContext = null;
        detailsComparison = null;
        detailsComparisonCurrent = null;
        detailsComparisonBaseline = null;
        detailsComparisonOwner = null;
        currentFundingFailure = null;
        currentProjectionSummary = null;
        currentProjectionAssumptions = null;
        currentNonInvestableAssetProjections = null;
    }

    public String getCurrentPlanName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile
                .getFileName()
                .toString();
    }

    public String getCurrentPlanDisplayName() {

        if (currentFile == null) {
            return "Untitled";
        }

        return currentFile.getFileName().toString();
    }


    public BigDecimal getBaselineNonInvestableAssetValue(
            int calendarYear) {

        if (currentPlan == null
                || currentPlan.getBaseline() == null) {

            return null;
        }

        List<NonInvestableAssetProjection> projections =
                getBaselineNonInvestableAssetProjections();

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(
                        NonInvestableAssetProjection::
                                getTotalValue)
                .orElse(null);
    }


    public void markModified() {

        modified = true;
        sourcePlanChanged();
    }

    public boolean isModified() {

        return modified;
    }



}
