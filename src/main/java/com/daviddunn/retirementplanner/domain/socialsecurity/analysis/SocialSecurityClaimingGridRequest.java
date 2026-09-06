package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable whole-year retirement claiming search under fixed assumptions. */
public record SocialSecurityClaimingGridRequest(
        SocialSecurityStrategyRequest baseStrategy,
        List<Integer> primaryClaimAges,
        List<Integer> spouseClaimAges,
        LocalDate presentValueBaseDate,
        BigDecimal realDiscountRate) {

    public SocialSecurityClaimingGridRequest {
        Objects.requireNonNull(baseStrategy, "Base strategy is required.");
        primaryClaimAges = copyAndValidateAges(
                primaryClaimAges,
                "Primary claim ages");
        spouseClaimAges = copyAndValidateAges(
                spouseClaimAges,
                "Spouse claim ages");
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
        if (YearMonth.from(presentValueBaseDate).isAfter(
                YearMonth.from(baseStrategy.resolvedAnalysisEndDate()))) {
            throw new IllegalArgumentException(
                    "Present-value base date cannot be after the strategy horizon.");
        }
    }

    private static List<Integer> copyAndValidateAges(
            List<Integer> ages,
            String description) {
        Objects.requireNonNull(ages, description + " are required.");
        if (ages.isEmpty()) {
            throw new IllegalArgumentException(description + " cannot be empty.");
        }
        Set<Integer> unique = new HashSet<>();
        for (Integer age : ages) {
            Objects.requireNonNull(age, description + " cannot contain null.");
            if (age < 62 || age > 70) {
                throw new IllegalArgumentException(
                        description + " value " + age
                                + " must be between 62 and 70.");
            }
            if (!unique.add(age)) {
                throw new IllegalArgumentException(
                        description + " cannot contain duplicate age " + age + ".");
            }
        }
        return List.copyOf(ages);
    }
}
