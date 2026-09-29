package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Nominal A-minus-B observations. The containing terminal/annual result defines the comparable population.
 * Relations compare exact differences with zero, including for taxes (positive means A paid more).
 * No samples means absent percentiles, mean, and conditional probabilities. */
public record MonteCarloPairedMetricSummary(
        int sampleCount,
        Optional<MonteCarloPercentiles> differencePercentiles,
        Optional<BigDecimal> meanDifference,
        int greaterCount,
        int equalCount,
        int lessCount) {

    public MonteCarloPairedMetricSummary {
        Objects.requireNonNull(differencePercentiles);
        Objects.requireNonNull(meanDifference);
        if (sampleCount < 0 || greaterCount < 0 || equalCount < 0 || lessCount < 0
                || sampleCount != (long) greaterCount + equalCount + lessCount
                || differencePercentiles.isPresent() != (sampleCount > 0)
                || meanDifference.isPresent() != (sampleCount > 0)
                || differencePercentiles.map(MonteCarloPercentiles::sampleCount).orElse(0) != sampleCount) {
            throw new IllegalArgumentException("Comparable metric populations must reconcile.");
        }
    }

    static MonteCarloPairedMetricSummary fromDifferences(List<BigDecimal> differences) {
        int greater = 0;
        int equal = 0;
        int less = 0;
        var sum = BigDecimal.ZERO;
        for (var difference : differences) {
            sum = sum.add(difference);
            if (difference.signum() > 0) {
                greater++;
            } else if (difference.signum() < 0) {
                less++;
            } else {
                equal++;
            }
        }
        return new MonteCarloPairedMetricSummary(differences.size(), MonteCarloPercentiles.of(differences),
                differences.isEmpty() ? Optional.empty()
                        : Optional.of(sum.divide(BigDecimal.valueOf(differences.size()), MathContext.DECIMAL128)),
                greater, equal, less);
    }

    private Optional<BigDecimal> probability(int index) {
        return sampleCount == 0 ? Optional.empty()
                : Optional.of(MonteCarloPairedProbabilities.of(greaterCount, equalCount, lessCount).get(index));
    }

    public Optional<BigDecimal> greaterProbabilityAmongComparable() { return probability(0); }
    public Optional<BigDecimal> equalProbabilityAmongComparable() { return probability(1); }
    public Optional<BigDecimal> lessProbabilityAmongComparable() { return probability(2); }
}
