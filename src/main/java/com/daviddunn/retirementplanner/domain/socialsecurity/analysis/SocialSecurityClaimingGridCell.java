package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One whole-year retirement claiming combination and its full valuation. */
public record SocialSecurityClaimingGridCell(
        int primaryClaimAge,
        int spouseClaimAge,
        LocalDate primaryClaimDate,
        LocalDate spouseClaimDate,
        SocialSecurityStrategyValuation valuation) {

    public SocialSecurityClaimingGridCell {
        validateAge(primaryClaimAge, "Primary claim age");
        validateAge(spouseClaimAge, "Spouse claim age");
        Objects.requireNonNull(primaryClaimDate, "Primary claim date is required.");
        Objects.requireNonNull(spouseClaimDate, "Spouse claim date is required.");
        Objects.requireNonNull(valuation, "Strategy valuation is required.");
    }

    public BigDecimal nominalLifetimeBenefits() {
        return valuation.nominalLifetimeBenefits();
    }

    public BigDecimal realLifetimeBenefits() {
        return valuation.realLifetimeBenefits();
    }

    public BigDecimal presentValue() {
        return valuation.presentValue();
    }

    public BigDecimal primaryLifetimeBenefits() {
        SocialSecurityLifetimeResult result = valuation.strategyResult();
        return result.primaryTotalOwnRetirementBenefits()
                .add(result.primaryTotalSpousalExcessBenefits())
                .add(result.primaryTotalSurvivorBenefits());
    }

    public BigDecimal spouseLifetimeBenefits() {
        SocialSecurityLifetimeResult result = valuation.strategyResult();
        return result.spouseTotalOwnRetirementBenefits()
                .add(result.spouseTotalSpousalExcessBenefits())
                .add(result.spouseTotalSurvivorBenefits());
    }

    private static void validateAge(int age, String description) {
        if (age < 62 || age > 70) {
            throw new IllegalArgumentException(
                    description + " must be between 62 and 70.");
        }
    }
}
