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

class MichiganRetirementDeductionCalculatorTest {

    private MichiganRetirementDeductionCalculator calculator;
    private MichiganTaxRules projectedRules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new MichiganRetirementDeductionCalculator();

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
    void singleTaxpayerGetsFullDeductionWhenBelowLimit() {

        BigDecimal deduction =
                calculator.calculate(
                        BigDecimal.valueOf(30_000),
                        FilingStatus.SINGLE,
                        projectedRules);

        assertEquals(
                BigDecimal.valueOf(30_000),
                deduction);
    }

    @Test
    void singleTaxpayerIsLimitedToProjectedDeduction() {

        BigDecimal deduction =
                calculator.calculate(
                        BigDecimal.valueOf(100_000),
                        FilingStatus.SINGLE,
                        projectedRules);

        assertEquals(
                projectedRules.getRetirementDeductionSingle(),
                deduction);
    }

    @Test
    void marriedTaxpayerGetsFullDeductionWhenBelowLimit() {

        BigDecimal deduction =
                calculator.calculate(
                        BigDecimal.valueOf(60_000),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        projectedRules);

        assertEquals(
                BigDecimal.valueOf(60_000),
                deduction);
    }

    @Test
    void marriedTaxpayerIsLimitedToProjectedDeduction() {

        BigDecimal deduction =
                calculator.calculate(
                        BigDecimal.valueOf(200_000),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        projectedRules);

        assertEquals(
                projectedRules.getRetirementDeductionMarried(),
                deduction);
    }
}