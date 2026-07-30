package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederalTaxableIncomeCalculatorTest {

    private FederalTaxableIncomeCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new FederalTaxableIncomeCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void marriedFilingJointlySubtractsStandardDeduction() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        new BigDecimal("150000"),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * $150,000 AGI
         * - $32,200 standard deduction
         * ----------------------------
         * $117,800 taxable income
         */

        assertEquals(
                0,
                new BigDecimal("117800")
                        .compareTo(taxableIncome));
    }

    @Test
    void singleSubtractsStandardDeduction() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        new BigDecimal("50000"),
                        FilingStatus.SINGLE,
                        rules);

        /*
         * $50,000 AGI
         * - $16,100 standard deduction
         * ----------------------------
         * $33,900 taxable income
         */

        assertEquals(
                0,
                new BigDecimal("33900")
                        .compareTo(taxableIncome));
    }

    @Test
    void deductionCannotProduceNegativeTaxableIncome() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        new BigDecimal("10000"),
                        FilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(taxableIncome));
    }

    @Test
    void zeroAgiProducesZeroTaxableIncome() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        BigDecimal.ZERO,
                        FilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(taxableIncome));
    }

    @Test
    void negativeAgiProducesZeroTaxableIncome() {

        BigDecimal taxableIncome =
                calculator.calculateTaxableIncome(
                        new BigDecimal("-5000"),
                        FilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO
                        .compareTo(taxableIncome));
    }
}