package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** Compact expected values for one claiming strategy across joint mortality. */
public record SocialSecurityMortalityWeightedStrategyValue(
        BigDecimal expectedNominalBenefits,
        BigDecimal expectedRealBenefits,
        BigDecimal expectedPresentValue,
        BigDecimal expectedPrimarySelectedBenefits,
        BigDecimal expectedSpouseSelectedBenefits,
        int strategyEvaluationCount) {

    public SocialSecurityMortalityWeightedStrategyValue {
        Objects.requireNonNull(expectedNominalBenefits, "Expected nominal benefits are required.");
        Objects.requireNonNull(expectedRealBenefits, "Expected real benefits are required.");
        Objects.requireNonNull(expectedPresentValue, "Expected present value is required.");
        Objects.requireNonNull(expectedPrimarySelectedBenefits, "Expected primary benefits are required.");
        Objects.requireNonNull(expectedSpouseSelectedBenefits, "Expected spouse benefits are required.");
        if (strategyEvaluationCount < 1) {
            throw new IllegalArgumentException("Strategy evaluation count must be positive.");
        }
        if (expectedPrimarySelectedBenefits.add(expectedSpouseSelectedBenefits)
                .compareTo(expectedNominalBenefits) != 0) {
            throw new IllegalArgumentException(
                    "Expected primary and spouse benefits must reconcile to household nominal benefits.");
        }
    }
}
