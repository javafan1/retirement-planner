package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** One aligned month of a two-strategy gross-benefit comparison. */
public record SocialSecurityMonthlyComparison(
        YearMonth month,
        SocialSecurityMonthlyResult strategyAMonth,
        SocialSecurityMonthlyResult strategyBMonth,
        BigDecimal strategyANominalBenefit,
        BigDecimal strategyBNominalBenefit,
        BigDecimal nominalDifference,
        BigDecimal strategyARealBenefit,
        BigDecimal strategyBRealBenefit,
        BigDecimal realDifference,
        BigDecimal strategyAPresentValueContribution,
        BigDecimal strategyBPresentValueContribution,
        BigDecimal presentValueDifference,
        BigDecimal cumulativeNominalA,
        BigDecimal cumulativeNominalB,
        BigDecimal cumulativeNominalDifference,
        BigDecimal cumulativeRealA,
        BigDecimal cumulativeRealB,
        BigDecimal cumulativeRealDifference,
        BigDecimal cumulativePresentValueA,
        BigDecimal cumulativePresentValueB,
        BigDecimal cumulativePresentValueDifference) {

    public SocialSecurityMonthlyComparison {
        Objects.requireNonNull(month, "Month is required.");
        Objects.requireNonNull(strategyAMonth, "Strategy A month is required.");
        Objects.requireNonNull(strategyBMonth, "Strategy B month is required.");
        if (!month.equals(strategyAMonth.month())
                || !month.equals(strategyBMonth.month())) {
            throw new IllegalArgumentException(
                    "Comparison month must match both strategy months.");
        }
        requireValues(
                strategyANominalBenefit,
                strategyBNominalBenefit,
                nominalDifference,
                strategyARealBenefit,
                strategyBRealBenefit,
                realDifference,
                strategyAPresentValueContribution,
                strategyBPresentValueContribution,
                presentValueDifference,
                cumulativeNominalA,
                cumulativeNominalB,
                cumulativeNominalDifference,
                cumulativeRealA,
                cumulativeRealB,
                cumulativeRealDifference,
                cumulativePresentValueA,
                cumulativePresentValueB,
                cumulativePresentValueDifference);
        validateDifference(
                strategyANominalBenefit,
                strategyBNominalBenefit,
                nominalDifference,
                "Monthly nominal difference");
        validateDifference(
                strategyARealBenefit,
                strategyBRealBenefit,
                realDifference,
                "Monthly real difference");
        validateDifference(
                strategyAPresentValueContribution,
                strategyBPresentValueContribution,
                presentValueDifference,
                "Monthly present-value difference");
        validateDifference(
                cumulativeNominalA,
                cumulativeNominalB,
                cumulativeNominalDifference,
                "Cumulative nominal difference");
        validateDifference(
                cumulativeRealA,
                cumulativeRealB,
                cumulativeRealDifference,
                "Cumulative real difference");
        validateDifference(
                cumulativePresentValueA,
                cumulativePresentValueB,
                cumulativePresentValueDifference,
                "Cumulative present-value difference");
    }

    private static void requireValues(BigDecimal... values) {
        for (BigDecimal value : values) {
            Objects.requireNonNull(value, "Comparison amount is required.");
        }
    }

    private static void validateDifference(
            BigDecimal strategyA,
            BigDecimal strategyB,
            BigDecimal difference,
            String description) {
        if (difference.compareTo(strategyA.subtract(strategyB)) != 0) {
            throw new IllegalArgumentException(
                    description + " must equal Strategy A minus Strategy B.");
        }
    }
}
