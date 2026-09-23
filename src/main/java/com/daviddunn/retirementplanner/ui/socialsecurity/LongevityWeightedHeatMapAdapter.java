package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedIntegratedStrategyComparisonEntry;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.IntStream;

/** Adapts finished weighted results without changing their objective or ordering. */
final class LongevityWeightedHeatMapAdapter {
    private static final List<Integer> STANDARD_AGES = IntStream.rangeClosed(62, 70).boxed().toList();
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    static ClaimingStrategyHeatMapModel from(LongevityWeightedIntegratedPresentation presentation) {
        Objects.requireNonNull(presentation);
        var result = presentation.result();
        var highest = presentation.highest().map(LongevityWeightedIntegratedPresentation::pv);
        Map<Pair, LongevityWeightedIntegratedStrategyComparisonEntry> best = new HashMap<>();
        Map<Pair, Integer> failures = new HashMap<>();
        // The backend's exact competition order also resolves ties in original occurrence order.
        // Selecting its first entry per pair preserves survivor optimization and never reranks candidates.
        for (var entry : result.rankedSuccessfulEntries()) {
            best.putIfAbsent(pair(entry), entry);
        }
        for (var entry : result.orderedEntries()) {
            if (!entry.successful()) failures.merge(pair(entry), 1, Integer::sum);
        }
        boolean validBaseline = result.baseline().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful).isPresent();
        List<ClaimingStrategyHeatMapCell> cells = new ArrayList<>();
        for (int spouseAge : STANDARD_AGES) {
            for (int primaryAge : STANDARD_AGES) {
                var pair = new Pair(primaryAge, spouseAge);
                var entry = best.get(pair);
                Optional<ClaimingStrategyHeatMapCell.Strategy> strategy = Optional.empty();
                if (entry != null && highest.isPresent()) {
                    var aggregate = entry.aggregate().orElseThrow();
                    BigDecimal pv = aggregate.expectedPvAfterTaxEstate();
                    BigDecimal optimal = highest.orElseThrow();
                    Optional<BigDecimal> percent = optimal.signum() > 0
                            ? Optional.of(pv.multiply(HUNDRED).divide(optimal, MathContext.DECIMAL128)) : Optional.empty();
                    strategy = Optional.of(new ClaimingStrategyHeatMapCell.Strategy(entry.inputOrder(), values(pv, optimal, percent,
                            validBaseline ? entry.pvDifferenceFromBaseline() : Optional.empty(), aggregate.expectedNominalEstateAtSecondDeath()),
                            survivor(entry.strategy().primarySurvivorElection()), survivor(entry.strategy().spouseSurvivorElection()),
                            pv.compareTo(optimal) == 0, entry.rank()));
                }
                cells.add(new ClaimingStrategyHeatMapCell(primaryAge, spouseAge, strategy, failures.getOrDefault(pair, 0)));
            }
        }
        return new ClaimingStrategyHeatMapModel(STANDARD_AGES, STANDARD_AGES, cells, highest, "Valuation date: " + result.metadata().valuationDate());
    }

    private static Map<ClaimingStrategyHeatMapMetric, BigDecimal> values(BigDecimal pv, BigDecimal optimal,
            Optional<BigDecimal> percent, Optional<BigDecimal> current, BigDecimal nominal) {
        Map<ClaimingStrategyHeatMapMetric, BigDecimal> values = new EnumMap<>(ClaimingStrategyHeatMapMetric.class);
        values.put(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE, pv);
        values.put(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL, pv.subtract(optimal));
        values.put(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE, nominal);
        percent.ifPresent(value -> values.put(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL, value));
        current.ifPresent(value -> values.put(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, value));
        return values;
    }

    private static ClaimingStrategyHeatMapCell.SurvivorElection survivor(SocialSecuritySurvivorClaimingCandidate candidate) {
        return new ClaimingStrategyHeatMapCell.SurvivorElection(candidate.ageYears(), candidate.ageMonths(), candidate.claimDate());
    }

    private static Pair pair(LongevityWeightedIntegratedStrategyComparisonEntry entry) {
        return new Pair(entry.strategy().primaryRetirementAge(), entry.strategy().spouseRetirementAge());
    }

    private record Pair(int primaryAge, int spouseAge) { }
}
