package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Ordinary spouse auxiliary-benefit rules for the modern monthly analyzer.
 * The worker's projected FRA benefit is the PIA-equivalent basis. The
 * claimant's own projected FRA benefit is subtracted before the separate
 * spouse reduction is applied to the excess component.
 */
public final class SocialSecuritySpousalBenefitCalculator {

    private static final BigDecimal ONE_HALF =
            new BigDecimal("0.5");

    private SocialSecuritySpousalBenefitCalculator() {
    }

    public static BigDecimal calculateUnreducedSpousalBasis(
            BigDecimal workerFraMonthlyBenefit) {

        requireNonNegative(
                workerFraMonthlyBenefit,
                "Worker FRA monthly benefit");

        return workerFraMonthlyBenefit.multiply(ONE_HALF);
    }

    public static BigDecimal calculateUnreducedExcessBasis(
            BigDecimal claimantFraMonthlyBenefit,
            BigDecimal workerFraMonthlyBenefit) {

        requireNonNegative(
                claimantFraMonthlyBenefit,
                "Claimant FRA monthly benefit");

        return calculateUnreducedSpousalBasis(
                        workerFraMonthlyBenefit)
                .subtract(claimantFraMonthlyBenefit)
                .max(BigDecimal.ZERO);
    }

    public static BigDecimal calculateMonthlyExcessBenefit(
            BigDecimal claimantFraMonthlyBenefit,
            BigDecimal workerFraMonthlyBenefit,
            LocalDate claimantBirthDate,
            YearMonth spouseEntitlementMonth) {

        Objects.requireNonNull(
                claimantBirthDate,
                "Claimant birth date is required.");
        Objects.requireNonNull(
                spouseEntitlementMonth,
                "Spouse entitlement month is required.");

        BigDecimal excessBasis = calculateUnreducedExcessBasis(
                claimantFraMonthlyBenefit,
                workerFraMonthlyBenefit);

        if (excessBasis.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return excessBasis.multiply(
                        calculateReductionFactor(
                                claimantBirthDate,
                                spouseEntitlementMonth))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateReductionFactor(
            LocalDate claimantBirthDate,
            YearMonth spouseEntitlementMonth) {

        Objects.requireNonNull(
                claimantBirthDate,
                "Claimant birth date is required.");
        Objects.requireNonNull(
                spouseEntitlementMonth,
                "Spouse entitlement month is required.");

        YearMonth fullRetirementMonth = YearMonth.from(
                SocialSecurityRetirementDateCalculator
                        .calculateFullRetirementDate(
                                claimantBirthDate));

        if (!spouseEntitlementMonth.isBefore(
                fullRetirementMonth)) {
            return BigDecimal.ONE;
        }

        int monthsEarly = Math.toIntExact(
                ChronoUnit.MONTHS.between(
                        spouseEntitlementMonth,
                        fullRetirementMonth));
        int first36Months = Math.min(monthsEarly, 36);
        int additionalMonths = Math.max(monthsEarly - 36, 0);

        BigDecimal firstReduction = BigDecimal
                .valueOf(first36Months)
                .divide(
                        BigDecimal.valueOf(144),
                        12,
                        RoundingMode.HALF_UP);
        BigDecimal additionalReduction = BigDecimal
                .valueOf(additionalMonths)
                .divide(
                        BigDecimal.valueOf(240),
                        12,
                        RoundingMode.HALF_UP);

        return BigDecimal.ONE
                .subtract(firstReduction)
                .subtract(additionalReduction);
    }

    private static void requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(amount, description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }
    }
}
