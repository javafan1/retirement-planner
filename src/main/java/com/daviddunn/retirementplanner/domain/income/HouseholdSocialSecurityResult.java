package com.daviddunn.retirementplanner.domain.income;

import java.math.BigDecimal;
import java.util.Objects;

public record HouseholdSocialSecurityResult(
        BigDecimal primaryOwnBenefit,
        BigDecimal spouseOwnBenefit,
        BigDecimal primarySpousalExcessBenefit,
        BigDecimal spouseSpousalExcessBenefit,
        BigDecimal primarySurvivorCandidate,
        BigDecimal spouseSurvivorCandidate,
        SocialSecurityBenefitSelection primarySelection,
        SocialSecurityBenefitSelection spouseSelection,
        BigDecimal householdBenefit) {

    public HouseholdSocialSecurityResult {

        primaryOwnBenefit = requireNonNegative(
                primaryOwnBenefit,
                "Primary own benefit");
        spouseOwnBenefit = requireNonNegative(
                spouseOwnBenefit,
                "Spouse own benefit");
        primarySpousalExcessBenefit = requireNonNegative(
                primarySpousalExcessBenefit,
                "Primary spousal excess benefit");
        spouseSpousalExcessBenefit = requireNonNegative(
                spouseSpousalExcessBenefit,
                "Spouse spousal excess benefit");
        primarySurvivorCandidate = requireNonNegative(
                primarySurvivorCandidate,
                "Primary survivor candidate");
        spouseSurvivorCandidate = requireNonNegative(
                spouseSurvivorCandidate,
                "Spouse survivor candidate");
        primarySelection = Objects.requireNonNull(
                primarySelection,
                "Primary selection is required.");
        spouseSelection = Objects.requireNonNull(
                spouseSelection,
                "Spouse selection is required.");
        householdBenefit = requireNonNegative(
                householdBenefit,
                "Household benefit");

        BigDecimal selectedBenefits = selectedBenefit(
                primarySelection,
                primaryOwnBenefit,
                primarySpousalExcessBenefit,
                primarySurvivorCandidate)
                .add(selectedBenefit(
                        spouseSelection,
                        spouseOwnBenefit,
                        spouseSpousalExcessBenefit,
                        spouseSurvivorCandidate));

        if (householdBenefit.compareTo(selectedBenefits) != 0) {
            throw new IllegalArgumentException(
                    "Household benefit must equal the selected owner benefits.");
        }
    }

    /** Backward-compatible result shape from before spousal audit fields. */
    public HouseholdSocialSecurityResult(
            BigDecimal primaryOwnBenefit,
            BigDecimal spouseOwnBenefit,
            BigDecimal primarySurvivorCandidate,
            BigDecimal spouseSurvivorCandidate,
            SocialSecurityBenefitSelection primarySelection,
            SocialSecurityBenefitSelection spouseSelection,
            BigDecimal householdBenefit) {
        this(primaryOwnBenefit, spouseOwnBenefit,
                BigDecimal.ZERO, BigDecimal.ZERO,
                primarySurvivorCandidate, spouseSurvivorCandidate,
                primarySelection, spouseSelection, householdBenefit);
    }

    public static HouseholdSocialSecurityResult zero() {

        return new HouseholdSocialSecurityResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                SocialSecurityBenefitSelection.NONE,
                SocialSecurityBenefitSelection.NONE,
                BigDecimal.ZERO);
    }

    public BigDecimal primarySelectedBenefit() {
        return selectedBenefit(primarySelection, primaryOwnBenefit,
                primarySpousalExcessBenefit, primarySurvivorCandidate);
    }

    public BigDecimal spouseSelectedBenefit() {
        return selectedBenefit(spouseSelection, spouseOwnBenefit,
                spouseSpousalExcessBenefit, spouseSurvivorCandidate);
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(
                amount,
                description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }

    private static BigDecimal selectedBenefit(
            SocialSecurityBenefitSelection selection,
            BigDecimal ownBenefit,
            BigDecimal spousalExcessBenefit,
            BigDecimal survivorCandidate) {

        return switch (selection) {
            case NONE -> BigDecimal.ZERO;
            case OWN -> ownBenefit.add(spousalExcessBenefit);
            case SURVIVOR -> survivorCandidate;
        };
    }
}
