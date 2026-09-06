package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** Compact expected values for one complete retirement-and-survivor strategy. */
public record SocialSecuritySurvivorClaimingOptimizationCell(
        SocialSecurityHouseholdClaimingStrategy strategy,
        SocialSecurityMortalityWeightedStrategyValue expectedValue) {

    public SocialSecuritySurvivorClaimingOptimizationCell {
        Objects.requireNonNull(strategy, "Household claiming strategy is required.");
        Objects.requireNonNull(expectedValue, "Expected strategy value is required.");
    }

    public BigDecimal expectedNominalBenefits() {
        return expectedValue.expectedNominalBenefits();
    }

    public BigDecimal expectedRealBenefits() {
        return expectedValue.expectedRealBenefits();
    }

    public BigDecimal expectedPresentValue() {
        return expectedValue.expectedPresentValue();
    }

    public BigDecimal expectedPrimarySelectedBenefits() {
        return expectedValue.expectedPrimarySelectedBenefits();
    }

    public BigDecimal expectedSpouseSelectedBenefits() {
        return expectedValue.expectedSpouseSelectedBenefits();
    }
}
