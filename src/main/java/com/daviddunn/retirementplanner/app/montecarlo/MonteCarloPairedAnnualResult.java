package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.Objects;

/** Shared mortality makes living/applicable counts identical for A and B.
 * Failures here count only living households; normal post-second-death absence is separate.
 * Comparable annual prefixes include worlds that subsequently fail. */
public record MonteCarloPairedAnnualResult(
        int year,
        int requestedWorldCount,
        int livingHouseholdCount,
        int bothDeceasedCount,
        int aCompletedBFailedCount,
        int aFailedBCompletedCount,
        int bothFailedCount,
        MonteCarloPairedMetricSummary investableAssetsDifference) {

    public MonteCarloPairedAnnualResult {
        Objects.requireNonNull(investableAssetsDifference);
        if (requestedWorldCount <= 0 || livingHouseholdCount < 0 || bothDeceasedCount < 0
                || aCompletedBFailedCount < 0 || aFailedBCompletedCount < 0 || bothFailedCount < 0
                || requestedWorldCount != (long) livingHouseholdCount + bothDeceasedCount
                || livingHouseholdCount != (long) investableAssetsDifference.sampleCount()
                + aCompletedBFailedCount + aFailedBCompletedCount + bothFailedCount) {
            throw new IllegalArgumentException("Annual paired populations must reconcile.");
        }
    }

    public int comparableCount() { return investableAssetsDifference.sampleCount(); }
    public int aAvailableCount() { return comparableCount() + aCompletedBFailedCount; }
    public int bAvailableCount() { return comparableCount() + aFailedBCompletedCount; }
}
