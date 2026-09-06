package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered axes and base claiming strategies for a longevity matrix. */
public record SocialSecurityDeathAgeMatrixRequest(
        SocialSecurityStrategyRequest strategyA,
        SocialSecurityStrategyRequest strategyB,
        List<Integer> primaryDeathAges,
        List<Integer> spouseDeathAges,
        LocalDate presentValueBaseDate,
        BigDecimal realDiscountRate) {

    public SocialSecurityDeathAgeMatrixRequest {

        Objects.requireNonNull(strategyA, "Strategy A is required.");
        Objects.requireNonNull(strategyB, "Strategy B is required.");
        primaryDeathAges = copyAndValidateAges(
                primaryDeathAges,
                "Primary death ages");
        spouseDeathAges = copyAndValidateAges(
                spouseDeathAges,
                "Spouse death ages");
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
        validateSameDecimal(
                strategyA.socialSecurityColaRate(),
                strategyB.socialSecurityColaRate(),
                "Strategies must use the same Social Security COLA rate.");
        validateSame(
                YearMonth.from(strategyA.analysisDate()),
                YearMonth.from(strategyB.analysisDate()),
                "Strategies must use the same analysis month.");
    }

    private static List<Integer> copyAndValidateAges(
            List<Integer> ages,
            String description) {

        Objects.requireNonNull(ages, description + " are required.");
        if (ages.isEmpty()) {
            throw new IllegalArgumentException(
                    description + " cannot be empty.");
        }

        Set<Integer> unique = new HashSet<>();
        for (Integer age : ages) {
            Objects.requireNonNull(age, description + " cannot contain null.");
            SocialSecurityDeathDateCalculator.validateDeathAge(
                    age,
                    description + " value " + age);
            if (!unique.add(age)) {
                throw new IllegalArgumentException(
                        description + " cannot contain duplicate age " + age + ".");
            }
        }
        return List.copyOf(ages);
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
