package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MichiganIncomeTaxCalculatorTest {

    private MichiganIncomeTaxCalculator calculator;
    private MichiganTaxRules rules;

    @BeforeEach
    void setUp() {

        calculator =
                new MichiganIncomeTaxCalculator();

        rules =
                new MichiganTaxRules(
                        new BigDecimal("0.0425"),
                        new BigDecimal("65897"),
                        new BigDecimal("131794"));
    }

    @Test
    void calculatesIncomeTax() {

        BigDecimal incomeTax =
                calculator.calculate(
                        new BigDecimal("100000"),
                        rules);

        assertEquals(
                new BigDecimal("4250.0000"),
                incomeTax);
    }

    @Test
    void zeroIncomeProducesZeroTax() {

        BigDecimal incomeTax =
                calculator.calculate(
                        BigDecimal.ZERO,
                        rules);

        assertEquals(
                BigDecimal.ZERO,
                incomeTax);
    }

    @Test
    void negativeIncomeProducesZeroTax() {

        BigDecimal incomeTax =
                calculator.calculate(
                        new BigDecimal("-1"),
                        rules);

        assertEquals(
                BigDecimal.ZERO,
                incomeTax);
    }
}