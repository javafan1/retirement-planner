package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchResult;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** UI-independent grouping of financially identical ranked search outcomes. */
record ExhaustiveIntegratedSearchPresentation(
        IntegratedSocialSecurityCompleteStrategySearchResult result,
        List<Group> groups,
        Duration runtime) {

    static ExhaustiveIntegratedSearchPresentation from(
            IntegratedSocialSecurityCompleteStrategySearchResult result,
            Duration runtime,
            int displayedGroupLimit) {
        Objects.requireNonNull(result);
        Objects.requireNonNull(runtime);
        if (displayedGroupLimit < 1) {
            throw new IllegalArgumentException("Displayed group limit must be positive.");
        }
        Map<FinancialKey, GroupAccumulator> grouped = new LinkedHashMap<>();
        for (IntegratedSocialSecurityCompleteStrategySearchEntry entry
                : result.rankedSuccessfulEntries()) {
            ProjectionMetrics metrics = entry.metrics().orElseThrow();
            FinancialKey key = new FinancialKey(
                    metrics.afterTaxEstate(),
                    metrics.endingInvestableAssets(),
                    metrics.totalTaxes(),
                    metrics.lifetimePortfolioWithdrawals(),
                    metrics.lifetimeHouseholdSocialSecurity());
            grouped.computeIfAbsent(key, ignored -> new GroupAccumulator(entry)).increment();
        }
        List<Group> groups = grouped.values().stream()
                .limit(displayedGroupLimit)
                .map(GroupAccumulator::group)
                .toList();
        return new ExhaustiveIntegratedSearchPresentation(result, groups, runtime);
    }

    record Group(
            IntegratedSocialSecurityCompleteStrategySearchEntry representative,
            int equivalentStrategyCount) {
        Group {
            Objects.requireNonNull(representative);
            if (equivalentStrategyCount < 1) {
                throw new IllegalArgumentException("Equivalent count must be positive.");
            }
        }
    }

    private record FinancialKey(
            BigDecimal afterTaxEstate,
            BigDecimal endingInvestableAssets,
            BigDecimal totalTaxes,
            BigDecimal withdrawals,
            BigDecimal householdSocialSecurity) {
    }

    private static final class GroupAccumulator {
        private final IntegratedSocialSecurityCompleteStrategySearchEntry representative;
        private int count;

        private GroupAccumulator(IntegratedSocialSecurityCompleteStrategySearchEntry representative) {
            this.representative = representative;
        }

        private void increment() {
            count++;
        }

        private Group group() {
            return new Group(representative, count);
        }
    }
}
