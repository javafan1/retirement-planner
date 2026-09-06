package com.daviddunn.retirementplanner.domain.analysis;

import java.util.Objects;

/** Immutable real-work progress snapshot. */
public record AnalysisProgress(
        AnalysisPhase phase,
        int completedWork,
        int totalWork) {

    public AnalysisProgress {
        Objects.requireNonNull(phase, "Analysis phase is required.");
        if (totalWork < 0) {
            throw new IllegalArgumentException("Total work cannot be negative.");
        }
        if (completedWork < 0 || completedWork > totalWork) {
            throw new IllegalArgumentException(
                    "Completed work must be between zero and total work.");
        }
    }

    public double fractionComplete() {
        return totalWork == 0 ? 1.0d : (double) completedWork / totalWork;
    }

    public int wholePercent() {
        return (int) Math.floor(fractionComplete() * 100.0d);
    }
}
