package com.daviddunn.retirementplanner.domain.income;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

public final class SocialSecuritySurvivorBenefitCalculator {

    private SocialSecuritySurvivorBenefitCalculator() {
    }

    public static BigDecimal calculateMonthlyBenefit(
            BigDecimal deceasedMonthlyBenefit,
            LocalDate survivorBirthDate,
            int survivorClaimingAge) {

        Objects.requireNonNull(
                deceasedMonthlyBenefit,
                "Deceased monthly benefit is required.");

        Objects.requireNonNull(
                survivorBirthDate,
                "Survivor birth date is required.");

        if (deceasedMonthlyBenefit.signum() < 0) {
            throw new IllegalArgumentException(
                    "Deceased monthly benefit cannot be negative.");
        }

        if (survivorClaimingAge < 62
                || survivorClaimingAge > 70) {

            throw new IllegalArgumentException(
                    "Survivor claiming age must be between "
                            + "62 and 70.");
        }

        FullRetirementAge survivorFra =
                SurvivorFullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        BigDecimal factor =
                calculateSurvivorBenefitFactor(
                        survivorFra,
                        survivorClaimingAge);

        return deceasedMonthlyBenefit
                .multiply(factor)
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }

    private static BigDecimal calculateSurvivorBenefitFactor(
            FullRetirementAge survivorFra,
            int survivorClaimingAge) {

        int fraMonths =
                toTotalMonths(survivorFra);

        int claimingMonths =
                survivorClaimingAge * 12;

        int age60Months =
                60 * 12;

        /*
         * At or after survivor FRA,
         * the survivor receives 100%.
         */
        if (claimingMonths >= fraMonths) {
            return BigDecimal.ONE;
        }

        int possibleEarlyMonths =
                fraMonths - age60Months;

        int monthsEarly =
                fraMonths - claimingMonths;

        BigDecimal maximumReduction =
                new BigDecimal("0.285");

        BigDecimal monthlyReduction =
                maximumReduction.divide(
                        BigDecimal.valueOf(
                                possibleEarlyMonths),
                        12,
                        RoundingMode.HALF_UP);

        BigDecimal reduction =
                monthlyReduction.multiply(
                        BigDecimal.valueOf(
                                monthsEarly));

        return BigDecimal.ONE
                .subtract(reduction);
    }

    private static int toTotalMonths(
            FullRetirementAge fra) {

        return fra.years() * 12
                + fra.months();
    }
}