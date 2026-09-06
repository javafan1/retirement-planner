package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.Objects;
import java.util.Optional;

/** Immutable overrides scoped to one projection execution. */
public record ProjectionEvaluationContext(
        Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy) {

    public ProjectionEvaluationContext {
        socialSecurityStrategy = Objects.requireNonNull(
                socialSecurityStrategy,
                "Social Security strategy override is required.");
    }

    public static ProjectionEvaluationContext empty() {
        return new ProjectionEvaluationContext(Optional.empty());
    }

    public static ProjectionEvaluationContext withSocialSecurityStrategy(
            SocialSecurityHouseholdClaimingStrategy strategy) {
        return new ProjectionEvaluationContext(Optional.of(
                Objects.requireNonNull(strategy, "Social Security strategy is required.")));
    }
}
