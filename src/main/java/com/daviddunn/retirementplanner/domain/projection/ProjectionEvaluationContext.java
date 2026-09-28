package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.Objects;
import java.util.Optional;

/** Immutable overrides scoped to one projection execution. */
public record ProjectionEvaluationContext(
        Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
        Optional<HouseholdLifetimeScenario> householdLifetimeScenario,
        Optional<Integer> endingYearOverride,
        HorizonPolicy horizonPolicy,
        Optional<Integer> survivorClaimingAge,
        Optional<ProjectionInflationPath> inflationPath) {

    public enum HorizonPolicy {
        CONFIGURED,
        EXTEND_TO_REQUESTED,
        EXACT_REQUESTED
    }

    public ProjectionEvaluationContext {
        inflationPath = Objects.requireNonNull(inflationPath, "Inflation path optional is required.");
        socialSecurityStrategy = Objects.requireNonNull(
                socialSecurityStrategy,
                "Social Security strategy override is required.");
        householdLifetimeScenario = Objects.requireNonNull(
                householdLifetimeScenario, "Lifetime scenario optional is required.");
        endingYearOverride = Objects.requireNonNull(endingYearOverride, "Ending year optional is required.");
        endingYearOverride.ifPresent(java.time.Year::of);
        horizonPolicy = Objects.requireNonNull(horizonPolicy, "Horizon policy is required.");
        survivorClaimingAge = Objects.requireNonNull(survivorClaimingAge, "Survivor claiming age optional is required.");
        survivorClaimingAge.ifPresent(com.daviddunn.retirementplanner.domain.income.SurvivorBenefitClaimingPolicy::validateClaimingAge);
        if (survivorClaimingAge.isPresent() && (householdLifetimeScenario.isEmpty() || socialSecurityStrategy.isPresent())) {
            throw new IllegalArgumentException("A shared survivor age requires a lifetime scenario without a full claiming strategy override.");
        }
        if ((horizonPolicy == HorizonPolicy.CONFIGURED) == endingYearOverride.isPresent()) {
            throw new IllegalArgumentException("Only a requested horizon policy requires an ending year.");
        }
    }

    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
            Optional<HouseholdLifetimeScenario> householdLifetimeScenario,
            Optional<Integer> endingYearOverride,
            HorizonPolicy horizonPolicy,
            Optional<Integer> survivorClaimingAge) {
        this(socialSecurityStrategy, householdLifetimeScenario, endingYearOverride,
                horizonPolicy, survivorClaimingAge, Optional.empty());
    }

    public ProjectionEvaluationContext withInflationPath(ProjectionInflationPath path) {
        return new ProjectionEvaluationContext(socialSecurityStrategy, householdLifetimeScenario,
                endingYearOverride, horizonPolicy, survivorClaimingAge,
                Optional.of(Objects.requireNonNull(path, "Inflation path is required.")));
    }

    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
            Optional<HouseholdLifetimeScenario> householdLifetimeScenario,
            Optional<Integer> endingYearOverride,
            HorizonPolicy horizonPolicy) {
        this(socialSecurityStrategy, householdLifetimeScenario, endingYearOverride, horizonPolicy, Optional.empty());
    }

    /** Shared analysis election for either survivor, without overriding either own-retirement election. */
    public ProjectionEvaluationContext withSurvivorClaimingAge(int age) {
        return new ProjectionEvaluationContext(socialSecurityStrategy, householdLifetimeScenario,
                endingYearOverride, horizonPolicy, Optional.of(age), inflationPath);
    }

    /** Existing three-argument callers retain extension-only behavior. */
    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
            Optional<HouseholdLifetimeScenario> householdLifetimeScenario,
            Optional<Integer> endingYearOverride) {
        this(socialSecurityStrategy, householdLifetimeScenario, endingYearOverride,
                endingYearOverride.isPresent() ? HorizonPolicy.EXTEND_TO_REQUESTED : HorizonPolicy.CONFIGURED);
    }

    public ProjectionEvaluationContext withExactEndingYear(int endingYear) {
        return new ProjectionEvaluationContext(socialSecurityStrategy,
                householdLifetimeScenario, Optional.of(endingYear), HorizonPolicy.EXACT_REQUESTED, survivorClaimingAge, inflationPath);
    }

    /** Resolve once for both financial iteration and finite Social Security preparation. */
    public int resolveEndingYear(int firstYear, int configuredLastYear) {
        int lastYear = switch (horizonPolicy) {
            case CONFIGURED -> configuredLastYear;
            case EXTEND_TO_REQUESTED -> Math.max(configuredLastYear, endingYearOverride.orElseThrow());
            case EXACT_REQUESTED -> endingYearOverride.orElseThrow();
        };
        if (lastYear < firstYear) {
            throw new IllegalArgumentException("Projection ending year cannot precede its opening year.");
        }
        return lastYear;
    }

    public ProjectionEvaluationContext(
            Optional<SocialSecurityHouseholdClaimingStrategy> socialSecurityStrategy,
            Optional<HouseholdLifetimeScenario> householdLifetimeScenario) {
        this(socialSecurityStrategy, householdLifetimeScenario, Optional.empty());
    }

    /** Extension only: the engine retains at least the configured horizon. */
    public ProjectionEvaluationContext withEndingYear(int endingYear) {
        return new ProjectionEvaluationContext(socialSecurityStrategy,
                householdLifetimeScenario, Optional.of(endingYear), HorizonPolicy.EXTEND_TO_REQUESTED, survivorClaimingAge, inflationPath);
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
