package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomeSummaryServiceTest {

    private final IncomeSummaryService service =
            new IncomeSummaryService();

    @Test
    void emptyPlanReturnsZeroIncome() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        IncomeSummary summary =
                service.summarize(plan);

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                summary.getPrimarySocialSecurityMonthly());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                summary.getSpouseSocialSecurityMonthly());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                summary.getPrimaryPensionMonthly());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                summary.getSpousePensionMonthly());

        assertEquals(
                BigDecimal.ZERO.setScale(2),
                summary.getTotalGuaranteedMonthlyIncome());
    }

    @Test
    void summarizesPrimarySocialSecurity() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        Person primary =
                plan.getHousehold()
                        .getPrimaryPerson();

        primary.setBirthDate(
                LocalDate.of(1963, 6, 4));

        primary.addIncomeSource(
                new SocialSecurityIncome(
                        "Primary SS",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2033, 6, 4),
                        null,
                        new BigDecimal("3673"),
                        70,
                        BigDecimal.ZERO));

        IncomeSummary summary =
                service.summarize(plan);

        assertEquals(
                new BigDecimal("4554.52"),
                summary.getPrimarySocialSecurityMonthly());
    }

    @Test
    void summarizesPrimaryPension() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        Person primary =
                plan.getHousehold()
                        .getPrimaryPerson();

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1000"),
                        BigDecimal.ZERO));

        IncomeSummary summary =
                service.summarize(plan);

        assertEquals(
                new BigDecimal("1000.00"),
                summary.getPrimaryPensionMonthly());
    }

    @Test
    void calculatesTotalGuaranteedIncome() {

        RetirementPlan plan =
                RetirementPlanFactory.createEmptyPlan();

        Person primary =
                plan.getHousehold()
                        .getPrimaryPerson();

        Person spouse =
                plan.getHousehold()
                        .getSpouse();

        primary.setBirthDate(
                LocalDate.of(1963, 6, 4));

        spouse.setBirthDate(
                LocalDate.of(1965, 2, 28));

        primary.addIncomeSource(
                new Pension(
                        "Primary Pension",
                        AccountOwnership.PRIMARY,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("1000"),
                        BigDecimal.ZERO));

        spouse.addIncomeSource(
                new Pension(
                        "Spouse Pension",
                        AccountOwnership.SPOUSE,
                        LocalDate.of(2026, 1, 1),
                        null,
                        new BigDecimal("849"),
                        BigDecimal.ZERO));

        IncomeSummary summary =
                service.summarize(plan);

        assertEquals(
                new BigDecimal("1849.00"),
                summary.getTotalGuaranteedMonthlyIncome());
    }
}