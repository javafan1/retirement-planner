package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FederalIncomeTaxCalculatorTest {

    private FederalIncomeTaxCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new FederalIncomeTaxCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void zeroTaxableIncomeProducesZeroTax() {

        BigDecimal tax =
                calculator.calculateTax(
                        BigDecimal.ZERO,
                        TaxFilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(tax));
    }

    @Test
    void incomeInsideFirstBracketUsesTenPercentRate() {

        BigDecimal tax =
                calculator.calculateTax(
                        new BigDecimal("10000"),
                        TaxFilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(tax));
    }

    @Test
    void singleIncomeCrossingFirstBracketCalculatesProgressively() {

        BigDecimal tax =
                calculator.calculateTax(
                        new BigDecimal("20000"),
                        TaxFilingStatus.SINGLE,
                        rules);

        /*
         * First $12,400:
         *
         * $12,400 × 10% = $1,240
         *
         * Remaining $7,600:
         *
         * $7,600 × 12% = $912
         *
         * Total = $2,152
         */

        assertEquals(
                0,
                new BigDecimal("2152.00")
                        .compareTo(tax));
    }

    @Test
    void marriedIncomeCrossingMultipleBracketsCalculatesProgressively() {

        BigDecimal tax =
                calculator.calculateTax(
                        new BigDecimal("150000"),
                        TaxFilingStatus.MARRIED_FILING_JOINTLY,
                        rules);

        /*
         * 2026 MFJ:
         *
         * $24,800 × 10% = $2,480
         *
         * $76,000 × 12% = $9,120
         *
         * Remaining:
         *
         * $150,000 - $100,800
         * = $49,200
         *
         * $49,200 × 22% = $10,824
         *
         * Total:
         *
         * $2,480 + $9,120 + $10,824
         * = $22,424
         */

        assertEquals(
                0,
                new BigDecimal("22424.00")
                        .compareTo(tax));
    }

    @Test
    void negativeTaxableIncomeProducesZeroTax() {

        BigDecimal tax =
                calculator.calculateTax(
                        new BigDecimal("-10000"),
                        TaxFilingStatus.SINGLE,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(tax));
    }
}