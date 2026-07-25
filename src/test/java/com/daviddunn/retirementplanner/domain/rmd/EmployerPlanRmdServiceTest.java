package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.Roth401K;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.financial.Traditional403B;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployerPlanRmdServiceTest {

    private EmployerPlanRmdService service;
    private GovernmentRules rules;
    private AccountPortfolio portfolio;

    @BeforeEach
    void setUp() throws Exception {

        service =
                new EmployerPlanRmdService();

        GovernmentRulesRepository repository =
                new GovernmentRulesRepository();

        rules =
                repository.load(
                        "/rules/government-rules-2026.json");

        portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k) A",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k) B",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("250000")));

        portfolio.addAccount(
                new Roth401K(
                        "Primary Roth 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000")));

        portfolio.addAccount(
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        portfolio.addAccount(
                new Traditional401K(
                        "Spouse 401(k)",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("400000")));

        portfolio.addAccount(
                new Traditional403B(
                        "Primary 403(b) A",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        portfolio.addAccount(
                new Traditional403B(
                        "Primary 403(b) B",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000")));
    }

    @Test
    void calculatesEachPrimary401kSeparately() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        List<AccountRmd> results =
                service.calculate401kRmds(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        assertEquals(
                2,
                results.size());

        /*
         * $500,000 / 24.6 = $20,325.20
         */
        assertEquals(
                "Primary 401(k) A",
                results.get(0)
                        .getAccount()
                        .getName());

        assertEquals(
                0,
                new BigDecimal("20325.20")
                        .compareTo(
                                results.get(0)
                                        .getAmount()));

        /*
         * $250,000 / 24.6 = $10,162.60
         */
        assertEquals(
                "Primary 401(k) B",
                results.get(1)
                        .getAccount()
                        .getName());

        assertEquals(
                0,
                new BigDecimal("10162.60")
                        .compareTo(
                                results.get(1)
                                        .getAmount()));
    }

    @Test
    void excludesRoth401kAndIra() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        List<AccountRmd> results =
                service.calculate401kRmds(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        assertEquals(
                2,
                results.size());
    }

    @Test
    void keepsSpouse401kSeparate() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        2,
                        28);

        List<AccountRmd> results =
                service.calculate401kRmds(
                        portfolio,
                        AccountOwnership.SPOUSE,
                        dateOfBirth,
                        2035,
                        rules);

        assertEquals(
                1,
                results.size());

        assertEquals(
                "Spouse 401(k)",
                results.get(0)
                        .getAccount()
                        .getName());

        /*
         * $400,000 / 24.6
         * = $16,260.16
         */
        assertEquals(
                0,
                new BigDecimal("16260.16")
                        .compareTo(
                                results.get(0)
                                        .getAmount()));
    }

    @Test
    void returnsZeroRmdsBeforeStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        List<AccountRmd> results =
                service.calculate401kRmds(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2034,
                        rules);

        assertEquals(
                2,
                results.size());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        results.get(0)
                                .getAmount()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        results.get(1)
                                .getAmount()));
    }

    @Test
    void calculatesEachPrimary403bSeparately() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        List<AccountRmd> results =
                service.calculate403bRmds(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        assertEquals(
                2,
                results.size());

        /*
         * $300,000 / 24.6
         * = $12,195.12
         */
        assertEquals(
                "Primary 403(b) A",
                results.get(0)
                        .getAccount()
                        .getName());

        assertEquals(
                0,
                new BigDecimal("12195.12")
                        .compareTo(
                                results.get(0)
                                        .getAmount()));

        /*
         * $200,000 / 24.6
         * = $8,130.08
         */
        assertEquals(
                "Primary 403(b) B",
                results.get(1)
                        .getAccount()
                        .getName());

        assertEquals(
                0,
                new BigDecimal("8130.08")
                        .compareTo(
                                results.get(1)
                                        .getAmount()));
    }

    @Test
    void returnsZero403bRmdsBeforeStartingAge() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        List<AccountRmd> results =
                service.calculate403bRmds(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2034,
                        rules);

        assertEquals(
                2,
                results.size());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        results.get(0)
                                .getAmount()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        results.get(1)
                                .getAmount()));
    }

    @Test
    void usesSnapshotBalanceFor401kRmd() {

        LocalDate dateOfBirth =
                LocalDate.of(
                        1960,
                        6,
                        15);

        /*
         * Find Primary 401(k) A.
         *
         * Its actual account balance is $500,000.
         */
        var primary401k =
                portfolio
                        .getAccounts()
                        .stream()
                        .filter(account ->
                                account.getName()
                                        .equals("Primary 401(k) A"))
                        .findFirst()
                        .orElseThrow();

        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);

        /*
         * Simulate a projected 12/31/2034 balance
         * of $1,000,000.
         */
        projectedPortfolio =
                projectedPortfolio.withBalance(
                        primary401k,
                        new BigDecimal("1000000"));

        RmdBalanceSnapshot snapshot =
                RmdBalanceSnapshot.from(
                        LocalDate.of(
                                2034,
                                12,
                                31),
                        projectedPortfolio);

        List<AccountRmd> results =
                service.calculate401kRmds(
                        portfolio,
                        snapshot,
                        AccountOwnership.PRIMARY,
                        dateOfBirth,
                        2035,
                        rules);

        /*
         * There are still two primary traditional
         * 401(k) accounts.
         */
        assertEquals(
                2,
                results.size());

        AccountRmd primary401kResult =
                results
                        .stream()
                        .filter(result ->
                                result.getAccount()
                                        == primary401k)
                        .findFirst()
                        .orElseThrow();

        /*
         * Age 75 divisor = 24.6
         *
         * Snapshot balance:
         * $1,000,000 / 24.6
         * = $40,650.41
         *
         * If the service incorrectly used the
         * actual $500,000 balance, the result
         * would be $20,325.20.
         */
        assertEquals(
                0,
                new BigDecimal("40650.41")
                        .compareTo(
                                primary401kResult
                                        .getAmount()));

        /*
         * The actual account itself must remain
         * unchanged.
         */
        assertEquals(
                0,
                new BigDecimal("500000")
                        .compareTo(
                                primary401k
                                        .getCurrentBalance()));
    }
}