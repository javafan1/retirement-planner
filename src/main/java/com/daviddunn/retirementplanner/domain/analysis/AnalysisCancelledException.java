package com.daviddunn.retirementplanner.domain.analysis;

/** Signals cooperative cancellation without publishing a partial result. */
public final class AnalysisCancelledException extends RuntimeException {

    public AnalysisCancelledException() {
        super("Analysis was cancelled.");
    }
}
