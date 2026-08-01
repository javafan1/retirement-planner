package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MichiganTaxableIncomeCalculatorTest {

    private MichiganTaxableIncomeCalculator calculator;
    private MichiganTaxRules projectedRules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new MichiganTaxableIncomeCalculator();

        GovernmentRules governmentRules =
                new GovernmentRulesRepository()
                        .load("/rules/government-rules-2026.json");

        PlanningAssumptions planningAssumptions =
                new PlanningAssumptions(
                        new BigDecimal("0.070"),
                        new BigDecimal("0.025"),
                        30,
                        LocalDate.of(2026, 1, 1));

        projectedRules =
                new MichiganTaxRuleProjectionService()
                        .project(
                                governmentRules.getMichiganTaxRules(),
                                governmentRules.getTaxYear(),
                                2027,
                                planningAssumptions);
    }

    @Test
    void retirementIncomeBelowDeductionProducesZeroTaxableIncome() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        BigDecimal.valueOf(30_000),
                        FilingStatus.SINGLE,
                        projectedRules);

        assertEquals(
                BigDecimal.ZERO,
                taxableIncome);
    }

    @Test
    void retirementIncomeAboveDeductionProducesTaxableIncome() {

        BigDecimal retirementIncome =
                BigDecimal.valueOf(100_000);

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        retirementIncome,
                        FilingStatus.SINGLE,
                        projectedRules);

        BigDecimal expected =
                retirementIncome.subtract(
                        projectedRules
                                .getRetirementDeductionSingle());

        assertEquals(
                expected,
                taxableIncome);
    }

    @Test
    void marriedRetirementIncomeAboveDeductionProducesTaxableIncome() {

        BigDecimal retirementIncome =
                BigDecimal.valueOf(200_000);

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        retirementIncome,
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        projectedRules);

        BigDecimal expected =
                retirementIncome.subtract(
                        projectedRules
                                .getRetirementDeductionMarried());

        assertEquals(
                expected,
                taxableIncome);
    }
}