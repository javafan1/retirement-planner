package com.daviddunn.retirementplanner.app.analysis.parameters;

import java.util.Objects;

/** Only callers that have passed the financial job's final eligibility gate may submit this event. */
public record AcceptedAnalysisParameters(AnalysisParameterCoordinator.Binding binding,
        long acceptanceSequence, AnalysisParameterConfiguration configuration) {
    public AcceptedAnalysisParameters {
        Objects.requireNonNull(binding); Objects.requireNonNull(configuration);
        if (acceptanceSequence < 1) throw new IllegalArgumentException("Positive acceptance sequence required.");
    }
}
