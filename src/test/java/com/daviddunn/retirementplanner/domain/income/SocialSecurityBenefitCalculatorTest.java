package com.daviddunn.retirementplanner.domain.income;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityBenefitCalculatorTest {

    @Test
    void personBorn1963ClaimingAt62Gets70Percent() {

        BigDecimal factor =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        BigDecimal.ONE,
                        LocalDate.of(1963, 6, 4),
                        62);

        assertEquals(
                new BigDecimal("0.7000000000"),
                factor.setScale(10, RoundingMode.HALF_UP));
    }

    @Test
    void personBorn1963ClaimingAt67Gets100Percent() {

        BigDecimal factor =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        BigDecimal.ONE,
                        LocalDate.of(1963, 6, 4),
                        67);

        assertEquals(
                new BigDecimal("1.0000000000"),
                factor.setScale(10, RoundingMode.HALF_UP));
    }

    @Test
    void personBorn1963ClaimingAt70Gets124Percent() {

        BigDecimal factor =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        BigDecimal.ONE,
                        LocalDate.of(1963, 6, 4),
                        70);

        assertEquals(
                new BigDecimal("1.2400000000"),
                factor.setScale(10, RoundingMode.HALF_UP));
    }

    @Test
    void personBorn1958ClaimingAt62GetsCorrectReduction() {

        BigDecimal factor =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        BigDecimal.ONE,
                        LocalDate.of(1958, 6, 1),
                        62);

        assertEquals(
                new BigDecimal("0.7166666667"),
                factor.setScale(10, RoundingMode.HALF_UP));
    }

    @Test
    void personBorn1958ClaimingAt67GetsCorrectDelayedCredit() {

        BigDecimal factor =
                SocialSecurityBenefitCalculator.calculateMonthlyBenefit(
                        BigDecimal.ONE,
                        LocalDate.of(1958, 6, 1),
                        67);

        assertEquals(
                new BigDecimal("1.0266666667"),
                factor.setScale(10, RoundingMode.HALF_UP));
    }
}