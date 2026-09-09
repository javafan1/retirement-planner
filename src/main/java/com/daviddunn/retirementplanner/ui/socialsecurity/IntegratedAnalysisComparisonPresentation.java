package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import java.math.BigDecimal;
import java.util.*;

/** Joins exact elections only, with compatible source revisions and complete search universes. */
final class IntegratedAnalysisComparisonPresentation {
    static final String EXPLANATION = "Deterministic analysis measures estate at the configured projection horizon. "
            + "Longevity-weighted analysis measures estate at second death across possible lifespans and converts it "
            + "to valuation-date present value. These objectives can favor different claiming strategies.";
    record Row(String role, SocialSecurityHouseholdClaimingStrategy strategy,
            OptionalInt deterministicRank, OptionalInt weightedRank,
            Optional<BigDecimal> deterministicEstate, Optional<BigDecimal> weightedPv) { }

    static boolean compatible(LongevityWeightedIntegratedPresentation weighted,
            IntegratedSocialSecurityCompleteStrategySearchResult deterministic,
            long deterministicRevision, long currentPlanRevision, boolean weightedCurrent) {
        return weightedCurrent && weighted.planRevision() == currentPlanRevision
                && deterministic != null && deterministicRevision == currentPlanRevision
                && deterministic.entries().size() == weighted.result().orderedEntries().size()
                && deterministic.entries().stream().collect(java.util.stream.Collectors.groupingBy(
                        entry -> LongevityWeightedIntegratedPresentation.identity(entry.strategy()), java.util.stream.Collectors.counting()))
                        .equals(weighted.result().orderedEntries().stream().collect(java.util.stream.Collectors.groupingBy(
                                entry -> LongevityWeightedIntegratedPresentation.identity(entry.strategy()), java.util.stream.Collectors.counting())));
    }

    static List<Row> create(LongevityWeightedIntegratedPresentation weighted,
            IntegratedSocialSecurityCompleteStrategySearchResult deterministic,
            long deterministicRevision, long currentPlanRevision, boolean weightedCurrent) {
        boolean compatible = compatible(weighted, deterministic, deterministicRevision, currentPlanRevision, weightedCurrent);
        List<Row> rows = new ArrayList<>();
        if (compatible && !deterministic.rankedSuccessfulEntries().isEmpty()) {
            add(rows, "Deterministic highest", deterministic.rankedSuccessfulEntries().getFirst().strategy());
        }
        weighted.highest().ifPresent(entry -> add(rows, "Longevity-weighted highest", entry.strategy()));
        weighted.result().baseline().ifPresent(entry -> add(rows, "Current", entry.strategy()));
        return rows.stream().map(row -> {
            var candidate = weighted.candidate(row.strategy());
            var baseline = weighted.result().baseline().filter(entry ->
                    LongevityWeightedIntegratedPresentation.sameStrategy(entry.strategy(), row.strategy()));
            var value = candidate.filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                    .or(() -> baseline.filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful));
            var det = compatible ? deterministic.entryFor(row.strategy()) : Optional.<IntegratedSocialSecurityCompleteStrategySearchEntry>empty();
            boolean current = compatible && baseline.isPresent();
            return new Row(row.role(), row.strategy(), det.map(IntegratedSocialSecurityCompleteStrategySearchEntry::afterTaxEstateRank)
                    .orElseGet(OptionalInt::empty), candidate.map(LongevityWeightedIntegratedStrategyComparisonEntry::rank)
                    .orElseGet(OptionalInt::empty), det.flatMap(IntegratedSocialSecurityCompleteStrategySearchEntry::metrics)
                    .map(metrics -> metrics.afterTaxEstate()).or(() -> current
                            ? Optional.of(deterministic.currentPlanBaseline().metrics().afterTaxEstate()) : Optional.empty()),
                    value.map(LongevityWeightedIntegratedPresentation::pv));
        }).toList();
    }

    private static void add(List<Row> rows, String role, SocialSecurityHouseholdClaimingStrategy strategy) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (LongevityWeightedIntegratedPresentation.sameStrategy(row.strategy(), strategy)) {
                rows.set(i, new Row(row.role() + " / " + role, strategy, OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty()));
                return;
            }
        }
        rows.add(new Row(role, strategy, OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty()));
    }
}
