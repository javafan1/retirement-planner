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
        com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission association) {
    public AcceptedAnalysisCompletion(UUID planId, Path planFile, AnalysisKind kind, UUID runId,
            Instant completedAt, String inputFingerprint, String contextFingerprint, Map<String, String> algorithms,
            Map<String, String> references, AnalysisPayload payload, SnapshotContext context) {
        this(planId, planFile, kind, runId, completedAt, inputFingerprint, contextFingerprint, algorithms, references, payload, context, null);
    }
    public AcceptedAnalysisCompletion associatedWith(com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission admission) {
        return new AcceptedAnalysisCompletion(planId, planFile, kind, runId, completedAt, inputFingerprint,
                contextFingerprint, algorithms, references, payload, context, admission);
    }
    public AcceptedAnalysisCompletion forRun(UUID identity) {
        return new AcceptedAnalysisCompletion(planId, planFile, kind, identity, completedAt, inputFingerprint,
                contextFingerprint, algorithms, references, payload, context, association);
    }
    public AcceptedAnalysisCompletion {
        Objects.requireNonNull(planId); Objects.requireNonNull(kind); Objects.requireNonNull(runId);
        if (planFile != null) planFile = planFile.toAbsolutePath().normalize();
        Objects.requireNonNull(completedAt); algorithms = Map.copyOf(algorithms); references = Map.copyOf(references);
        Objects.requireNonNull(payload); Objects.requireNonNull(context);
        payload.validate(kind);
        if (!payload.successful()) throw new IllegalArgumentException("No successful candidates; previous result must be retained.");
    }
    public AnalysisSnapshot snapshot(AnalysisSnapshotCodec codec) {
        if (planFile == null) throw new IllegalStateException("An unsaved plan's result is memory only.");
        return presentationSnapshot(codec);
    }
    /** Immutable in-memory metadata; this operation makes no claim of durable storage. */
    public AnalysisSnapshot presentationSnapshot(AnalysisSnapshotCodec codec) {
        return new AnalysisSnapshot(1, 1, planId, kind, kind.shape(), runId, completedAt,
                "1.0-SNAPSHOT", algorithms, references.isEmpty() ? AnalysisVersions.references(kind) : references, inputFingerprint, contextFingerprint,
                codec.digest(payload, context), payload, context, null);
    }
}
