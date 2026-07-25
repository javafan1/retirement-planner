package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RolloverIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.Traditional403B;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
//Primary owner, age 75
//        │
//        ├── IRA + Rollover     $800,000 → $32,520.33
//        │
//        ├── 401(k)             $400,000 → $16,260.16
//        │
//        └── 403(b)             $200,000 →  $8,130.08
//        ----------
//Total →  $56,910.57
class OwnerRmdCalculatorTest {

    private OwnerRmdCalculator calculator;
    private GovernmentRules rules;
    private AccountPortfolio portfolio;

    @BeforeEach
    void setUp() throws Exception {

        calculator =
                new OwnerRmdCalculator();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        portfolio =
                new AccountPortfolio();

        /*
         * IRA pool = $800,000
         */
        portfolio.addAccount(
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        portfolio.addAccount(
                new RolloverIRA(
                        "Primary Rollover IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        /*
         * Separate 401(k)
         */
        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("400000")));

        /*
         * 403(b)
         */
        portfolio.addAccount(
                new Traditional403B(
                        "Primary 403(b)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000")));
    }

    @Test
    void calculatesCompleteOwnerRmdAtAge75() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        OwnerRmdResult result =
                calculator.calculate(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * IRA:
         *
         * $800,000 / 24.6
         * = $32,520.33
         */
        assertEquals(
                0,
                new BigDecimal("32520.33")
                        .compareTo(
                                result.getIraRmd()));

        /*
         * 401(k):
         *
         * $400,000 / 24.6
         * = $16,260.16
         */
        assertEquals(
                1,
                result.getTraditional401kRmds()
                        .size());

        assertEquals(
                0,
                new BigDecimal("16260.16")
                        .compareTo(
                                result
                                        .getTraditional401kRmdTotal()));

        /*
         * 403(b):
         *
         * $200,000 / 24.6
         * = $8,130.08
         */
        assertEquals(
                1,
                result.getTraditional403bRmds()
                        .size());

        assertEquals(
                0,
                new BigDecimal("8130.08")
                        .compareTo(
                                result
                                        .getTraditional403bRmdTotal()));

        /*
         * Total:
         *
         * $32,520.33
         * $16,260.16
         *  $8,130.08
         * ----------
         * $56,910.57
         */
        assertEquals(
                0,
                new BigDecimal("56910.57")
                        .compareTo(
                                result.getTotalRmd()));
    }

    @Test
    void completeOwnerRmdIsZeroBeforeStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        OwnerRmdResult result =
                calculator.calculate(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2034,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getIraRmd()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTraditional401kRmdTotal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTraditional403bRmdTotal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.getTotalRmd()));
    }
}