package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxCalculatorTest {

    private TaxCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new TaxCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void calculatesFederalTaxCalculationForMarriedCouple() {

        TaxCalculation calculation =
                calculator.calculate(
                        new BigDecimal("150000"),
                        FilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * AGI:
         *
         * $150,000
         */
        assertEquals(
                0,
                new BigDecimal("150000")
                        .compareTo(
                                calculation
                                        .getAdjustedGrossIncome()));

        /*
         * 2026 MFJ standard deduction:
         *
         * $150,000
         * - $32,200
         * ----------
         * $117,800 taxable income
         */
        assertEquals(
                0,
                new BigDecimal("117800")
                        .compareTo(
                                calculation
                                        .getTaxableIncome()));

        /*
         * Federal income tax:
         *
         * First $24,800 @ 10%
         * = $2,480
         *
         * Next $76,000 @ 12%
         * = $9,120
         *
         * Remaining:
         *
         * $117,800 - $100,800
         * = $17,000 @ 22%
         * = $3,740
         *
         * Total:
         *
         * $2,480 + $9,120 + $3,740
         * = $15,340
         */
        assertEquals(
                0,
                new BigDecimal("15340.00")
                        .compareTo(
                                calculation
                                        .getFederalIncomeTax()));
    }
}