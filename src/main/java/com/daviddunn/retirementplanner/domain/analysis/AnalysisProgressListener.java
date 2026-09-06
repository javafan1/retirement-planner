package com.daviddunn.retirementplanner.domain.analysis;

/** Neutral callback; deliberately has no JavaFX dependency. */
@FunctionalInterface
public interface AnalysisProgressListener {

    void onProgress(AnalysisProgress progress);

    static AnalysisProgressListener none() {
        return progress -> { };
    }
}
