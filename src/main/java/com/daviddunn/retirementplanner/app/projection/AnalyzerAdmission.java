package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import java.util.UUID;

/** Immutable request-preparation token. This type is never serialized as historical evidence. */
public record AnalyzerAdmission(UUID planId, UUID projectionId, String projectionInputDigest,
        AnalysisKind kind, long projectionGeneration, long sourceRevision, UUID ticket, String requestFingerprint) { }
