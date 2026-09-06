package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;

import java.util.Objects;
import java.util.Optional;

/** One first-seen candidate in caller/analyzer order. */
public record IntegratedSocialSecurityStrategyComparisonEntry(
        int callerOrder,
        SocialSecurityHouseholdClaimingStrategy strategy,
        Optional<SocialSecurityMortalityWeightedStrategyValue> socialSecurityOnlyExpectedValue,
        Optional<IntegratedSocialSecurityStrategyResult> integratedResult,
        Optional<ProjectionMetrics> differencesFromCurrentPlan,
        Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure) {

    public IntegratedSocialSecurityStrategyComparisonEntry {
        if (callerOrder < 1) {
            throw new IllegalArgumentException("Caller order must be at least 1.");
        }
        Objects.requireNonNull(strategy, "Candidate strategy is required.");
        socialSecurityOnlyExpectedValue = Objects.requireNonNull(
                socialSecurityOnlyExpectedValue);
        integratedResult = Objects.requireNonNull(integratedResult);
        differencesFromCurrentPlan = Objects.requireNonNull(differencesFromCurrentPlan);
        failure = Objects.requireNonNull(failure);
        if (integratedResult.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException(
                    "A comparison entry must contain either a result or a failure.");
        }
        if (integratedResult.isPresent() != differencesFromCurrentPlan.isPresent()) {
            throw new IllegalArgumentException(
                    "Successful entries require differences; failed entries cannot have them.");
        }
    }

    public boolean successful() {
        return integratedResult.isPresent();
    }

    public static IntegratedSocialSecurityStrategyComparisonEntry success(
            int callerOrder,
            SocialSecurityHouseholdClaimingStrategy strategy,
            Optional<SocialSecurityMortalityWeightedStrategyValue> expectedValue,
            IntegratedSocialSecurityStrategyResult result,
            ProjectionMetrics differences) {
        return new IntegratedSocialSecurityStrategyComparisonEntry(
                callerOrder, strategy, expectedValue, Optional.of(result),
                Optional.of(differences), Optional.empty());
    }

    public static IntegratedSocialSecurityStrategyComparisonEntry failure(
            int callerOrder,
            SocialSecurityHouseholdClaimingStrategy strategy,
            Optional<SocialSecurityMortalityWeightedStrategyValue> expectedValue,
            IntegratedSocialSecurityStrategyEvaluationFailure failure) {
        return new IntegratedSocialSecurityStrategyComparisonEntry(
                callerOrder, strategy, expectedValue, Optional.empty(),
                Optional.empty(), Optional.of(failure));
    }
}
