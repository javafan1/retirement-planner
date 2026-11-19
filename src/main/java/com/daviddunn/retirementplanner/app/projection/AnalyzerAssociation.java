package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import java.time.Instant;
import java.util.*;

/** Recorded provenance, never inferred from a visible result or a matching historical hash. */
public record AnalyzerAssociation(int version, UUID owner, UUID projectionId, String projectionInputDigest,
        AnalysisKind kind, UUID runId, String inputFingerprint, String contextFingerprint,
        String payloadDigest, String componentDigest, Instant acceptedAt,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        UUID alternativeProjectionId) {
    public AnalyzerAssociation(int version, UUID owner, UUID projectionId, String projectionInputDigest,
            AnalysisKind kind, UUID runId, String inputFingerprint, String contextFingerprint,
            String payloadDigest, String componentDigest, Instant acceptedAt) {
        this(version, owner, projectionId, projectionInputDigest, kind, runId, inputFingerprint, contextFingerprint,
                payloadDigest, componentDigest, acceptedAt, null);
    }
    public AnalyzerAssociation {
        if ((version != 1 && version != 2) || (version == 2) != (alternativeProjectionId != null)
                || (alternativeProjectionId != null && (alternativeProjectionId.equals(projectionId)
                        || !kind.isMonteCarlo() || !kind.name().contains("_PAIRED_")))) {
            throw new IllegalArgumentException("Unsupported analyzer association version or projection dependencies.");
        }
        Objects.requireNonNull(owner); Objects.requireNonNull(projectionId); Objects.requireNonNull(kind);
        Objects.requireNonNull(runId); Objects.requireNonNull(acceptedAt);
        for (var value : List.of(projectionInputDigest, inputFingerprint, contextFingerprint, payloadDigest, componentDigest))
            if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid association digest.");
    }
    public AnalyzerAssociation ownedBy(UUID target, String digest) {
        return new AnalyzerAssociation(version, target, projectionId, projectionInputDigest, kind, runId,
                inputFingerprint, contextFingerprint, payloadDigest, digest, acceptedAt, alternativeProjectionId);
    }
    public boolean references(UUID id) { return projectionId.equals(id) || id.equals(alternativeProjectionId); }

    public void validate(AnalysisSnapshot snapshot) {
        if (!owner.equals(snapshot.planId()) || kind != snapshot.kind() || !runId.equals(snapshot.runId())
                || !inputFingerprint.equals(snapshot.inputFingerprint()) || !contextFingerprint.equals(snapshot.contextFingerprint())
                || !payloadDigest.equals(snapshot.payloadDigest()) || !acceptedAt.equals(snapshot.completedAt()))
            throw new IllegalArgumentException("Analyzer association/envelope mismatch.");
        if (snapshot.arkansasExecution() != null) {
            snapshot.arkansasExecution().validateAssociation(projectionId, projectionInputDigest);
            var dependencies = snapshot.arkansasExecution().dependencies();
            var alternative = dependencies.size() == 2 ? dependencies.get(1).projectionId() : null;
            if (!Objects.equals(alternative, alternativeProjectionId)) {
                throw new IllegalArgumentException("Arkansas analysis alternative projection association mismatch.");
            }
        } else if (alternativeProjectionId != null) {
            throw new IllegalArgumentException("Alternate projection pin requires Arkansas execution provenance.");
        }
    }
}
