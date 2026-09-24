package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult.WorldOutcome;

/**
 * Reduces validated compact world outcomes only; never executes financial logic.
 * One annual sample list is live at a time, and each distribution is sorted once
 * by the existing Type-7 implementation. No full projections or sample copies
 * are retained in the aggregate.
 */
final class MonteCarloMortalityAccumulator {

    private MonteCarloMortalityAccumulator() {
    }

    static Aggregates reduce(int firstYear, List<WorldOutcome> outcomes) {
        int lastYear = outcomes.stream().mapToInt(WorldOutcome::finalLivingFinancialYear).max().orElseThrow();
        var annual = new TreeMap<Integer, MonteCarloMortalityAnnualResult>();
        for (int year = firstYear; year <= lastYear; year++) {
            var samples = new ArrayList<BigDecimal>(outcomes.size());
            int bothAlive = 0;
            int primaryOnly = 0;
            int spouseOnly = 0;
            int deceased = 0;
            int failed = 0;
            for (var outcome : outcomes) {
                boolean primaryAlive = year < outcome.lifetimeScenario().primaryDeathYear().orElseThrow().getValue();
                boolean spouseAlive = year < outcome.lifetimeScenario().spouseDeathYear().orElseThrow().getValue();
                if (!primaryAlive && !spouseAlive) {
                    deceased++;
                    continue;
                }
                if (primaryAlive && spouseAlive) {
                    bothAlive++;
                } else if (primaryAlive) {
                    primaryOnly++;
                } else {
                    spouseOnly++;
                }
                var observation = outcome.annualInvestableAssets().get(year);
                if (observation != null) {
                    samples.add(observation);
                } else if (outcome.fundingFailure().isPresent()
                        && outcome.fundingFailure().orElseThrow().calendarYear() <= year) {
                    failed++;
                } else {
                    throw new IllegalArgumentException("Living world lacks a completed row or actual funding failure.");
                }
            }
            annual.put(year, new MonteCarloMortalityAnnualResult(year, outcomes.size(),
                    bothAlive + primaryOnly + spouseOnly, samples.size(), failed, deceased,
                    bothAlive, primaryOnly, spouseOnly, MonteCarloPercentiles.of(samples)));
        }

        var assets = new ArrayList<BigDecimal>();
        var netWorth = new ArrayList<BigDecimal>();
        var estate = new ArrayList<BigDecimal>();
        var taxes = new ArrayList<BigDecimal>();
        var terminalYears = new TreeMap<Integer, Integer>();
        LocalDate earliest = null;
        LocalDate latest = null;
        for (var outcome : outcomes) {
            if (outcome.terminal().isEmpty()) {
                continue;
            }
            var terminal = outcome.terminal().orElseThrow();
            assets.add(terminal.endingInvestableAssets());
            netWorth.add(terminal.endingNetWorth());
            estate.add(terminal.afterTaxEstate());
            taxes.add(terminal.lifetimeTaxes());
            var date = terminal.balanceDate();
            terminalYears.merge(date.getYear(), 1, Integer::sum);
            if (earliest == null || date.isBefore(earliest)) {
                earliest = date;
            }
            if (latest == null || date.isAfter(latest)) {
                latest = date;
            }
        }
        return new Aggregates(annual, assets.size(),
                MonteCarloPercentiles.of(assets), MonteCarloPercentiles.of(netWorth),
                MonteCarloPercentiles.of(estate), MonteCarloPercentiles.of(taxes),
                Optional.ofNullable(earliest), Optional.ofNullable(latest), terminalYears,
                FundingFailureStatistics.from(outcomes.stream()
                        .flatMap(outcome -> outcome.fundingFailure().stream()).toList(), outcomes.size()));
    }

    record Aggregates(
            Map<Integer, MonteCarloMortalityAnnualResult> annualResults,
            int completedCount,
            Optional<MonteCarloPercentiles> endingInvestableAssets,
            Optional<MonteCarloPercentiles> endingNetWorth,
            Optional<MonteCarloPercentiles> afterTaxEstate,
            Optional<MonteCarloPercentiles> lifetimeTaxes,
            Optional<LocalDate> earliestSuccessfulTerminalBalanceDate,
            Optional<LocalDate> latestSuccessfulTerminalBalanceDate,
            Map<Integer, Integer> successfulTerminalYearCounts,
            Optional<FundingFailureStatistics> fundingFailureStatistics) {

        Aggregates {
            annualResults = Collections.unmodifiableMap(new TreeMap<>(annualResults));
            successfulTerminalYearCounts = Collections.unmodifiableMap(new TreeMap<>(successfulTerminalYearCounts));
        }
    }
}
