package com.daviddunn.retirementplanner.domain.income;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

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

    /**
     * Calculates an own-retirement benefit using an exact candidate
     * claim date. The claim month is compared with the person's precise
     * full-retirement month, including the January 1 birth convention.
     * Existing integer-age callers intentionally retain their established
     * calculation path above.
     */
    public static BigDecimal calculateMonthlyBenefit(
            BigDecimal primaryInsuranceAmount,
            LocalDate birthDate,
            LocalDate claimDate) {

        return primaryInsuranceAmount.multiply(
                calculateRetirementBenefitFactor(
                        birthDate,
                        claimDate));
    }

    public static BigDecimal calculateRetirementBenefitFactor(
            LocalDate birthDate,
            LocalDate claimDate) {

        LocalDate fullRetirementDate =
                SocialSecurityRetirementDateCalculator
                        .calculateFullRetirementDate(birthDate);

        long monthDifference = ChronoUnit.MONTHS.between(
                YearMonth.from(fullRetirementDate),
                YearMonth.from(claimDate));

        return calculateBenefitFactor(
                Math.toIntExact(monthDifference));
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
