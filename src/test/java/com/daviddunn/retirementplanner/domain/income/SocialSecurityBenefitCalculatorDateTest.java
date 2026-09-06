package com.daviddunn.retirementplanner.domain.income;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityBenefitCalculatorDateTest {

    @Test
    void exactClaimMonthSupportsFullRetirementAgeWithMonthComponent() {

        LocalDate birthDate = LocalDate.of(1958, 6, 15);
        LocalDate fullRetirementDate = LocalDate.of(2025, 2, 15);

        assertEquals(
                LocalDate.of(2025, 2, 15),
                SocialSecurityRetirementDateCalculator
                        .calculateFullRetirementDate(birthDate));
        assertMoney(
                new BigDecimal("3000"),
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        new BigDecimal("3000"),
                        birthDate,
                        fullRetirementDate));
    }

    @Test
    void exactClaimMonthSupportsPartialYearDelayedCredits() {

        BigDecimal result =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        new BigDecimal("3000"),
                        LocalDate.of(1963, 6, 4),
                        LocalDate.of(2030, 10, 4));

        assertEquals(
                new BigDecimal("3080.0000001000"),
                result.setScale(10, RoundingMode.HALF_UP));
    }

    @Test
    void januaryFirstBirthUsesPreviousYearFraSchedule() {

        assertEquals(
                LocalDate.of(2026, 10, 31),
                SocialSecurityRetirementDateCalculator
                        .calculateFullRetirementDate(
                                LocalDate.of(1960, 1, 1)));
    }

    private void assertMoney(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(0, expected.compareTo(actual));
    }
}
