package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RmdCalculatorTest {

    private RmdCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new RmdCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void calculatesRmdAtAge75() {

        BigDecimal rmd =
                calculator.calculateRmd(
                        new BigDecimal("1000000"),
                        75,
                        rules);

        /*
         * $1,000,000 / 24.6
         * = $40,650.41
         */

        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(rmd));
    }

    @Test
    void calculatesRmdAtAge80() {

        BigDecimal rmd =
                calculator.calculateRmd(
                        new BigDecimal("500000"),
                        80,
                        rules);

        /*
         * Age 80 distribution period = 20.2
         *
         * $500,000 / 20.2
         * = $24,752.48
         */

        assertEquals(
                0,
                new BigDecimal("24752.48")
                        .compareTo(rmd));
    }

    @Test
    void zeroBalanceProducesZeroRmd() {

        BigDecimal rmd =
                calculator.calculateRmd(
                        BigDecimal.ZERO,
                        75,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(rmd));
    }

    @Test
    void negativeBalanceIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calculator.calculateRmd(
                                new BigDecimal("-1000"),
                                75,
                                rules));
    }

    @Test
    void unknownAgeIsRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calculator.calculateRmd(
                                new BigDecimal("1000000"),
                                70,
                                rules));
    }
}