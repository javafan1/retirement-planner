package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RmdBalanceSnapshotTest {

    private AccountPortfolio portfolio;
    private ProjectedPortfolio projectedPortfolio;

    @BeforeEach
    void setUp() {

        portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")));

        portfolio.addAccount(
                new RothIRA(
                        "Primary Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);
    }

    @Test
    void createsSnapshotWithSpecifiedDate() {

        LocalDate snapshotDate =
                LocalDate.of(
                        2034,
                        12,
                        31);

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        snapshotDate,
                        projectedPortfolio);

        assertEquals(
                snapshotDate,
                snapshot.getSnapshotDate());
    }

    @Test
    void capturesProjectedAccountBalances() {

        var traditionalIra =
                portfolio
                        .getAccounts()
                        .get(0);

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                snapshot.getBalance(
                                        traditionalIra)));
    }

    @Test
    void calculatesTotalSnapshotBalance() {

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        assertEquals(
                0,
                new BigDecimal("1500000")
                        .compareTo(
                                snapshot.getTotalBalance()));
    }

    @Test
    void snapshotIsIndependentOfLaterProjectedPortfolio() {

        var traditionalIra =
                portfolio
                        .getAccounts()
                        .get(0);

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        ProjectedPortfolio updated =
                projectedPortfolio.withBalance(
                        traditionalIra,
                        new BigDecimal("900000"));

        /*
         * The new projected portfolio changed.
         */
        assertEquals(
                0,
                new BigDecimal("900000")
                        .compareTo(
                                updated.getBalance(
                                        traditionalIra)));

        /*
         * But the December 31 snapshot remains
         * exactly as it was when captured.
         */
        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                snapshot.getBalance(
                                        traditionalIra)));
    }
    /*
    12/31/2034 snapshot
Traditional IRA = $1,000,000
        │
        │ captured
        ▼
2035 RMD calculation
        uses $1,000,000

Meanwhile projection continues
        │
        ▼
Traditional IRA = $900,000
     */
}