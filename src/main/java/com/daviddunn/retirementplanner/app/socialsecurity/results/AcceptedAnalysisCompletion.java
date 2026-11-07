package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Only create at an existing eligible success callback. It owns no dialog or live plan reference. */
public record AcceptedAnalysisCompletion(UUID planId, Path planFile, AnalysisKind kind, UUID runId,
        Instant completedAt, String inputFingerprint, String contextFingerprint,
        Map<String, String> algorithms, Map<String, String> references,
        AnalysisPayload payload, SnapshotContext context,
        com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission association,
        ConditionalBenefitAnalysisExecution benefitExecution) {
    public AcceptedAnalysisCompletion(UUID planId, Path planFile, AnalysisKind kind, UUID runId,
            Instant completedAt, String inputFingerprint, String contextFingerprint, Map<String, String> algorithms,
            Map<String, String> references, AnalysisPayload payload, SnapshotContext context,
            com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission association) {
        this(planId, planFile, kind, runId, completedAt, inputFingerprint, contextFingerprint, algorithms, references,
                payload, context, association, null);
    }
    public AcceptedAnalysisCompletion(UUID planId, Path planFile, AnalysisKind kind, UUID runId,
            Instant completedAt, String inputFingerprint, String contextFingerprint, Map<String, String> algorithms,
            Map<String, String> references, AnalysisPayload payload, SnapshotContext context) {
        this(planId, planFile, kind, runId, completedAt, inputFingerprint, contextFingerprint, algorithms, references, payload, context, null);
    }
    public AcceptedAnalysisCompletion associatedWith(com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission admission) {
        return new AcceptedAnalysisCompletion(planId, planFile, kind, runId, completedAt, inputFingerprint,
                contextFingerprint, algorithms, references, payload, context, admission, benefitExecution);
    }
    public AcceptedAnalysisCompletion forRun(UUID identity) {
        return new AcceptedAnalysisCompletion(planId, planFile, kind, identity, completedAt, inputFingerprint,
                contextFingerprint, algorithms, references, payload, context, association, benefitExecution);
    }
    public AcceptedAnalysisCompletion withConditionalBenefits(com.daviddunn.retirementplanner.domain.model.RetirementPlan frozenPlan) {
        if (frozenPlan.getConditionalBenefits().isEmpty()) return this;
        if (kind.isMonteCarlo()) throw new IllegalArgumentException("Monte Carlo benefits require their payload capability.");
        var capability = ConditionalBenefitAnalysisExecution.capture(kind, frozenPlan);
        var versions = new TreeMap<>(AnalysisVersions.calculatedAlgorithms(kind, payload));
        versions.put(ConditionalBenefitAnalysisExecution.KEY, capability.algorithm());
        return new AcceptedAnalysisCompletion(planId, planFile, kind, runId, completedAt, inputFingerprint,
                contextFingerprint, versions, references, payload, context, association, capability);
    }
    public AcceptedAnalysisCompletion {
        Objects.requireNonNull(planId); Objects.requireNonNull(kind); Objects.requireNonNull(runId);
        if (planFile != null) planFile = planFile.toAbsolutePath().normalize();
        Objects.requireNonNull(completedAt); algorithms = Map.copyOf(algorithms); references = Map.copyOf(references);
        Objects.requireNonNull(payload); Objects.requireNonNull(context);
        payload.validate(kind);
        if (benefitExecution != null) ConditionalBenefitAnalyzerValidation.validate(kind, payload, context,
                benefitExecution, algorithms, inputFingerprint, contextFingerprint);
        if (!payload.successful()) throw new IllegalArgumentException("No successful candidates; previous result must be retained.");
    }
    public void validateCapabilities() {
        ConditionalBenefitAnalyzerValidation.validate(kind, payload, context, benefitExecution, algorithms,
                inputFingerprint, contextFingerprint);
    }
    public AnalysisSnapshot snapshot(AnalysisSnapshotCodec codec) {
        if (planFile == null) throw new IllegalStateException("An unsaved plan's result is memory only.");
        return presentationSnapshot(codec);
    }
    /** Immutable in-memory metadata; this operation makes no claim of durable storage. */
    public AnalysisSnapshot presentationSnapshot(AnalysisSnapshotCodec codec) {
        int fingerprintVersion = payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                ? mc.fingerprintVersion() : 1;
        return new AnalysisSnapshot(1, fingerprintVersion, planId, kind, kind.shape(), runId, completedAt,
                "1.0-SNAPSHOT", algorithms, references.isEmpty() ? AnalysisVersions.references(kind) : references, inputFingerprint, contextFingerprint,
                codec.digest(payload, context, benefitExecution), payload, context, null, benefitExecution);
    }
}
