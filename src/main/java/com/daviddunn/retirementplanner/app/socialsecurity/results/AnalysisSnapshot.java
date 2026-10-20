package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.time.Instant;
import java.util.*;

public record AnalysisSnapshot(int schemaVersion, int fingerprintVersion, UUID planId,
        AnalysisKind kind, AnalysisKind.Shape shape, UUID runId, Instant completedAt,
        String applicationBuild, Map<String, String> algorithms, Map<String, String> references,
        String inputFingerprint, String contextFingerprint, String payloadDigest,
        WeightedSnapshots.Payload payload, SnapshotContext context, UUID originPlanId) {
    public static final int SCHEMA = 1;
    public AnalysisSnapshot {
        Objects.requireNonNull(planId); Objects.requireNonNull(kind); Objects.requireNonNull(shape);
        Objects.requireNonNull(runId); Objects.requireNonNull(completedAt); Objects.requireNonNull(applicationBuild);
        algorithms = Map.copyOf(algorithms); references = Map.copyOf(references);
        if (algorithms.isEmpty() || references.isEmpty()) throw new IllegalArgumentException("Compatibility provenance is required.");
        Objects.requireNonNull(payload); Objects.requireNonNull(context);
        if (kind == AnalysisKind.COUPLE_WEIGHTED && context.baseline() == null)
            throw new IllegalArgumentException("Frozen weighted baseline provenance is required, including unavailable baseline inputs.");
        if (schemaVersion != SCHEMA || fingerprintVersion != 1 || shape != kind.shape()) {
            throw new IllegalArgumentException("Unsupported snapshot version or household shape.");
        }
        for (String digest : List.of(inputFingerprint, contextFingerprint, payloadDigest)) {
            if (!digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid snapshot digest.");
        }
        payload.validate(kind);
        if (!payload.successful()) throw new IllegalArgumentException("A last-success snapshot requires a successful candidate.");
    }
    public AnalysisSnapshot clonedTo(UUID target) {
        return new AnalysisSnapshot(schemaVersion, fingerprintVersion, target, kind, shape, runId,
                completedAt, applicationBuild, algorithms, references, inputFingerprint, contextFingerprint,
                payloadDigest, payload, context, originPlanId == null ? planId : originPlanId);
    }
}
