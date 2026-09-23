package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Terminal distributions, including lifetime taxes, condition on full-horizon funding.
 * Annual samples include every completed year, including prefixes of funding failures.
 * Unexpected errors abort analysis; returned outcomes reconcile to the requested count.
 */
public record MonteCarloAnalysisResult(
        MonteCarloSettings settings,
        String returnModel,
        List<RunOutcome> outcomes,
        Map<Integer, Optional<MonteCarloPercentiles>> annualInvestableAssets,
        Optional<MonteCarloPercentiles> endingInvestableAssets,
        Optional<MonteCarloPercentiles> endingNetWorth,
        Optional<MonteCarloPercentiles> endingAfterTaxEstate,
        Optional<MonteCarloPercentiles> lifetimeTaxes,
        Reference deterministicReference) {

    public MonteCarloAnalysisResult {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(returnModel);
        Objects.requireNonNull(endingInvestableAssets);
        Objects.requireNonNull(endingNetWorth);
        Objects.requireNonNull(endingAfterTaxEstate);
        Objects.requireNonNull(lifetimeTaxes);
        Objects.requireNonNull(deterministicReference);
        outcomes = List.copyOf(outcomes);
        if (outcomes.size() != settings.simulationCount()) {
            throw new IllegalArgumentException("Every requested simulation needs an outcome.");
        }
        var indices = new HashSet<Integer>();
        for (var outcome : outcomes) {
            if (outcome.scenarioIndex() < 0
                    || outcome.scenarioIndex() >= settings.simulationCount()
                    || !indices.add(outcome.scenarioIndex())) {
                throw new IllegalArgumentException("Scenario indices must occur exactly once.");
            }
        }
        annualInvestableAssets = Collections.unmodifiableMap(new TreeMap<>(annualInvestableAssets));
    }

    public enum RunStatus {
        COMPLETED,
        INSUFFICIENT_FUNDS
    }

    public enum AnnualPercentileBasis {
        SIMULATIONS_COMPLETING_EACH_YEAR
    }

    public AnnualPercentileBasis annualPercentileBasis() {
        return AnnualPercentileBasis.SIMULATIONS_COMPLETING_EACH_YEAR;
    }

    public record RunOutcome(
            int scenarioIndex,
            Optional<ProjectionMetrics> metrics,
            Optional<Integer> firstNonpositiveYearEnd,
            Optional<FundingFailure> fundingFailure) {

        public RunOutcome {
            Objects.requireNonNull(metrics);
            Objects.requireNonNull(firstNonpositiveYearEnd);
            Objects.requireNonNull(fundingFailure);
            if (metrics.isPresent() == fundingFailure.isPresent()) {
                throw new IllegalArgumentException("Exactly one outcome is required.");
            }
            if (fundingFailure.isPresent() && firstNonpositiveYearEnd.isPresent()) {
                throw new IllegalArgumentException("Year-end observations require a completed simulation.");
            }
        }

        public RunStatus status() {
            return metrics.isPresent() ? RunStatus.COMPLETED : RunStatus.INSUFFICIENT_FUNDS;
        }
    }

    public record Reference(
            Map<Integer, BigDecimal> annualInvestableAssets,
            Optional<ProjectionMetrics> metrics,
            Optional<FundingFailure> fundingFailure) {

        public Reference {
            annualInvestableAssets = Collections.unmodifiableMap(new TreeMap<>(annualInvestableAssets));
            Objects.requireNonNull(metrics);
            Objects.requireNonNull(fundingFailure);
            if (metrics.isPresent() == fundingFailure.isPresent()) {
                throw new IllegalArgumentException("Exactly one reference outcome is required.");
            }
        }
    }

    public long completedCount() {
        return outcomes.stream().filter(outcome -> outcome.metrics().isPresent()).count();
    }

    public long fundingFailureCount() {
        return outcomes.stream().filter(outcome -> outcome.fundingFailure().isPresent()).count();
    }

    public long completedSimulationCount() {
        return completedCount();
    }

    public BigDecimal fundingProbability() {
        return fundingRate(completedCount());
    }

    public BigDecimal fundingFailureProbability() {
        return fundingRate(fundingFailureCount());
    }

    private BigDecimal fundingRate(long count) {
        return BigDecimal.valueOf(count).divide(
                BigDecimal.valueOf(settings.simulationCount()), MathContext.DECIMAL128);
    }

    public Optional<FundingFailureStatistics> fundingFailureStatistics() {
        return FundingFailureStatistics.from(
                outcomes.stream().flatMap(outcome -> outcome.fundingFailure().stream()).toList(),
                settings.simulationCount());
    }

    public long observedNonpositiveCount() {
        return outcomes.stream().filter(outcome -> outcome.firstNonpositiveYearEnd().isPresent()).count();
    }

    public long positiveEveryYearCompletedCount() {
        return completedCount() - observedNonpositiveCount();
    }

    public Optional<BigDecimal> probabilityPositiveEveryYearAmongCompleted() {
        if (completedCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(positiveEveryYearCompletedCount()).divide(
                BigDecimal.valueOf(completedCount()), MathContext.DECIMAL128));
    }

    public Optional<BigDecimal> probabilityNonpositiveYearEndAmongCompleted() {
        return probabilityPositiveEveryYearAmongCompleted().map(probability -> BigDecimal.ONE.subtract(probability));
    }

    public Optional<MonteCarloPercentiles> observedFirstNonpositiveYearDistribution() {
        return MonteCarloPercentiles.of(
                outcomes.stream().flatMap(outcome -> outcome.firstNonpositiveYearEnd().stream())
                        .map(BigDecimal::valueOf).toList());
    }
}
