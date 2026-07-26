package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.CheckingAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class TaxDeferredFirstWithdrawalStrategyTest {

    @Test
    void ordersTaxDeferredAccountsFirst() {

        /*
         * Deliberately arrange accounts in an order
         * different from the strategy.
         */
        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        CheckingAccount checking =
                new CheckingAccount(
                        "Checking",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        rothIra,
                                        rothIra.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        brokerage,
                                        brokerage.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        checking,
                                        checking.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        traditionalIra.getCurrentBalance())));

        TaxDeferredFirstWithdrawalStrategy strategy =
                new TaxDeferredFirstWithdrawalStrategy();

        List<ProjectedAccountBalance> ordered =
                strategy.orderAccounts(
                        portfolio);

        /*
         * Expected:
         *
         * TAX_DEFERRED
         * CASH
         * TAXABLE
         * ROTH
         */
        assertSame(
                traditionalIra,
                ordered.get(0).getAccount());

        assertSame(
                checking,
                ordered.get(1).getAccount());

        assertSame(
                brokerage,
                ordered.get(2).getAccount());

        assertSame(
                rothIra,
                ordered.get(3).getAccount());
    }
}