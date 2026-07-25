package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.RolloverIRA;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RmdBalanceAggregatorTest {

    private RmdBalanceAggregator aggregator;
    private AccountPortfolio portfolio;

    @BeforeEach
    void setUp() {

        aggregator =
                new RmdBalanceAggregator();

        portfolio =
                new AccountPortfolio();

        portfolio.addAccount(
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000")));

        portfolio.addAccount(
                new RolloverIRA(
                        "Primary Rollover",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("400000")));

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

        portfolio.addAccount(
                new InheritedTraditionalIRA(
                        "Inherited IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("34000"),
                        new InheritedAccountInformation(
                                LocalDate.of(1960, 1, 1),
                                LocalDate.of(2025, 1, 1),
                                BeneficiaryRelationship.SIBLING)));
    }

    @Test
    void aggregatesPrimaryIraBalances() {

        BigDecimal balance =
                aggregator.getBalance(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.IRA);

        /*
         * Primary Traditional IRA    $500,000
         * Primary Rollover IRA       $300,000
         *                            --------
         * IRA RMD balance            $800,000
         *
         * Roth IRA and inherited IRA
         * must NOT be included.
         */

        assertEquals(
                0,
                new BigDecimal("800000")
                        .compareTo(balance));
    }

    @Test
    void keepsSpouseIraBalanceSeparate() {

        BigDecimal balance =
                aggregator.getBalance(
                        portfolio,
                        AccountOwnership.SPOUSE,
                        RmdAccountCategory.IRA);

        assertEquals(
                0,
                new BigDecimal("600000")
                        .compareTo(balance));
    }

    @Test
    void identifiesPrimary401kBalanceSeparately() {

        BigDecimal balance =
                aggregator.getBalance(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.TRADITIONAL_401K);

        assertEquals(
                0,
                new BigDecimal("400000")
                        .compareTo(balance));
    }

    @Test
    void spouse401kBalanceIsZeroWhenNoneExists() {

        BigDecimal balance =
                aggregator.getBalance(
                        portfolio,
                        AccountOwnership.SPOUSE,
                        RmdAccountCategory.TRADITIONAL_401K);

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance));
    }

    @Test
    void inheritedIraIsNotIncludedInOwnerIraBalance() {

        BigDecimal balance =
                aggregator.getBalance(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.IRA);

        /*
         * The $34,000 inherited IRA is intentionally
         * excluded from the owner's normal IRA RMD pool.
         */

        assertEquals(
                0,
                new BigDecimal("800000")
                        .compareTo(balance));
    }
}