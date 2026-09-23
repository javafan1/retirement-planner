package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.IntStream;

/** Copies compact deterministic results; never evaluates strategies or changes backend ranking. */
final class DeterministicHeatMapAdapter {
    static ClaimingStrategyHeatMapModel from(ExhaustiveIntegratedSearchPresentation presentation) {
        var result = Objects.requireNonNull(presentation).result();
        if (result.rankingMeasure() != IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE) {
            throw new IllegalArgumentException("Heat map requires the deterministic after-tax-estate objective.");
        }
        var highest = result.rankedSuccessfulEntries().stream().findFirst()
                .map(entry -> entry.metrics().orElseThrow().afterTaxEstate());
        Map<Pair, IntegratedSocialSecurityCompleteStrategySearchEntry> best = new HashMap<>();
        Map<Pair, Integer> failures = new HashMap<>();
        for (var entry : result.rankedSuccessfulEntries()) best.putIfAbsent(pair(entry), entry);
        for (var entry : result.entries()) if (!entry.successful()) failures.merge(pair(entry), 1, Integer::sum);
        var ages = IntStream.rangeClosed(62, 70).boxed().toList();
        List<ClaimingStrategyHeatMapCell> cells = new ArrayList<>();
        for (int spouse : ages) for (int primary : ages) {
            var pair = new Pair(primary, spouse);
            var entry = best.get(pair);
            Optional<ClaimingStrategyHeatMapCell.Strategy> strategy = Optional.empty();
            if (entry != null && highest.isPresent()) {
                BigDecimal estate = entry.metrics().orElseThrow().afterTaxEstate();
                BigDecimal optimal = highest.orElseThrow();
                Map<ClaimingStrategyHeatMapMetric, BigDecimal> values = new EnumMap<>(ClaimingStrategyHeatMapMetric.class);
                values.put(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE, estate);
                values.put(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL, estate.subtract(optimal));
                values.put(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT,
                        entry.differencesFromCurrentPlan().orElseThrow().afterTaxEstate());
                if (optimal.signum() > 0) values.put(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                        estate.multiply(new BigDecimal("100")).divide(optimal, MathContext.DECIMAL128));
                strategy = Optional.of(new ClaimingStrategyHeatMapCell.Strategy(entry.generationOrder(), values,
                        survivor(entry.strategy().primarySurvivorElection()), survivor(entry.strategy().spouseSurvivorElection()),
                        estate.compareTo(optimal) == 0, entry.afterTaxEstateRank()));
            }
            cells.add(new ClaimingStrategyHeatMapCell(primary, spouse, strategy, failures.getOrDefault(pair, 0)));
        }
        return new ClaimingStrategyHeatMapModel(ages, ages, cells, highest,
                "Deterministic future dollars — projection ending "
                        + (result.currentPlanBaseline().projection().getYears().isEmpty() ? "Not Available"
                        : result.currentPlanBaseline().projection().getLastYear().getCalendarYear()));
    }

    private static ClaimingStrategyHeatMapCell.SurvivorElection survivor(SocialSecuritySurvivorClaimingCandidate election) {
        return new ClaimingStrategyHeatMapCell.SurvivorElection(election.ageYears(), election.ageMonths(), election.claimDate());
    }

    private static Pair pair(IntegratedSocialSecurityCompleteStrategySearchEntry entry) {
        return new Pair(entry.strategy().primaryRetirementAge(), entry.strategy().spouseRetirementAge());
    }

    private record Pair(int primary, int spouse) { }
}
