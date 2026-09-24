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
 * Authoritative mortality aggregates plus compact execution observations.
 * All four terminal distributions are conditional on funding through second
 * death. Values remain nominal future dollars at each world's own terminal
 * balance date, not a common valuation date; no discount or inflation adjustment
 * is applied. Lifetime taxes are the engine's nominal lifetime total. Reproduction requires
 * the same financial plan revision as well as these frozen generation inputs.
 * Caller-supplied worlds additionally require the caller's world source.
 * No deterministic reference is embedded here. The fixed-mode configured-horizon,
 * configured-death, expected-return reference remains a separate baseline; it is
 * neither a mortality median nor a same-horizon comparator.
 *
 * Existing engine limitations apply: deceased-owner accounts remain household
 * assets available for funding, without retitling or beneficiary schedules.
 * Deceased owners generate neither their own RMDs nor Roth conversions.
 */
public final class MonteCarloMortalityAnalysisResult {

    private final MonteCarloMortalityRequest request;
    private final String returnModel;
    private final String mortalityModel;
    private final List<WorldOutcome> outcomes;
    private final MonteCarloMortalityAccumulator.Aggregates aggregates;

    public MonteCarloMortalityAnalysisResult(
            MonteCarloMortalityRequest request,
            String returnModel,
            String mortalityModel,
            List<WorldOutcome> outcomes) {
        this.request = Objects.requireNonNull(request);
        this.returnModel = Objects.requireNonNull(returnModel);
        this.mortalityModel = Objects.requireNonNull(mortalityModel);
        this.outcomes = List.copyOf(outcomes);
        outcomes = this.outcomes;
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
                if (!outcome.annualInvestableAssets().containsKey(year)) {
                    throw new IllegalArgumentException("Completed prefix years must be contiguous.");
                }
            }
        }
        aggregates = MonteCarloMortalityAccumulator.reduce(first, outcomes);
    }

    public MonteCarloMortalityRequest request() {
        return request;
    }

    public String returnModel() {
        return returnModel;
    }

    public String mortalityModel() {
        return mortalityModel;
    }

    public List<WorldOutcome> outcomes() {
        return outcomes;
    }

    /** Projection start year; preparation requires mortality conditioning to match the plan start. */
    public int firstReportingYear() {
        return request.longevityAssumptions().mortalityBaseDate().getYear();
    }

    /** Latest sampled final living year, absent when every world ends at opening. No nominal-horizon cap. */
    public Optional<Integer> lastReportingYear() {
        return aggregates.annualResults().keySet().stream().max(Integer::compareTo);
    }

    /** Inclusive common reporting range, empty only when every world has opening-date second death. */
    public Map<Integer, MonteCarloMortalityAnnualResult> annualResults() {
        return aggregates.annualResults();
    }

    public Optional<MonteCarloPercentiles> endingInvestableAssets() {
        return aggregates.endingInvestableAssets();
    }

    public Optional<MonteCarloPercentiles> endingNetWorth() {
        return aggregates.endingNetWorth();
    }

    public Optional<MonteCarloPercentiles> afterTaxEstate() {
        return aggregates.afterTaxEstate();
    }

    public Optional<MonteCarloPercentiles> lifetimeTaxes() {
        return aggregates.lifetimeTaxes();
    }

    public Optional<LocalDate> earliestSuccessfulTerminalBalanceDate() {
        return aggregates.earliestSuccessfulTerminalBalanceDate();
    }

    public Optional<LocalDate> latestSuccessfulTerminalBalanceDate() {
        return aggregates.latestSuccessfulTerminalBalanceDate();
    }

    /** Counts by actual balance-date year, including the opening date for opening second death. */
    public Map<Integer, Integer> successfulTerminalYearCounts() {
        return aggregates.successfulTerminalYearCounts();
    }

    public int requestedSimulationCount() {
        return request.settings().simulationCount();
    }

    /** Worlds that completed every modeled obligation through their final living financial year. */
    public long completedCount() {
        return aggregates.completedCount();
    }

    public long fundingFailureCount() {
        return requestedSimulationCount() - completedCount();
    }

    /** Empirical lifetime funding fraction, not survival or positive-estate probability. */
    public BigDecimal fundingProbability() {
        return BigDecimal.valueOf(completedCount()).divide(
                BigDecimal.valueOf(requestedSimulationCount()), MathContext.DECIMAL128);
    }

    public Optional<FundingFailureStatistics> fundingFailureStatistics() {
        return aggregates.fundingFailureStatistics();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MonteCarloMortalityAnalysisResult that
                && request.equals(that.request)
                && returnModel.equals(that.returnModel)
                && mortalityModel.equals(that.mortalityModel)
                && outcomes.equals(that.outcomes)
                && aggregates.equals(that.aggregates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(request, returnModel, mortalityModel, outcomes, aggregates);
    }

    @Override
    public String toString() {
        return "MonteCarloMortalityAnalysisResult[request=" + request + ", returnModel=" + returnModel
                + ", mortalityModel=" + mortalityModel + ", outcomes=" + outcomes + ", aggregates=" + aggregates + "]";
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
