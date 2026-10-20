package com.daviddunn.retirementplanner.app.socialsecurity.results;

/** Explicit storage families. The codec dispatches by validated analysis kind, never runtime type names. */
public interface AnalysisPayload {
    void validate(AnalysisKind kind);
    boolean successful();
    default WeightedSnapshots.Couple couple() { return null; }
    default WeightedSnapshots.Individual individual() { return null; }
}
