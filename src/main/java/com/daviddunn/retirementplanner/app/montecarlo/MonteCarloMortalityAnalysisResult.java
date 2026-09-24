package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Compact execution observations, without mortality percentile aggregation.
 * Values are nominal dollars at each world's own dates. Reproduction requires
 * the same financial plan revision as well as these frozen generation inputs.
 * Caller-supplied worlds additionally require the caller's world source.
 *
 * Existing engine limitations apply: deceased-owner accounts remain household
 * assets available for funding, without retitling or beneficiary schedules.
 * Deceased owners generate neither their own RMDs nor Roth conversions.
 */
public record MonteCarloMortalityAnalysisResult(
        MonteCarloMortalityRequest request,
        String returnModel,
        String mortalityModel,
        List<WorldOutcome> outcomes) {

    public MonteCarloMortalityAnalysisResult {
        Objects.requireNonNull(request);
        Objects.requireNonNull(returnModel);
        Objects.requireNonNull(mortalityModel);
        outcomes = List.copyOf(outcomes);
        if (outcomes.size() != request.settings().simulationCount()) {
            throw new IllegalArgumentException("Every requested mortality world needs an outcome.");
        }
        int first = request.longevityAssumptions().mortalityBaseDate().getYear();
        for (int index = 0; index < outcomes.size(); index++) {
            var outcome = outcomes.get(index);
            if (outcome.scenarioIndex() != index) {
                throw new IllegalArgumentException("World outcomes must retain scenario index order.");
            }
            int lastCompleted = outcome.fundingFailure()
                    .map(failure -> failure.calendarYear() - 1)
                    .orElse(outcome.finalLivingFinancialYear());
            if (lastCompleted < first - 1
                    || outcome.annualInvestableAssets().size() != lastCompleted - first + 1) {
                throw new IllegalArgumentException("World must retain its complete annual prefix.");
            }
            for (int year = first; year <= lastCompleted; year++) {
                Objects.requireNonNull(outcome.annualInvestableAssets().get(year),
                        "Completed prefix years must be contiguous.");
            }
        }
    }

    public int requestedSimulationCount() {
        return request.settings().simulationCount();
    }

    public long completedCount() {
        return outcomes.stream().filter(outcome -> outcome.terminal().isPresent()).count();
    }

    public long fundingFailureCount() {
        return outcomes.stream().filter(outcome -> outcome.fundingFailure().isPresent()).count();
    }

    /** Empirical lifetime funding fraction, not survival or positive-estate probability. */
    public BigDecimal fundingProbability() {
        return BigDecimal.valueOf(completedCount()).divide(
                BigDecimal.valueOf(requestedSimulationCount()), MathContext.DECIMAL128);
    }

    public Optional<FundingFailureStatistics> fundingFailureStatistics() {
        return FundingFailureStatistics.from(outcomes.stream()
                .flatMap(outcome -> outcome.fundingFailure().stream()).toList(), requestedSimulationCount());
    }

    /** No terminal metric (including lifetime taxes) exists for a funding-failed world. */
    public record WorldOutcome(
            int scenarioIndex,
            HouseholdLifetimeScenario lifetimeScenario,
            Map<Integer, BigDecimal> annualInvestableAssets,
            Optional<TerminalOutcome> terminal,
            Optional<FundingFailure> fundingFailure) {

        public WorldOutcome {
            Objects.requireNonNull(lifetimeScenario);
            Objects.requireNonNull(terminal);
            Objects.requireNonNull(fundingFailure);
            if (scenarioIndex < 0 || lifetimeScenario.primaryDeathYear().isEmpty()
                    || lifetimeScenario.spouseDeathYear().isEmpty()) {
                throw new IllegalArgumentException("An indexed complete lifetime is required.");
            }
            if (terminal.isPresent() == fundingFailure.isPresent()) {
                throw new IllegalArgumentException("Exactly one world outcome is required.");
            }
            annualInvestableAssets = Collections.unmodifiableMap(new TreeMap<>(annualInvestableAssets));
            annualInvestableAssets.values().forEach(Objects::requireNonNull);
            int secondDeath = Math.max(lifetimeScenario.primaryDeathYear().orElseThrow().getValue(),
                    lifetimeScenario.spouseDeathYear().orElseThrow().getValue());
            if (fundingFailure.isPresent() && fundingFailure.orElseThrow().calendarYear() >= secondDeath) {
                throw new IllegalArgumentException("Funding failure must precede second death.");
            }
        }

        public int secondDeathYear() {
            return Math.max(lifetimeScenario.primaryDeathYear().orElseThrow().getValue(),
                    lifetimeScenario.spouseDeathYear().orElseThrow().getValue());
        }

        public int finalLivingFinancialYear() {
            return secondDeathYear() - 1;
        }
    }

    /** Opening-date death uses the opening balance date and zero modeled lifetime taxes. */
    public record TerminalOutcome(
            LocalDate balanceDate,
            BigDecimal endingInvestableAssets,
            BigDecimal endingNetWorth,
            BigDecimal afterTaxEstate,
            BigDecimal lifetimeTaxes) {

        public TerminalOutcome {
            Objects.requireNonNull(balanceDate);
            Objects.requireNonNull(endingInvestableAssets);
            Objects.requireNonNull(endingNetWorth);
            Objects.requireNonNull(afterTaxEstate);
            Objects.requireNonNull(lifetimeTaxes);
        }
    }
}
