package com.daviddunn.retirementplanner.app.socialsecurity;

/** Actual run-local work. Failed speculative carriers never finalize member scenarios. */
public record LongevityContinuationWork(
        long positiveScenarios,
        long carrierAttempts,
        long successfulCarriers,
        long failedCarriers,
        long independentEarlyHorizonRuns,
        long fallbackIndependentRuns,
        long projectionStarts,
        long completedProjections,
        long scenariosStarted,
        long outcomesProduced,
        long reusedOutcomes,
        long completedAnnualRows) {
    public static LongevityContinuationWork zero() {
        return new LongevityContinuationWork(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Each reused member avoids one independent run; every speculative carrier costs one. May be negative on failure. */
    public long evaluationsAvoided() {
        return reusedOutcomes - carrierAttempts;
    }

    public LongevityContinuationWork plus(LongevityContinuationWork other) {
        return new LongevityContinuationWork(positiveScenarios + other.positiveScenarios,
                carrierAttempts + other.carrierAttempts, successfulCarriers + other.successfulCarriers,
                failedCarriers + other.failedCarriers, independentEarlyHorizonRuns + other.independentEarlyHorizonRuns,
                fallbackIndependentRuns + other.fallbackIndependentRuns, projectionStarts + other.projectionStarts,
                completedProjections + other.completedProjections, scenariosStarted + other.scenariosStarted,
                outcomesProduced + other.outcomesProduced, reusedOutcomes + other.reusedOutcomes,
                completedAnnualRows + other.completedAnnualRows);
    }
}
