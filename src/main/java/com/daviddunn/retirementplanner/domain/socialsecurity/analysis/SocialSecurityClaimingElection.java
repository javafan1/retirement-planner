package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable own-retirement election for one member of a household.
 * The claim date is authoritative; claiming age is derived when needed.
 */
public record SocialSecurityClaimingElection(
        AccountOwnership owner,
        LocalDate birthDate,
        BigDecimal fullRetirementMonthlyBenefit,
        int benefitValuationYear,
        LocalDate retirementClaimDate) {

    public SocialSecurityClaimingElection {

        Objects.requireNonNull(owner, "Owner is required.");
        Objects.requireNonNull(birthDate, "Birth date is required.");
        Objects.requireNonNull(
                fullRetirementMonthlyBenefit,
                "Full retirement monthly benefit is required.");
        Objects.requireNonNull(
                retirementClaimDate,
                "Retirement claim date is required.");

        if (owner == AccountOwnership.JOINT) {
            throw new IllegalArgumentException(
                    "Social Security election owner must be PRIMARY or SPOUSE.");
        }

        if (fullRetirementMonthlyBenefit.signum() < 0) {
            throw new IllegalArgumentException(
                    "Full retirement monthly benefit cannot be negative.");
        }

        if (benefitValuationYear <= 0) {
            throw new IllegalArgumentException(
                    "Benefit valuation year must be greater than zero.");
        }

        LocalDate earliestClaimDate =
                SocialSecurityRetirementDateCalculator
                        .calculateEarliestRetirementClaimDate(
                                birthDate);
        LocalDate latestClaimDate =
                SocialSecurityRetirementDateCalculator
                        .calculateLatestRetirementClaimDate(
                                birthDate);

        if (retirementClaimDate.isBefore(earliestClaimDate)) {
            throw new IllegalArgumentException(
                    "Retirement claim date cannot be before age 62.");
        }

        if (retirementClaimDate.isAfter(latestClaimDate)) {
            throw new IllegalArgumentException(
                    "Retirement claim date cannot be after age 70.");
        }
    }
}
