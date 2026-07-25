package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RmdEligibilityCalculatorTest {

    private RmdEligibilityCalculator calculator;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new RmdEligibilityCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void personBornIn1959DoesNotRequireRmdAt72() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1959,
                        6,
                        15);

        assertFalse(
                calculator.isRmdRequired(
                        dateOfBirth,
                        2031,
                        rules));
    }

    @Test
    void personBornIn1959RequiresRmdAt73() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1959,
                        6,
                        15);

        assertTrue(
                calculator.isRmdRequired(
                        dateOfBirth,
                        2032,
                        rules));
    }

    @Test
    void personBornIn1960DoesNotRequireRmdAt74() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        assertFalse(
                calculator.isRmdRequired(
                        dateOfBirth,
                        2034,
                        rules));
    }

    @Test
    void personBornIn1960RequiresRmdAt75() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        assertTrue(
                calculator.isRmdRequired(
                        dateOfBirth,
                        2035,
                        rules));
    }

    @Test
    void birthMonthDoesNotChangeRmdYear() {

        LocalDate januaryBirth =
                LocalDate.of(
                        1960,
                        1,
                        1);

        LocalDate decemberBirth =
                LocalDate.of(
                        1960,
                        12,
                        31);

        assertTrue(
                calculator.isRmdRequired(
                        januaryBirth,
                        2035,
                        rules));

        assertTrue(
                calculator.isRmdRequired(
                        decemberBirth,
                        2035,
                        rules));
    }

    @Test
    void projectionYearBeforeBirthYearIsRejected() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calculator.isRmdRequired(
                                dateOfBirth,
                                1959,
                                rules));
    }
}