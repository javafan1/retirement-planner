package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;

import java.util.Objects;

/** Actionable failure for one candidate without retaining an exception object. */
public record IntegratedSocialSecurityStrategyEvaluationFailure(
        SocialSecurityHouseholdClaimingStrategy strategy,
        String message,
        Category category) {

    public enum Category {
        VALIDATION,
        EVALUATION
    }

    public IntegratedSocialSecurityStrategyEvaluationFailure {
        Objects.requireNonNull(strategy, "Failed strategy is required.");
        Objects.requireNonNull(message, "Failure message is required.");
        Objects.requireNonNull(category, "Failure category is required.");
        if (message.isBlank()) {
            throw new IllegalArgumentException("Failure message cannot be blank.");
        }
    }
}
