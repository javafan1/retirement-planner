package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Single traversal of authoritative pairs. No engine, candidate plans, generators, or random streams.
 * Temporary difference samples scale O(worlds * years); retained summaries scale O(years). */
public final class MonteCarloStrategyComparisonAccumulator {
    private MonteCarloStrategyComparisonAccumulator() { }

    public static MonteCarloStrategyComparisonSummary reduce(
            MonteCarloStrategyComparisonRequest.Assumptions assumptions,
            List<MonteCarloPairedOutcome> outcomes) {
        Objects.requireNonNull(assumptions);
        Objects.requireNonNull(outcomes);
        int requested = assumptions.settings().simulationCount();
        if (outcomes.size() != requested) {
            throw new IllegalArgumentException("Every requested world needs a pair.");
        }
        int first = assumptions.start().getYear();
        int[] states = new int[4];
        var assets = new ArrayList<BigDecimal>();
        var netWorth = new ArrayList<BigDecimal>();
        var estate = new ArrayList<BigDecimal>();
        var taxes = new ArrayList<BigDecimal>();
        var failuresA = new ArrayList<FundingFailure>();
        var failuresB = new ArrayList<FundingFailure>();
        var years = new TreeMap<Integer, AnnualSamples>();
        int index = 0;
        for (var pair : outcomes) {
            if (pair.scenarioIndex() != index++) {
                throw new IllegalArgumentException("Pairs must retain scenario order.");
            }
            int last;
            if (assumptions instanceof MonteCarloStrategyComparisonRequest.Fixed fixed) {
                if (!fixed.lifetimeScenario().equals(pair.lifetimeScenario())) {
                    throw new IllegalArgumentException("Fixed pair must match configured mortality.");
                }
                last = fixed.endingYear();
            } else {
                last = Math.max(pair.lifetimeScenario().primaryDeathYear().orElseThrow().getValue(),
                        pair.lifetimeScenario().spouseDeathYear().orElseThrow().getValue()) - 1;
                if (LocalDate.of(last + 1, 1, 1).isBefore(assumptions.start())) {
                    throw new IllegalArgumentException("Second death precedes opening balances.");
                }
            }
            validateSide(pair.outcomeA(), assumptions.start(), last);
            validateSide(pair.outcomeB(), assumptions.start(), last);
            states[pair.status().ordinal()]++;
            pair.outcomeA().fundingFailure().ifPresent(failuresA::add);
            pair.outcomeB().fundingFailure().ifPresent(failuresB::add);
            pair.deltas().ifPresent(delta -> {
                assets.add(delta.investableAssets());
                netWorth.add(delta.netWorth());
                estate.add(delta.afterTaxEstate());
                taxes.add(delta.lifetimeTaxes());
            });
            for (int year = first; year <= last; year++) {
                var samples = years.computeIfAbsent(year, ignored -> new AnnualSamples());
                samples.living++;
                var a = pair.outcomeA().annualInvestableAssets().get(year);
                var b = pair.outcomeB().annualInvestableAssets().get(year);
                if (a != null && b != null) {
                    samples.differences.add(a.subtract(b));
                } else if (a != null) {
                    samples.aCompletedBFailed++;
                } else if (b != null) {
                    samples.aFailedBCompleted++;
                } else {
                    samples.bothFailed++;
                }
            }
        }
        var annual = new TreeMap<Integer, MonteCarloPairedAnnualResult>();
        years.forEach((year, samples) -> annual.put(year, new MonteCarloPairedAnnualResult(
                year, requested, samples.living, requested - samples.living,
                samples.aCompletedBFailed, samples.aFailedBCompleted, samples.bothFailed,
                MonteCarloPairedMetricSummary.fromDifferences(samples.differences))));
        return new MonteCarloStrategyComparisonSummary(
                new MonteCarloPairedStateSummary(requested, states[0], states[1], states[2], states[3]),
                FundingFailureStatistics.from(failuresA, requested), FundingFailureStatistics.from(failuresB, requested),
                MonteCarloPairedMetricSummary.fromDifferences(assets),
                MonteCarloPairedMetricSummary.fromDifferences(netWorth),
                MonteCarloPairedMetricSummary.fromDifferences(estate),
                MonteCarloPairedMetricSummary.fromDifferences(taxes), annual);
    }

    private static void validateSide(MonteCarloStrategyOutcome side, LocalDate start, int last) {
        int first = start.getYear();
        int completedLast = side.fundingFailure().map(failure -> failure.calendarYear() - 1).orElse(last);
        if (completedLast < first - 1 || completedLast > last
                || side.fundingFailure().isPresent() && completedLast >= last
                || side.annualInvestableAssets().size() != completedLast - first + 1) {
            throw new IllegalArgumentException("Outcome must contain its authoritative completed prefix.");
        }
        int expected = first;
        for (int year : side.annualInvestableAssets().keySet()) {
            if (year != expected++) {
                throw new IllegalArgumentException("Annual completed prefix must be contiguous.");
            }
        }
        var expectedDate = last < first ? start : LocalDate.of(last, 12, 31);
        side.terminal().ifPresent(terminal -> {
            if (!terminal.balanceDate().equals(expectedDate)) {
                throw new IllegalArgumentException("Terminal date must match the shared world horizon.");
            }
        });
    }

    private static final class AnnualSamples {
        private int living;
        private int aCompletedBFailed;
        private int aFailedBCompleted;
        private int bothFailed;
        private final List<BigDecimal> differences = new ArrayList<>();
    }
}
