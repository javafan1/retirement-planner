package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.Objects;
import java.util.Optional;

/** Immutable overrides scoped to one projection execution. */
public record ProjectionEvaluationContext(
        Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
        Optional<HouseholdLifetimeScenario> householdLifetimeScenario,
        Optional<Integer> endingYearOverride) {

    public ProjectionEvaluationContext {
        socialSecurityStrategy = Objects.requireNonNull(
                socialSecurityStrategy,
                "Social Security strategy override is required.");
        householdLifetimeScenario = Objects.requireNonNull(
                householdLifetimeScenario, "Lifetime scenario optional is required.");
        endingYearOverride = Objects.requireNonNull(endingYearOverride, "Ending year optional is required.");
        endingYearOverride.ifPresent(java.time.Year::of);
    }

    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
            Optional<HouseholdLifetimeScenario> householdLifetimeScenario) {
        this(socialSecurityStrategy, householdLifetimeScenario, Optional.empty());
    }

    /** Extension only: the engine retains at least the configured horizon. */
    public ProjectionEvaluationContext withEndingYear(int endingYear) {
        return new ProjectionEvaluationContext(socialSecurityStrategy,
                householdLifetimeScenario, Optional.of(endingYear));
    }

    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy) {
        this(socialSecurityStrategy, Optional.empty());
    }

    public static ProjectionEvaluationContext empty() {
        return new ProjectionEvaluationContext(Optional.empty());
    }

    public static ProjectionEvaluationContext withSocialSecurityStrategy(
            SocialSecurityHouseholdClaimingStrategy strategy) {
        return new ProjectionEvaluationContext(Optional.of(
                Objects.requireNonNull(strategy, "Social Security strategy is required.")));
    }

    public static ProjectionEvaluationContext withLifetimeScenario(HouseholdLifetimeScenario scenario) {
        return new ProjectionEvaluationContext(Optional.empty(), Optional.of(
                Objects.requireNonNull(scenario, "Lifetime scenario is required.")));
    }

    public static ProjectionEvaluationContext withSocialSecurityStrategy(
            SocialSecurityHouseholdClaimingStrategy strategy,
            HouseholdLifetimeScenario scenario) {
        return new ProjectionEvaluationContext(
                Optional.of(Objects.requireNonNull(strategy, "Social Security strategy is required.")),
                Optional.of(Objects.requireNonNull(scenario, "Lifetime scenario is required.")));
    }
}
