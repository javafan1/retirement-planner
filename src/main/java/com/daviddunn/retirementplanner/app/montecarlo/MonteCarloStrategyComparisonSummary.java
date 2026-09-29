package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Cached evidence, never a score. All four terminal metrics condition on BOTH_COMPLETED worlds.
 * Each longevity pair uses its own common terminal date; values are nominal, not discounted. */
public record MonteCarloStrategyComparisonSummary(
        MonteCarloPairedStateSummary pairedStates,
        Optional<FundingFailureStatistics> strategyAFailureStatistics,
        Optional<FundingFailureStatistics> strategyBFailureStatistics,
        MonteCarloPairedMetricSummary terminalInvestableAssetsDifference,
        MonteCarloPairedMetricSummary terminalNetWorthDifference,
        MonteCarloPairedMetricSummary terminalAfterTaxEstateDifference,
        MonteCarloPairedMetricSummary lifetimeTaxesDifference,
        Map<Integer, MonteCarloPairedAnnualResult> annualResults) {

    public MonteCarloStrategyComparisonSummary {
        Objects.requireNonNull(pairedStates);
        Objects.requireNonNull(strategyAFailureStatistics);
        Objects.requireNonNull(strategyBFailureStatistics);
        for (var metric : java.util.List.of(terminalInvestableAssetsDifference, terminalNetWorthDifference,
                terminalAfterTaxEstateDifference, lifetimeTaxesDifference)) {
            if (metric.sampleCount() != pairedStates.bothCompleted()) {
                throw new IllegalArgumentException("Terminal metrics require exactly the both-completed population.");
            }
        }
        if (strategyAFailureStatistics.map(FundingFailureStatistics::count).orElse(0L) != pairedStates.aFailedCount()
                || strategyBFailureStatistics.map(FundingFailureStatistics::count).orElse(0L) != pairedStates.bFailedCount()) {
            throw new IllegalArgumentException("Independent failure counts must reconcile.");
        }
        annualResults = Collections.unmodifiableMap(new TreeMap<>(annualResults));
        Integer previous = null;
        for (var entry : annualResults.entrySet()) {
            var annual = entry.getValue();
            if (entry.getKey() != annual.year() || annual.requestedWorldCount() != pairedStates.requestedCount()
                    || previous != null && entry.getKey() != previous + 1) {
                throw new IllegalArgumentException("Annual reporting range must be contiguous and populations consistent.");
            }
            previous = entry.getKey();
        }
    }

    public int requestedCount() { return pairedStates.requestedCount(); }
}
