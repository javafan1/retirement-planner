package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Auditable result for one entitlement month. Own, normal-spousal, and
 * survivor amounts are retained as separate payment components.
 */
public record SocialSecurityMonthlyResult(
        YearMonth month,
        SocialSecurityHouseholdLifeState lifeState,
        boolean primaryAlive,
        boolean spouseAlive,
        BigDecimal primaryOwnBenefit,
        BigDecimal primarySpousalExcessBenefit,
        BigDecimal primarySurvivorBenefit,
        Set<SocialSecurityBenefitType> primaryActiveBenefitTypes,
        BigDecimal primarySelectedBenefit,
        BigDecimal spouseOwnBenefit,
        BigDecimal spouseSpousalExcessBenefit,
        BigDecimal spouseSurvivorBenefit,
        Set<SocialSecurityBenefitType> spouseActiveBenefitTypes,
        BigDecimal spouseSelectedBenefit,
        BigDecimal householdBenefit) {

    public SocialSecurityMonthlyResult {

        Objects.requireNonNull(month, "Month is required.");
        Objects.requireNonNull(lifeState, "Life state is required.");
        primaryActiveBenefitTypes = Set.copyOf(
                Objects.requireNonNull(
                        primaryActiveBenefitTypes,
                        "Primary active benefit types are required."));
        spouseActiveBenefitTypes = Set.copyOf(
                Objects.requireNonNull(
                        spouseActiveBenefitTypes,
                        "Spouse active benefit types are required."));

        primaryOwnBenefit = requireNonNegative(
                primaryOwnBenefit,
                "Primary own benefit");
        primarySpousalExcessBenefit = requireNonNegative(
                primarySpousalExcessBenefit,
                "Primary spousal excess benefit");
        primarySurvivorBenefit = requireNonNegative(
                primarySurvivorBenefit,
                "Primary survivor benefit");
        primarySelectedBenefit = requireNonNegative(
                primarySelectedBenefit,
                "Primary selected benefit");
        spouseOwnBenefit = requireNonNegative(
                spouseOwnBenefit,
                "Spouse own benefit");
        spouseSpousalExcessBenefit = requireNonNegative(
                spouseSpousalExcessBenefit,
                "Spouse spousal excess benefit");
        spouseSurvivorBenefit = requireNonNegative(
                spouseSurvivorBenefit,
                "Spouse survivor benefit");
        spouseSelectedBenefit = requireNonNegative(
                spouseSelectedBenefit,
                "Spouse selected benefit");
        householdBenefit = requireNonNegative(
                householdBenefit,
                "Household benefit");

        SocialSecurityHouseholdLifeState expectedLifeState =
                expectedLifeState(primaryAlive, spouseAlive);

        if (lifeState != expectedLifeState) {
            throw new IllegalArgumentException(
                    "Life state must match the owner alive flags.");
        }

        validateOwnerResult(
                primaryAlive,
                primaryOwnBenefit,
                primarySpousalExcessBenefit,
                primarySurvivorBenefit,
                primaryActiveBenefitTypes,
                primarySelectedBenefit,
                "Primary");
        validateOwnerResult(
                spouseAlive,
                spouseOwnBenefit,
                spouseSpousalExcessBenefit,
                spouseSurvivorBenefit,
                spouseActiveBenefitTypes,
                spouseSelectedBenefit,
                "Spouse");

        if (householdBenefit.compareTo(
                primarySelectedBenefit.add(
                        spouseSelectedBenefit)) != 0) {
            throw new IllegalArgumentException(
                    "Household benefit must equal selected owner benefits.");
        }
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(amount, description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }

    private static void validateOwnerResult(
            boolean alive,
            BigDecimal ownBenefit,
            BigDecimal spousalExcessBenefit,
            BigDecimal survivorBenefit,
            Set<SocialSecurityBenefitType> activeBenefitTypes,
            BigDecimal selectedBenefit,
            String ownerDescription) {

        if (!alive && (ownBenefit.signum() != 0
                || spousalExcessBenefit.signum() != 0
                || survivorBenefit.signum() != 0
                || selectedBenefit.signum() != 0
                || !activeBenefitTypes.equals(
                        Set.of(SocialSecurityBenefitType.NONE)))) {
            throw new IllegalArgumentException(
                    ownerDescription + " benefit must be zero after death.");
        }

        if (selectedBenefit.compareTo(
                ownBenefit.add(spousalExcessBenefit).add(
                        survivorBenefit)) != 0) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " selected benefit must equal its active components.");
        }

        Set<SocialSecurityBenefitType> expectedTypes = EnumSet.noneOf(
                SocialSecurityBenefitType.class);

        if (ownBenefit.signum() > 0) {
            expectedTypes.add(SocialSecurityBenefitType.OWN_RETIREMENT);
        }
        if (spousalExcessBenefit.signum() > 0) {
            expectedTypes.add(SocialSecurityBenefitType.SPOUSAL);
        }
        if (survivorBenefit.signum() > 0) {
            expectedTypes.add(SocialSecurityBenefitType.SURVIVOR);
        }
        if (expectedTypes.isEmpty()) {
            expectedTypes.add(SocialSecurityBenefitType.NONE);
        }

        if (!activeBenefitTypes.equals(expectedTypes)) {
            throw new IllegalArgumentException(
                    ownerDescription
                            + " active benefit types must match the calculated components.");
        }
    }

    private static SocialSecurityHouseholdLifeState expectedLifeState(
            boolean primaryAlive,
            boolean spouseAlive) {

        if (primaryAlive && spouseAlive) {
            return SocialSecurityHouseholdLifeState.BOTH_ALIVE;
        }
        if (primaryAlive) {
            return SocialSecurityHouseholdLifeState.PRIMARY_ONLY;
        }
        if (spouseAlive) {
            return SocialSecurityHouseholdLifeState.SPOUSE_ONLY;
        }
        return SocialSecurityHouseholdLifeState.NEITHER_ALIVE;
    }
}
