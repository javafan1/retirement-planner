package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RmdServiceTest {

    private RmdService rmdService;
    private GovernmentRules rules;

    @BeforeEach
    void setUp() throws Exception {

        rmdService =
                new RmdService();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");
    }

    @Test
    void returnsZeroBeforeRmdStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                rmdService.calculateRmd(
                        dateOfBirth,
                        2034,
                        new BigDecimal("1000000"),
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(rmd));
    }

    @Test
    void calculatesRmdAtStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                rmdService.calculateRmd(
                        dateOfBirth,
                        2035,
                        new BigDecimal("1000000"),
                        rules);

        /*
         * Born 1960 -> RMD starting age 75.
         *
         * Age 75 distribution period = 24.6
         *
         * $1,000,000 / 24.6
         * = $40,650.41
         */

        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(rmd));
    }

    @Test
    void calculatesRmdAfterStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                rmdService.calculateRmd(
                        dateOfBirth,
                        2040,
                        new BigDecimal("750000"),
                        rules);

        /*
         * Age 80 distribution period = 20.2
         *
         * $750,000 / 20.2
         * = $37,128.71
         */

        assertEquals(
                0,
                new BigDecimal("37128.71")
                        .compareTo(rmd));
    }

    @Test
    void zeroBalanceAtRmdAgeReturnsZero() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                rmdService.calculateRmd(
                        dateOfBirth,
                        2035,
                        BigDecimal.ZERO,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(rmd));
    }

    @Test
    void negativeBalanceAtRmdAgeIsRejected() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        rmdService.calculateRmd(
                                dateOfBirth,
                                2035,
                                new BigDecimal("-1000"),
                                rules));
    }
}