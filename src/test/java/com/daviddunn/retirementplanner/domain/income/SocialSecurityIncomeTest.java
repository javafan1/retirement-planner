package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialSecurityIncomeTest {

    private final Person person =
            new Person(
                    "John",
                    "Doe",
                    LocalDate.of(1963, 1, 1));

    @Test
    void claimingAt67ReturnsFullRetirementBenefit() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("36000.00"),
                annualIncome);
    }

    @Test
    void claimingAt62ReturnsReducedBenefit() {

        SocialSecurityIncome income =
                createIncome(62);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2030, 1, 1));

        assertEquals(
                new BigDecimal("25200.00"),
                annualIncome);
    }

    @Test
    void claimingAt70ReturnsDelayedBenefit() {

        SocialSecurityIncome income =
                createIncome(70);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
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
                        person,
                        LocalDate.of(2029, 12, 31));

        assertEquals(
                BigDecimal.ZERO,
                annualIncome);
    }

    @Test
    void socialSecurityAppliesColaAfterFirstYear() {

        SocialSecurityIncome income =
                createIncome(67);

        BigDecimal annualIncome =
                income.getAnnualIncome(
                        person,
                        LocalDate.of(2031, 1, 1));

        assertEquals(
                new BigDecimal("36900.00"),
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
    void projectedMonthlyBenefitUsesRetirementBenefitAndCola() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome socialSecurity =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO);

        BigDecimal monthlyBenefit =
                socialSecurity.getProjectedMonthlyBenefit(
                        person,
                        LocalDate.of(2035, 1, 1));

        BigDecimal expected =
                SocialSecurityBenefitCalculator
                        .calculateMonthlyBenefit(
                                new BigDecimal("3000"),
                                person.getBirthDate(),
                                67);

        assertEquals(
                0,
                expected.compareTo(monthlyBenefit));
    }

    @Test
    void monthlyBenefitAtDeathUsesActualClaimedBenefit() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome income =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2030, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        67,
                        BigDecimal.ZERO);

        BigDecimal result =
                income.getMonthlyBenefitAtDeath(
                        person,
                        LocalDate.of(2035, 1, 1));

        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }


    @Test
    void monthlyBenefitAtDeathUsesFullRetirementBenefitWhenNotYetClaimed() {

        Person person =
                new Person(
                        "David",
                        "Dunn",
                        LocalDate.of(1963, 6, 4));

        SocialSecurityIncome income =
                new SocialSecurityIncome(
                        "Social Security",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2038, 1, 1),
                        null,
                        new BigDecimal("3000"),
                        70,
                        new BigDecimal("0.025"));

        BigDecimal result =
                income.getMonthlyBenefitAtDeath(
                        person,
                        LocalDate.of(2035, 1, 1));


        assertEquals(
                new BigDecimal("3000.00"),
                result);
    }


}