package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RolloverIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnerRmdServiceTest {

    private OwnerRmdService service;
    private GovernmentRules rules;
    private AccountPortfolio portfolio;

    @BeforeEach
    void setUp() throws Exception {

        service =
                new OwnerRmdService();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        portfolio =
                new AccountPortfolio();

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

        portfolio.addAccount(
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000")));

        portfolio.addAccount(
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("600000")));
    }

    @Test
    void calculatesPrimaryIraRmdAtStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                service.calculateIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * Primary Traditional IRA     $500,000
         * Primary Rollover IRA         300,000
         *                              -------
         * RMD balance                 $800,000
         *
         * Age 75 distribution period = 24.6
         *
         * $800,000 / 24.6
         * = $32,520.33
         */

        assertEquals(
                0,
                new BigDecimal("32520.33")
                        .compareTo(rmd));
    }

    @Test
    void returnsZeroBeforePrimaryRmdStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                service.calculateIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2034,
                        rules);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(rmd));
    }

    @Test
    void calculatesSpouseIraRmdSeparately() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        2,
                        28);

        BigDecimal rmd =
                service.calculateIraRmd(
                        portfolio,
                        AccountOwnership.SPOUSE,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * Only spouse IRA = $600,000.
         *
         * $600,000 / 24.6
         * = $24,390.24
         */

        assertEquals(
                0,
                new BigDecimal("24390.24")
                        .compareTo(rmd));
    }

    @Test
    void rothIraIsExcludedFromRmdBalance() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        BigDecimal rmd =
                service.calculateIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * The $200,000 Roth IRA must not
         * increase the RMD balance.
         */

        assertEquals(
                0,
                new BigDecimal("32520.33")
                        .compareTo(rmd));
    }

    @Test
    void usesRmdBalanceSnapshotInsteadOfCurrentAccountBalance() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        /*
         * The actual portfolio contains:
         *
         * Primary Traditional IRA = $500,000
         * Primary Rollover IRA    = $300,000
         *
         * Actual IRA pool = $800,000
         */

        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);

        var traditionalIra =
                portfolio
                        .getAccounts()
                        .get(0);

        var rolloverIra =
                portfolio
                        .getAccounts()
                        .get(1);

        /*
         * Simulate projected 12/31/2034 balances
         * that are different from the actual
         * account balances.
         */
        projectedPortfolio =
                projectedPortfolio.withBalance(
                        traditionalIra,
                        new BigDecimal("1000000"));

        projectedPortfolio =
                projectedPortfolio.withBalance(
                        rolloverIra,
                        new BigDecimal("500000"));

        /*
         * Projected IRA balance on 12/31/2034:
         *
         * $1,000,000
         * +  500,000
         * ----------
         * $1,500,000
         */
        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        BigDecimal rmd =
                service.calculateIraRmd(
                        portfolio,
                        snapshot,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * Age 75 divisor = 24.6
         *
         * $1,500,000 / 24.6
         * = $60,975.61
         *
         * If the service incorrectly used the
         * actual $800,000 portfolio balance,
         * it would return $32,520.33 instead.
         */

        assertEquals(
                0,
                new BigDecimal("60975.61")
                        .compareTo(rmd));
    }
}