package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.testing.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RothConversionPlannerTest {

    private RothConversionPlanner planner;

    private FederalTaxRules federalTaxRules;

    @BeforeEach
    void setUp() {

        planner =
                new RothConversionPlanner();

        federalTaxRules =
                TestDataFactory
                        .marriedFilingJointlyFederalTaxRules();
    }

    @Test
    void shouldReturnZeroWhenStrategyIsNone() {

        PlanningAssumptions assumptions =
                TestDataFactory.planningAssumptions(
                        RothConversionStrategy.NONE);

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("150000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),BigDecimal.ZERO);

        RothConversionPlan result =
                planner.plan(
                        federalTaxRules,
                        assumptions,
                        taxIncome,
                        new BigDecimal("500000"));

        assertEquals(
                BigDecimal.ZERO,
                result.getConversionAmount());
    }

    @Test
    void shouldFillTwentyTwoPercentBracket() {

        PlanningAssumptions assumptions =
                TestDataFactory.planningAssumptions(
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET);

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("150000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),BigDecimal.ZERO);

        RothConversionPlan result =
                planner.plan(
                        federalTaxRules,
                        assumptions,
                        taxIncome,
                        new BigDecimal("500000"));

        /*
         * Ordinary income:
         *   Pension                  150,000
         *   IRA Withdrawals           30,000
         *                            -------
         *                            180,000
         *
         * Less Standard Deduction     30,000
         *                            -------
         * Taxable Income             150,000
         *
         * Top of 22% bracket         211,400
         *
         * Remaining room = 61,400
         */
        assertEquals(
                new BigDecimal("61400"),
                result.getConversionAmount());
    }

    @Test
    void shouldNotConvertMoreThanAvailableTraditionalBalance() {

        PlanningAssumptions assumptions =
                TestDataFactory.planningAssumptions(
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET);

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("150000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),BigDecimal.ZERO);

        RothConversionPlan result =
                planner.plan(
                        federalTaxRules,
                        assumptions,
                        taxIncome,
                        new BigDecimal("10000"));

        assertEquals(
                new BigDecimal("10000"),
                result.getConversionAmount());
    }

    @Test
    void shouldReturnZeroWhenAlreadyAboveTwentyTwoPercentBracket() {

        PlanningAssumptions assumptions =
                TestDataFactory.planningAssumptions(
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET);

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("250000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,BigDecimal.ZERO);

        RothConversionPlan result =
                planner.plan(
                        federalTaxRules,
                        assumptions,
                        taxIncome,
                        new BigDecimal("500000"));

        assertEquals(
                BigDecimal.ZERO,
                result.getConversionAmount());
    }

    @Test
    void shouldReturnZeroWhenTraditionalBalanceIsZero() {

        PlanningAssumptions assumptions =
                TestDataFactory.planningAssumptions(
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET);

        TaxIncome taxIncome =
                new TaxIncome(
                        new BigDecimal("150000"),
                        BigDecimal.ZERO,
                        new BigDecimal("30000"),BigDecimal.ZERO);

        RothConversionPlan result =
                planner.plan(
                        federalTaxRules,
                        assumptions,
                        taxIncome,
                        BigDecimal.ZERO);

        assertEquals(
                BigDecimal.ZERO,
                result.getConversionAmount());
    }
}