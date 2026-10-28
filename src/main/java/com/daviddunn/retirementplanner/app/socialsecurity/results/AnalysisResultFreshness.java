package com.daviddunn.retirementplanner.app.socialsecurity.results;

import com.daviddunn.retirementplanner.app.projection.AnalyzerAssociation;
import java.util.*;

/** Presentation assessment only. Never authorizes execution, changes fingerprints or creates associations. */
public record AnalysisResultFreshness(AnalysisCompatibilityPolicy.Assessment applicability, Association association) {
    public enum Association { CURRENT_PROJECTION, HISTORICAL_PROJECTION, INPUTS_EDITED, UNRECORDED }

    public AnalysisResultFreshness {
        Objects.requireNonNull(association);
    }

    public boolean pending() { return applicability == null; }
    public boolean compatible() {
        return applicability != null && applicability.state() == AnalysisCompatibilityPolicy.State.CURRENT;
    }
    public boolean mismatch() {
        return applicability != null && applicability.state() == AnalysisCompatibilityPolicy.State.OUTDATED;
    }
    public String warning() {
        if (pending()) return "Assessing completed result inputs…";
        if (compatible()) return "";
        return status();
    }
    public String status() {
        if (pending()) return "Assessing completed result inputs…";
        return switch (applicability.state()) {
            case CURRENT -> switch (association) {
                case CURRENT_PROJECTION -> "Current Results — verified projection association and matching analysis inputs.";
                case HISTORICAL_PROJECTION -> "Compatible Results — historical association belongs to another projection.";
                case INPUTS_EDITED -> "Compatible analyzer inputs — configured financial inputs differ from the accepted projection.";
                case UNRECORDED -> "Current Results — compatible but unassociated; financial/request compatibility only, not verified projection-associated Current.";
            };
            case OUTDATED -> "Historical Analysis — Outdated: " + applicability.explanation();
            case INPUTS_NEED_ATTENTION -> "Inputs Need Attention — " + applicability.explanation();
            case INCOMPATIBLE_OR_UNAVAILABLE -> "Incompatible or Unavailable — " + applicability.explanation();
            case NO_SAVED_RESULTS -> "No Saved Results";
        };
    }

    /** The caller supplies a verified envelope and recorded manifest; equal hashes never infer a UUID association. */
    public static Association association(AnalysisSnapshot saved, UUID owner, UUID currentProjection,
            String projectionInputDigest, boolean configuredInputsMatch, List<AnalyzerAssociation> references) {
        if (saved == null || !saved.planId().equals(owner)) return Association.UNRECORDED;
        for (var ref : references) {
            if (!ref.owner().equals(owner) || !ref.runId().equals(saved.runId()) || ref.kind() != saved.kind()) continue;
            try { ref.validate(saved); }
            catch (IllegalArgumentException invalid) { continue; }
            if (!ref.projectionId().equals(currentProjection)) return Association.HISTORICAL_PROJECTION;
            if (!ref.projectionInputDigest().equals(projectionInputDigest)) return Association.UNRECORDED;
            return configuredInputsMatch ? Association.CURRENT_PROJECTION : Association.INPUTS_EDITED;
        }
        return Association.UNRECORDED;
    }
}
