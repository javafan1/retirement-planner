package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Annual aggregation derived only from monthly strategy results. */
public record SocialSecurityAnnualResult(
        int calendarYear,
        BigDecimal primaryOwnBenefits,
        BigDecimal primarySpousalExcessBenefits,
        BigDecimal primarySurvivorBenefits,
        BigDecimal primarySelectedBenefits,
        BigDecimal spouseOwnBenefits,
        BigDecimal spouseSpousalExcessBenefits,
        BigDecimal spouseSurvivorBenefits,
        BigDecimal spouseSelectedBenefits,
        BigDecimal householdBenefits) {

    public SocialSecurityAnnualResult {

        if (calendarYear <= 0) {
            throw new IllegalArgumentException(
                    "Calendar year must be greater than zero.");
        }

        primaryOwnBenefits = requireNonNegative(
                primaryOwnBenefits,
                "Primary own benefits");
        primarySpousalExcessBenefits = requireNonNegative(
                primarySpousalExcessBenefits,
                "Primary spousal excess benefits");
        primarySurvivorBenefits = requireNonNegative(
                primarySurvivorBenefits,
                "Primary survivor benefits");
        primarySelectedBenefits = requireNonNegative(
                primarySelectedBenefits,
                "Primary selected benefits");
        spouseOwnBenefits = requireNonNegative(
                spouseOwnBenefits,
                "Spouse own benefits");
        spouseSpousalExcessBenefits = requireNonNegative(
                spouseSpousalExcessBenefits,
                "Spouse spousal excess benefits");
        spouseSurvivorBenefits = requireNonNegative(
                spouseSurvivorBenefits,
                "Spouse survivor benefits");
        spouseSelectedBenefits = requireNonNegative(
                spouseSelectedBenefits,
                "Spouse selected benefits");
        householdBenefits = requireNonNegative(
                householdBenefits,
                "Household benefits");

        if (householdBenefits.compareTo(
                primarySelectedBenefits.add(
                        spouseSelectedBenefits)) != 0) {
            throw new IllegalArgumentException(
                    "Annual household benefit must reconcile to selected benefits.");
        }

        if (primarySelectedBenefits.compareTo(
                primaryOwnBenefits.add(
                        primarySpousalExcessBenefits).add(
                        primarySurvivorBenefits)) != 0
                || spouseSelectedBenefits.compareTo(
                spouseOwnBenefits.add(
                        spouseSpousalExcessBenefits).add(
                        spouseSurvivorBenefits)) != 0) {
            throw new IllegalArgumentException(
                    "Annual owner benefits must reconcile to their components.");
        }
    }

    public static SocialSecurityAnnualResult fromMonthlyResults(
            int calendarYear,
            List<SocialSecurityMonthlyResult> monthlyResults) {

        BigDecimal primaryOwn = BigDecimal.ZERO;
        BigDecimal primarySpousalExcess = BigDecimal.ZERO;
        BigDecimal primarySurvivor = BigDecimal.ZERO;
        BigDecimal spouseOwn = BigDecimal.ZERO;
        BigDecimal spouseSpousalExcess = BigDecimal.ZERO;
        BigDecimal spouseSurvivor = BigDecimal.ZERO;
        BigDecimal primarySelected = BigDecimal.ZERO;
        BigDecimal spouseSelected = BigDecimal.ZERO;
        BigDecimal household = BigDecimal.ZERO;

        for (SocialSecurityMonthlyResult result : monthlyResults) {
            if (result.month().getYear() != calendarYear) {
                throw new IllegalArgumentException(
                        "Monthly result does not belong to calendar year "
                                + calendarYear + ".");
            }

            primaryOwn = primaryOwn.add(result.primaryOwnBenefit());
            primarySpousalExcess = primarySpousalExcess.add(
                    result.primarySpousalExcessBenefit());
            primarySurvivor = primarySurvivor.add(
                    result.primarySurvivorBenefit());
            spouseOwn = spouseOwn.add(result.spouseOwnBenefit());
            spouseSpousalExcess = spouseSpousalExcess.add(
                    result.spouseSpousalExcessBenefit());
            spouseSurvivor = spouseSurvivor.add(
                    result.spouseSurvivorBenefit());
            primarySelected = primarySelected.add(
                    result.primarySelectedBenefit());
            spouseSelected = spouseSelected.add(
                    result.spouseSelectedBenefit());
            household = household.add(result.householdBenefit());
        }

        if (household.compareTo(
                primarySelected.add(spouseSelected)) != 0) {
            throw new IllegalArgumentException(
                    "Annual household benefit must reconcile to selected benefits.");
        }

        return new SocialSecurityAnnualResult(
                calendarYear,
                primaryOwn,
                primarySpousalExcess,
                primarySurvivor,
                primarySelected,
                spouseOwn,
                spouseSpousalExcess,
                spouseSurvivor,
                spouseSelected,
                household);
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
}
