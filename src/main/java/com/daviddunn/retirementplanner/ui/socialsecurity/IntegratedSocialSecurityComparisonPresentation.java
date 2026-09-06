package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonResult;

import java.util.List;
import java.util.Objects;

/** Pure mapping from backend comparison entries to SS-ranked UI rows. */
public record IntegratedSocialSecurityComparisonPresentation(
        IntegratedSocialSecurityStrategyComparisonResult comparison,
        List<Row> rows) {

    public IntegratedSocialSecurityComparisonPresentation {
        Objects.requireNonNull(comparison, "Comparison result is required.");
        rows = List.copyOf(Objects.requireNonNull(rows, "Rows are required."));
    }

    public static IntegratedSocialSecurityComparisonPresentation from(
            IntegratedSocialSecurityStrategyComparisonResult comparison,
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected) {
        Objects.requireNonNull(comparison, "Comparison result is required.");
        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> ordered =
                List.copyOf(Objects.requireNonNull(selected, "Selected strategies are required."));
        List<Row> rows = comparison.entries().stream().map(entry -> {
            int index = entry.callerOrder() - 1;
            if (index < 0 || index >= ordered.size()) {
                throw new IllegalArgumentException(
                        "Comparison caller order does not match selected analyzer candidates.");
            }
            return new Row(ordered.get(index).rank(), entry);
        }).toList();
        return new IntegratedSocialSecurityComparisonPresentation(comparison, rows);
    }

    public long successfulCount() {
        return rows.stream().filter(row -> row.entry().successful()).count();
    }

    public long failureCount() {
        return rows.size() - successfulCount();
    }

    public record Row(
            int socialSecurityRank,
            IntegratedSocialSecurityStrategyComparisonEntry entry) {
        public Row {
            if (socialSecurityRank < 1) {
                throw new IllegalArgumentException("Social Security rank must be positive.");
            }
            Objects.requireNonNull(entry, "Comparison entry is required.");
        }
    }
}
