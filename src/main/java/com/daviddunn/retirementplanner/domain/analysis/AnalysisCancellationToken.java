package com.daviddunn.retirementplanner.domain.analysis;

/** Cooperative cancellation checked only at safe analysis boundaries. */
@FunctionalInterface
public interface AnalysisCancellationToken {

    boolean isCancellationRequested();

    static AnalysisCancellationToken none() {
        return () -> false;
    }

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new AnalysisCancelledException();
        }
    }
}
