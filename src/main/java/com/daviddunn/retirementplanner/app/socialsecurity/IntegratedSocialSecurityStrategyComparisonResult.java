package com.daviddunn.retirementplanner.app.socialsecurity;

import java.util.List;
import java.util.Objects;

/** Baseline plus ordered, deduplicated deterministic candidate evaluations. */
public record IntegratedSocialSecurityStrategyComparisonResult(
        IntegratedSocialSecurityStrategyResult currentPlanBaseline,
        int suppliedCandidateCount,
        List<IntegratedSocialSecurityStrategyComparisonEntry> entries) {

    public IntegratedSocialSecurityStrategyComparisonResult {
        Objects.requireNonNull(currentPlanBaseline, "Current-plan baseline is required.");
        if (suppliedCandidateCount < 0) {
            throw new IllegalArgumentException("Supplied candidate count cannot be negative.");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "Entries are required."));
        if (entries.size() > suppliedCandidateCount) {
            throw new IllegalArgumentException(
                    "Unique entry count cannot exceed supplied candidate count.");
        }
    }

    public int uniqueCandidateCount() {
        return entries.size();
    }
}
