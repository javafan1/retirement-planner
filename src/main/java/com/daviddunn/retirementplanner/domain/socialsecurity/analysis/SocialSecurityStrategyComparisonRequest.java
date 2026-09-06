package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** Immutable comparison of two claiming choices for one household scenario. */
public record SocialSecurityStrategyComparisonRequest(
        SocialSecurityStrategyRequest strategyA,
        SocialSecurityStrategyRequest strategyB,
        LocalDate presentValueBaseDate,
        BigDecimal realDiscountRate) {

    public SocialSecurityStrategyComparisonRequest {

        Objects.requireNonNull(strategyA, "Strategy A is required.");
        Objects.requireNonNull(strategyB, "Strategy B is required.");
        Objects.requireNonNull(
                presentValueBaseDate,
                "Present-value base date is required.");
        Objects.requireNonNull(
                realDiscountRate,
                "Real discount rate is required.");

        if (realDiscountRate.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException(
                    "Real discount rate must be greater than -100%.");
        }

        validateSame(
                strategyA.primaryElection().birthDate(),
                strategyB.primaryElection().birthDate(),
                "Strategies must use the same primary date of birth.");
        validateSame(
                strategyA.spouseElection().birthDate(),
                strategyB.spouseElection().birthDate(),
                "Strategies must use the same spouse date of birth.");
        validateSameDecimal(
                strategyA.primaryElection().fullRetirementMonthlyBenefit(),
                strategyB.primaryElection().fullRetirementMonthlyBenefit(),
                "Strategies must use the same primary FRA benefit.");
        validateSameDecimal(
                strategyA.spouseElection().fullRetirementMonthlyBenefit(),
                strategyB.spouseElection().fullRetirementMonthlyBenefit(),
                "Strategies must use the same spouse FRA benefit.");
        validateSame(
                strategyA.primaryElection().benefitValuationYear(),
                strategyB.primaryElection().benefitValuationYear(),
                "Strategies must use the same primary benefit valuation year.");
        validateSame(
                strategyA.spouseElection().benefitValuationYear(),
                strategyB.spouseElection().benefitValuationYear(),
                "Strategies must use the same spouse benefit valuation year.");
        validateSame(
                strategyA.primaryDeathDate(),
                strategyB.primaryDeathDate(),
                "Strategies must use the same primary death date.");
        validateSame(
                strategyA.spouseDeathDate(),
                strategyB.spouseDeathDate(),
                "Strategies must use the same spouse death date.");
        validateSameDecimal(
                strategyA.socialSecurityColaRate(),
                strategyB.socialSecurityColaRate(),
                "Strategies must use the same Social Security COLA rate.");
        validateSame(
                YearMonth.from(strategyA.analysisDate()),
                YearMonth.from(strategyB.analysisDate()),
                "Strategies must use the same analysis date.");
        validateSame(
                YearMonth.from(strategyA.resolvedAnalysisEndDate()),
                YearMonth.from(strategyB.resolvedAnalysisEndDate()),
                "Comparison horizons must match.");

        if (YearMonth.from(presentValueBaseDate).isAfter(
                YearMonth.from(strategyA.resolvedAnalysisEndDate()))) {
            throw new IllegalArgumentException(
                    "Present-value base date cannot be after the comparison horizon.");
        }
    }

    private static void validateSame(
            Object first,
            Object second,
            String message) {

        if (!Objects.equals(first, second)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateSameDecimal(
            BigDecimal first,
            BigDecimal second,
            String message) {

        if (first.compareTo(second) != 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
