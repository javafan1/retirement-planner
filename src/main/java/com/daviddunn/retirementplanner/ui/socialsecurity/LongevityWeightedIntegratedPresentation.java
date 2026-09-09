package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Presentation arithmetic only. Backend ranks and original occurrences remain authoritative. */
record LongevityWeightedIntegratedPresentation(
        LongevityWeightedIntegratedStrategyComparisonResult result, long planRevision, long assumptionsRevision) {
    static boolean sameStrategy(SocialSecurityHouseholdClaimingStrategy a, SocialSecurityHouseholdClaimingStrategy b) {
        return identity(a).equals(identity(b));
    }

    static List<Object> identity(SocialSecurityHouseholdClaimingStrategy strategy) {
        return List.of(strategy.primaryRetirementAge(), strategy.spouseRetirementAge(),
                strategy.primaryRetirementClaimDate(), strategy.spouseRetirementClaimDate(),
                strategy.primarySurvivorElection().claimDate(), strategy.spouseSurvivorElection().claimDate());
    }

    Optional<LongevityWeightedIntegratedStrategyComparisonEntry> highest() {
        return result.rankedSuccessfulEntries().stream().findFirst();
    }

    Optional<LongevityWeightedIntegratedStrategyComparisonEntry> candidate(SocialSecurityHouseholdClaimingStrategy strategy) {
        return result.orderedEntries().stream().filter(entry -> sameStrategy(entry.strategy(), strategy)).findFirst();
    }

    long highestTieCount() {
        return highest().map(best -> result.rankedSuccessfulEntries().stream().filter(entry ->
                pv(entry).compareTo(pv(best)) == 0).count()).orElse(0L);
    }

    long currentTieCount() {
        return result.baseline().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .map(base -> result.rankedSuccessfulEntries().stream()
                        .filter(entry -> pv(entry).compareTo(pv(base)) == 0).count()).orElse(0L);
    }

    OptionalInt currentPosition() {
        return result.baseline().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .map(base -> candidate(base.strategy()).filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                        .map(LongevityWeightedIntegratedStrategyComparisonEntry::rank)
                        .orElseGet(() -> OptionalInt.of(1 + (int) result.rankedSuccessfulEntries().stream()
                                .filter(entry -> pv(entry).compareTo(pv(base)) > 0).count())))
                .orElseGet(OptionalInt::empty);
    }

    boolean currentHasCandidateRank() {
        return result.baseline().flatMap(base -> candidate(base.strategy()))
                .filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful).isPresent();
    }

    String marker(LongevityWeightedIntegratedStrategyComparisonEntry entry) {
        String marker = entry.rank().orElse(0) == 1 ? "Highest" : "";
        if (result.baseline().filter(base -> sameStrategy(base.strategy(), entry.strategy())).isPresent()) {
            marker += marker.isEmpty() ? "Current" : " / Current";
        }
        return marker;
    }

    int provenEquivalentCount(int order) {
        return result.equivalencePlan().flatMap(plan -> plan.groups().stream()
                .filter(group -> group.contains(order)).findFirst()).map(List::size).orElse(1);
    }

    Optional<BigDecimal> improvementPercent() {
        return result.baseline().filter(LongevityWeightedIntegratedStrategyComparisonEntry::successful)
                .flatMap(base -> highest().flatMap(best -> percent(pv(best).subtract(pv(base)), pv(base))));
    }

    static Optional<BigDecimal> percent(BigDecimal difference, BigDecimal baseline) {
        return baseline.signum() <= 0 ? Optional.empty()
                : Optional.of(difference.multiply(new BigDecimal("100")).divide(baseline, 2, RoundingMode.HALF_UP));
    }

    static BigDecimal pv(LongevityWeightedIntegratedStrategyComparisonEntry entry) {
        return entry.aggregate().orElseThrow().expectedPvAfterTaxEstate();
    }
}
