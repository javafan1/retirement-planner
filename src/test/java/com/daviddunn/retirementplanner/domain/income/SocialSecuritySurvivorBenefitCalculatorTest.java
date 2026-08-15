package com.daviddunn.retirementplanner.domain.income;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialSecuritySurvivorBenefitCalculatorTest {

    @Test
    void survivorAtFraReceives100Percent() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("4554"),
                                survivorBirthDate,
                                fra.years());

        assertEquals(
                new BigDecimal("4554.00"),
                result);
    }

    @Test
    void survivorOlderThanFraReceives100Percent() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("4554.75"),
                                survivorBirthDate,
                                fra.years() + 3);

        assertEquals(
                new BigDecimal("4554.75"),
                result);
    }

    @Test
    void zeroBenefitProducesZeroSurvivorBenefit() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                BigDecimal.ZERO,
                                survivorBirthDate,
                                fra.years());

        assertEquals(
                new BigDecimal("0.00"),
                result);
    }

    @Test
    void negativeBenefitIsRejected() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SocialSecuritySurvivorBenefitCalculator
                                .calculateMonthlyBenefit(
                                        new BigDecimal("-1"),
                                        survivorBirthDate,
                                        fra.years()));
    }

    @Test
    void negativeAgeIsRejected() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SocialSecuritySurvivorBenefitCalculator
                                .calculateMonthlyBenefit(
                                        new BigDecimal("4554"),
                                        survivorBirthDate,
                                        -1));
    }

    @Test
    void survivorClaimingBelow62IsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SocialSecuritySurvivorBenefitCalculator
                                .calculateMonthlyBenefit(
                                        new BigDecimal("4554"),
                                        LocalDate.of(1965, 2, 28),
                                        61));
    }

    @Test
    void nullBenefitIsRejected() {

        LocalDate survivorBirthDate =
                LocalDate.of(1965, 2, 28);

        FullRetirementAge fra =
                FullRetirementAgeCalculator.determine(
                        survivorBirthDate);

        assertThrows(
                NullPointerException.class,
                () ->
                        SocialSecuritySurvivorBenefitCalculator
                                .calculateMonthlyBenefit(
                                        null,
                                        survivorBirthDate,
                                        fra.years()));
    }

    @Test
    void nullBirthDateIsRejected() {

        assertThrows(
                NullPointerException.class,
                () ->
                        SocialSecuritySurvivorBenefitCalculator
                                .calculateMonthlyBenefit(
                                        new BigDecimal("4554"),
                                        null,
                                        67));
    }

    @Test
    void survivorClaimingAt62ReceivesReducedBenefit() {

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                LocalDate.of(1965, 2, 28),
                                62);

        assertEquals(
                new BigDecimal("2389.29"),
                result);
    }

    @Test
    void survivorClaimingAt65ReceivesReducedBenefit() {

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                LocalDate.of(1965, 2, 28),
                                65);

        assertEquals(
                new BigDecimal("2755.71"),
                result);
    }

    @Test
    void survivorClaimingAt67ReceivesFullBenefit() {

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                LocalDate.of(1965, 2, 28),
                                67);

        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }


    @Test
    void survivorClaimingAt70ReceivesFullBenefit() {

        BigDecimal result =
                SocialSecuritySurvivorBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                LocalDate.of(1965, 2, 28),
                                70);

        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }


}