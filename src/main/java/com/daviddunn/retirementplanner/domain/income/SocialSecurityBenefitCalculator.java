package com.daviddunn.retirementplanner.domain.income;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class SocialSecurityBenefitCalculator {


public static BigDecimal calculateMonthlyBenefit(
        BigDecimal primaryInsuranceAmount,
        LocalDate birthDate,
        int claimingAge) {

    FullRetirementAge fra =
            FullRetirementAgeCalculator.determine(birthDate);

    int fraMonths =
            toTotalMonths(fra);

    int claimingMonths =
            claimingAge * 12;

    int monthDifference =
            claimingMonths - fraMonths;

    BigDecimal factor =
            calculateBenefitFactor(monthDifference);

    return primaryInsuranceAmount.multiply(factor);
}

    private static int toTotalMonths(
            FullRetirementAge fra) {

        return fra.years() * 12 + fra.months();
    }

    private static BigDecimal calculateBenefitFactor(
            int monthDifference) {

        if (monthDifference == 0) {
            return BigDecimal.ONE;
        }

        if (monthDifference < 0) {
            return calculateEarlyRetirementFactor(
                    -monthDifference);
        }

        return calculateDelayedRetirementFactor(
                monthDifference);
    }

    private static BigDecimal calculateEarlyRetirementFactor(
            int monthsEarly) {

        int first36 =
                Math.min(monthsEarly, 36);

        int remaining =
                Math.max(monthsEarly - 36, 0);

        BigDecimal reduction =
                BigDecimal.valueOf(first36)
                        .multiply(BigDecimal.valueOf(5))
                        .divide(BigDecimal.valueOf(900), 10, RoundingMode.HALF_UP)
                        .add(
                                BigDecimal.valueOf(remaining)
                                        .multiply(BigDecimal.valueOf(5))
                                        .divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP));

        return BigDecimal.ONE.subtract(reduction);
    }

    private static BigDecimal calculateDelayedRetirementFactor(
            int monthsDelayed) {

        BigDecimal credit =
                BigDecimal.valueOf(monthsDelayed)
                        .multiply(BigDecimal.valueOf(2))
                        .divide(BigDecimal.valueOf(300), 10, RoundingMode.HALF_UP);

        return BigDecimal.ONE.add(credit);
    }
}