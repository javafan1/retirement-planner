package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.SavingsAccount;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedAssetPoolsBuilderTest {

    private final ProjectedAssetPoolsBuilder builder =
            new ProjectedAssetPoolsBuilder();

    @Test
    void shouldBuildEmptyAssetPools() {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        ProjectedAssetPools pools =
                builder.build(portfolio);

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxableBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxDeferredBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getRothBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTotalBalance());
    }

    @Test
    void shouldAggregateTraditionalAccounts() {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000")));

        ProjectedAssetPools pools =
                builder.build(portfolio);

        assertEquals(
                new BigDecimal("100000"),
                pools.getTaxDeferredBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxableBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getRothBalance());
    }

    @Test
    void shouldAggregateRothAccounts() {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000")));

        ProjectedAssetPools pools =
                builder.build(portfolio);

        assertEquals(
                new BigDecimal("50000"),
                pools.getRothBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxDeferredBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxableBalance());
    }

    @Test
    void shouldAggregateTaxableAccounts() {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("80000")));

        portfolio.addAccount(
                new CheckingAccount(
                        "Checking",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("15000")));

        portfolio.addAccount(
                new SavingsAccount(
                        "Savings",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("5000")));

        ProjectedAssetPools pools =
                builder.build(portfolio);

        assertEquals(
                new BigDecimal("100000"),
                pools.getTaxableBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getTaxDeferredBalance());

        assertEquals(
                BigDecimal.ZERO,
                pools.getRothBalance());
    }

    @Test
    void shouldAggregateMixedPortfolio() {

        AccountPortfolio portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("600000")));

        portfolio.addAccount(
                new Traditional401K(
                        "401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("900000")));

        portfolio.addAccount(
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("150000")));

        portfolio.addAccount(
                new CheckingAccount(
                        "Checking",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000")));

        portfolio.addAccount(
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        ProjectedAssetPools pools =
                builder.build(portfolio);

        assertEquals(
                new BigDecimal("1500000"),
                pools.getTaxDeferredBalance());

        assertEquals(
                new BigDecimal("200000"),
                pools.getTaxableBalance());

        assertEquals(
                new BigDecimal("300000"),
                pools.getRothBalance());

        assertEquals(
                new BigDecimal("2000000"),
                pools.getTotalBalance());
    }
}