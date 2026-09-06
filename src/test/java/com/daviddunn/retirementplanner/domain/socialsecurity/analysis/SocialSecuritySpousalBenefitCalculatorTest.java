package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecuritySpousalBenefitCalculatorTest {

    private static final LocalDate FRA_67_BIRTH_DATE =
            LocalDate.of(1960, 6, 15);

    @Test
    void unreducedBasisIsHalfWorkerFraBenefit() {

        assertMoney(
                new BigDecimal("1500.0"),
                SocialSecuritySpousalBenefitCalculator
                        .calculateUnreducedSpousalBasis(
                                new BigDecimal("3000")));
    }

    @Test
    void age62ForFra67UsesThirtyFivePercentReduction() {

        assertFactor(
                new BigDecimal("0.650000000000"),
                YearMonth.of(2022, 6));
    }

    @Test
    void thirtySixMonthsEarlyUsesTwentyFivePercentReduction() {

        assertFactor(
                new BigDecimal("0.750000000000"),
                YearMonth.of(2024, 6));
    }

    @Test
    void fortyEightMonthsEarlyUsesSeparateAdditionalMonthRate() {

        assertFactor(
                new BigDecimal("0.700000000000"),
                YearMonth.of(2023, 6));
    }

    @Test
    void fraAndPostFraSpousalFactorsAreCappedAtOne() {

        assertFactor(BigDecimal.ONE, YearMonth.of(2027, 6));
        assertFactor(BigDecimal.ONE, YearMonth.of(2030, 6));
    }

    @Test
    void fraWithMonthComponentUsesExactFullRetirementMonth() {

        LocalDate birthDate = LocalDate.of(1958, 6, 15);

        assertEquals(
                new BigDecimal("0.750000000000"),
                SocialSecuritySpousalBenefitCalculator
                        .calculateReductionFactor(
                                birthDate,
                                YearMonth.of(2022, 2)));
        assertEquals(
                BigDecimal.ONE,
                SocialSecuritySpousalBenefitCalculator
                        .calculateReductionFactor(
                                birthDate,
                                YearMonth.of(2025, 2)));
    }

    @Test
    void januaryFirstBirthConventionControlsSpousalFraMonth() {

        assertEquals(
                new BigDecimal("0.750000000000"),
                SocialSecuritySpousalBenefitCalculator
                        .calculateReductionFactor(
                                LocalDate.of(1960, 1, 1),
                                YearMonth.of(2023, 10)));
    }

    private void assertFactor(
            BigDecimal expected,
            YearMonth entitlementMonth) {

        assertEquals(
                expected,
                SocialSecuritySpousalBenefitCalculator
                        .calculateReductionFactor(
                                FRA_67_BIRTH_DATE,
                                entitlementMonth));
    }

    private void assertMoney(
            BigDecimal expected,
            BigDecimal actual) {

        assertEquals(0, expected.compareTo(actual));
    }
}
