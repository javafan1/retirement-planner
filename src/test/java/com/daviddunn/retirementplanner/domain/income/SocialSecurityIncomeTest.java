package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityIncomeTest {

    @Test
    void claimingAt67ReturnsFullRetirementBenefit() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("36000"),
                annualIncome);
    }

    @Test
    void claimingAt62ReturnsReducedBenefit() {

        SocialSecurityIncome income =
                createIncome(62);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("25200.0"),
                annualIncome);
    }

    @Test
    void claimingAt70ReturnsDelayedBenefit() {

        SocialSecurityIncome income =
                createIncome(70);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("44640.00"),
                annualIncome);
    }

    @Test
    void beforeStartDateReturnsZero() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        LocalDate.of(2029, 12, 31));

        assertEquals(
                BigDecimal.ZERO,
                annualIncome);
    }

    private SocialSecurityIncome createIncome(int claimingAge) {

        return new SocialSecurityIncome(
                "Social Security",
                AccountOwnership.PRIMARY,
                LocalDate.of(2030, 1, 1),
                null,
                new BigDecimal("3000"),
                claimingAge,
                new BigDecimal("0.025"));
    }

    @Test
    void socialSecurityAppliesColaAfterFirstYear() {

        SocialSecurityIncome income =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        new BigDecimal("0.025"));

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        LocalDate.of(2031, 1, 1));

        assertEquals(
                new BigDecimal("36900.000"),
                annualIncome);
    }
}