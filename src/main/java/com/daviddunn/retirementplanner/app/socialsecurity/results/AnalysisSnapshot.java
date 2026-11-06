package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.time.Instant;
import java.util.*;

@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = AnalysisSnapshotReader.class)
public record AnalysisSnapshot(int schemaVersion, int fingerprintVersion, UUID planId,
        AnalysisKind kind, AnalysisKind.Shape shape, UUID runId, Instant completedAt,
        String applicationBuild, Map<String, String> algorithms, Map<String, String> references,
        String inputFingerprint, String contextFingerprint, String payloadDigest,
        AnalysisPayload payload, SnapshotContext context, UUID originPlanId,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        ConditionalBenefitAnalysisExecution benefitExecution) {
    public static final int SCHEMA = 1;
    public AnalysisSnapshot(int schemaVersion, int fingerprintVersion, UUID planId,
            AnalysisKind kind, AnalysisKind.Shape shape, UUID runId, Instant completedAt,
            String applicationBuild, Map<String, String> algorithms, Map<String, String> references,
            String inputFingerprint, String contextFingerprint, String payloadDigest,
            AnalysisPayload payload, SnapshotContext context, UUID originPlanId) {
        this(schemaVersion, fingerprintVersion, planId, kind, shape, runId, completedAt, applicationBuild,
                algorithms, references, inputFingerprint, contextFingerprint, payloadDigest, payload, context,
                originPlanId, null);
    }
    public AnalysisSnapshot {
        Objects.requireNonNull(planId); Objects.requireNonNull(kind); Objects.requireNonNull(shape);
        Objects.requireNonNull(runId); Objects.requireNonNull(completedAt); Objects.requireNonNull(applicationBuild);
        algorithms = Map.copyOf(algorithms); references = Map.copyOf(references);
        if (algorithms.isEmpty() || references.isEmpty()) throw new IllegalArgumentException("Compatibility provenance is required.");
        Objects.requireNonNull(payload); Objects.requireNonNull(context);
        if (kind == AnalysisKind.COUPLE_WEIGHTED && context.baseline() == null)
            throw new IllegalArgumentException("Frozen weighted baseline provenance is required, including unavailable baseline inputs.");
        boolean enabledExpenses = payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                && (mc.hasExpenseExecution() || mc.benefitExecution() != null);
        int expectedFingerprint = payload instanceof com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot mc
                ? mc.fingerprintVersion() : 1;
        if (schemaVersion != SCHEMA || fingerprintVersion != expectedFingerprint || shape != kind.shape()
                || enabledExpenses && (!kind.isMonteCarlo() || !AnalysisVersions.supportedMonteCarloAlgorithms(kind, payload, algorithms))) {
            throw new IllegalArgumentException("Unsupported snapshot version or household shape.");
        }
        for (String digest : List.of(inputFingerprint, contextFingerprint, payloadDigest)) {
            if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid snapshot digest.");
        }
        payload.validate(kind);
        ConditionalBenefitAnalyzerValidation.validate(kind, payload, context, benefitExecution, algorithms,
                inputFingerprint, contextFingerprint);
        if (!payload.successful()) throw new IllegalArgumentException("A last-success snapshot requires a successful candidate.");
    }
    public AnalysisSnapshot clonedTo(UUID target) {
        return new AnalysisSnapshot(schemaVersion, fingerprintVersion, target, kind, shape, runId,
                completedAt, applicationBuild, algorithms, references, inputFingerprint, contextFingerprint,
                payloadDigest, payload, context, originPlanId == null ? planId : originPlanId, benefitExecution);
    }
}
