package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;
import java.util.Optional;

/**
 * Empirical populations under January 1 deaths. A household is living in year y
 * exactly when at least one actual member is alive. Individual populations have no couple breakdown.
 * Investable-assets percentiles
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
        int deceasedCount,
        Optional<CouplePopulation> couplePopulation,
        Optional<MonteCarloPercentiles> investableAssets) {

    public record CouplePopulation(int bothAliveCount, int primaryOnlyAliveCount, int spouseOnlyAliveCount) {
        public CouplePopulation {
            if (bothAliveCount < 0 || primaryOnlyAliveCount < 0 || spouseOnlyAliveCount < 0)
                throw new IllegalArgumentException("Population counts cannot be negative.");
        }
        long living() { return (long) bothAliveCount + primaryOnlyAliveCount + spouseOnlyAliveCount; }
    }

    public MonteCarloMortalityAnnualResult(int year, int requested, int living, int completed, int failed,
            int deceased, int bothAlive, int primaryOnly, int spouseOnly, Optional<MonteCarloPercentiles> assets) {
        this(year, requested, living, completed, failed, deceased,
                Optional.of(new CouplePopulation(bothAlive, primaryOnly, spouseOnly)), assets);
    }

    public int bothAliveCount() { return couplePopulation.orElseThrow().bothAliveCount(); }
    public int primaryOnlyAliveCount() { return couplePopulation.orElseThrow().primaryOnlyAliveCount(); }
    public int spouseOnlyAliveCount() { return couplePopulation.orElseThrow().spouseOnlyAliveCount(); }
    public int bothDeceasedCount() {
        if (couplePopulation.isEmpty()) throw new IllegalStateException("Individual population has no both-deceased state.");
        return deceasedCount;
    }

    public MonteCarloMortalityAnnualResult {
        Objects.requireNonNull(couplePopulation);
        Objects.requireNonNull(investableAssets);
        if (requestedWorldCount <= 0 || livingHouseholdCount < 0 || completedLivingYearSampleCount < 0
                || livingFundingFailedByYearCount < 0 || deceasedCount < 0
                || requestedWorldCount != (long) livingHouseholdCount + deceasedCount
                || livingHouseholdCount != (long) completedLivingYearSampleCount + livingFundingFailedByYearCount
                || couplePopulation.isPresent() && livingHouseholdCount != couplePopulation.orElseThrow().living()
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
