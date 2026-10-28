package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.time.Instant;
import java.util.*;

/** Recorded provenance, never inferred from a visible result or a matching historical hash. */
public record AnalyzerAssociation(int version, UUID owner, UUID projectionId, String projectionInputDigest,
        AnalysisKind kind, UUID runId, String inputFingerprint, String contextFingerprint,
        String payloadDigest, String componentDigest, Instant acceptedAt) {
    public AnalyzerAssociation {
        if (version != 1) throw new IllegalArgumentException("Unsupported analyzer association version.");
        Objects.requireNonNull(owner); Objects.requireNonNull(projectionId); Objects.requireNonNull(kind);
        Objects.requireNonNull(runId); Objects.requireNonNull(acceptedAt);
        for (var value : List.of(projectionInputDigest, inputFingerprint, contextFingerprint, payloadDigest, componentDigest))
            if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid association digest.");
    }
    public AnalyzerAssociation ownedBy(UUID target, String digest) {
        return new AnalyzerAssociation(version, target, projectionId, projectionInputDigest, kind, runId,
                inputFingerprint, contextFingerprint, payloadDigest, digest, acceptedAt);
    }
    public void validate(AnalysisSnapshot snapshot) {
        if (!owner.equals(snapshot.planId()) || kind != snapshot.kind() || !runId.equals(snapshot.runId())
                || !inputFingerprint.equals(snapshot.inputFingerprint()) || !contextFingerprint.equals(snapshot.contextFingerprint())
                || !payloadDigest.equals(snapshot.payloadDigest()) || !acceptedAt.equals(snapshot.completedAt()))
            throw new IllegalArgumentException("Analyzer association/envelope mismatch.");
    }
}
