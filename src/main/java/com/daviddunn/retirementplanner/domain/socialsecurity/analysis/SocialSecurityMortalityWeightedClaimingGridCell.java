package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One retirement claiming combination valued under fixed mortality assumptions. */
public record SocialSecurityMortalityWeightedClaimingGridCell(
        int primaryClaimAge,
        int spouseClaimAge,
        LocalDate primaryClaimDate,
        LocalDate spouseClaimDate,
        SocialSecurityMortalityWeightedStrategyValue expectedValue) {

    public SocialSecurityMortalityWeightedClaimingGridCell {
        Objects.requireNonNull(primaryClaimDate, "Primary claim date is required.");
        Objects.requireNonNull(spouseClaimDate, "Spouse claim date is required.");
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
