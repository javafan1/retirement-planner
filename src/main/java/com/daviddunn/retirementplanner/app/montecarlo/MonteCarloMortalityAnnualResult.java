package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import java.util.Optional;

/**
 * Empirical populations under January 1 deaths. A household is living in year y
 * exactly when y is before its second death year. Investable-assets percentiles
 * are conditional on being living AND completing that year's financial row.
 * Deceased households and living households that funding-failed by this year
 * contribute no asset observation; neither is represented by a fabricated zero.
 */
public record MonteCarloMortalityAnnualResult(
        int year,
        int requestedWorldCount,
        int livingHouseholdCount,
        int completedLivingYearSampleCount,
        int livingFundingFailedByYearCount,
        int bothDeceasedCount,
        int bothAliveCount,
        int primaryOnlyAliveCount,
        int spouseOnlyAliveCount,
        Optional<MonteCarloPercentiles> investableAssets) {

    public MonteCarloMortalityAnnualResult {
        Objects.requireNonNull(investableAssets);
        if (requestedWorldCount <= 0 || livingHouseholdCount < 0 || completedLivingYearSampleCount < 0
                || livingFundingFailedByYearCount < 0 || bothDeceasedCount < 0
                || bothAliveCount < 0 || primaryOnlyAliveCount < 0 || spouseOnlyAliveCount < 0
                || requestedWorldCount != (long) livingHouseholdCount + bothDeceasedCount
                || livingHouseholdCount != (long) completedLivingYearSampleCount + livingFundingFailedByYearCount
                || livingHouseholdCount != (long) bothAliveCount + primaryOnlyAliveCount + spouseOnlyAliveCount
                || investableAssets.isPresent() != (completedLivingYearSampleCount > 0)
                || investableAssets.map(MonteCarloPercentiles::sampleCount).orElse(0) != completedLivingYearSampleCount) {
            throw new IllegalArgumentException("Annual mortality populations and asset samples must reconcile.");
        }
    }

    /** Fraction funded through this completed year among living households, not lifetime funding probability. */
    public Optional<BigDecimal> livingFundingRate() {
        return livingHouseholdCount == 0 ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(completedLivingYearSampleCount).divide(
                        BigDecimal.valueOf(livingHouseholdCount), MathContext.DECIMAL128));
    }
}
