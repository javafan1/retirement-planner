package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Compact outcome for every generated complete strategy. */
public record IntegratedSocialSecurityCompleteStrategySearchEntry(
        int generationOrder,
        SocialSecurityHouseholdClaimingStrategy strategy,
        OptionalInt afterTaxEstateRank,
        Optional<ProjectionMetrics> metrics,
        Optional<ProjectionMetrics> differencesFromCurrentPlan,
        Optional<IntegratedSocialSecurityStrategyResult> retainedDetail,
        Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure) {

    public IntegratedSocialSecurityCompleteStrategySearchEntry {
        if (generationOrder < 1) {
            throw new IllegalArgumentException("Generation order must be positive.");
        }
        Objects.requireNonNull(strategy);
        afterTaxEstateRank = Objects.requireNonNull(afterTaxEstateRank);
        metrics = Objects.requireNonNull(metrics);
        differencesFromCurrentPlan = Objects.requireNonNull(differencesFromCurrentPlan);
        retainedDetail = Objects.requireNonNull(retainedDetail);
        failure = Objects.requireNonNull(failure);
        if (metrics.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Entry requires either metrics or failure.");
        }
        if (metrics.isPresent() != differencesFromCurrentPlan.isPresent()
                || metrics.isPresent() != afterTaxEstateRank.isPresent()) {
            throw new IllegalArgumentException(
                    "Successful entries require metrics, differences, and rank.");
        }
        if (failure.isPresent() && retainedDetail.isPresent()) {
            throw new IllegalArgumentException("Failed entries cannot retain projection detail.");
        }
    }

    public boolean successful() {
        return metrics.isPresent();
    }
}
