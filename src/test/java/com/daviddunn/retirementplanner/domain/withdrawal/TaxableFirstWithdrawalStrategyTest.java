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

class TaxableFirstWithdrawalStrategyTest {

    @Test
    void ordersAccountsByTaxTreatment() {

        /*
         * Deliberately create the accounts in the
         * WRONG withdrawal order.
         *
         * This proves the strategy is actually
         * reordering them.
         */
        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
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

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        rothIra,
                                        rothIra.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        traditionalIra.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        brokerage,
                                        brokerage.getCurrentBalance()),
                                new ProjectedAccountBalance(
                                        checking,
                                        checking.getCurrentBalance())));

        TaxableFirstWithdrawalStrategy strategy =
                new TaxableFirstWithdrawalStrategy();

        List<ProjectedAccountBalance> ordered =
                strategy.orderAccounts(
                        portfolio);

        /*
         * Expected:
         *
         * CASH
         * TAXABLE
         * TAX_DEFERRED
         * ROTH
         */
        assertSame(
                checking,
                ordered.get(0).getAccount());

        assertSame(
                brokerage,
                ordered.get(1).getAccount());

        assertSame(
                traditionalIra,
                ordered.get(2).getAccount());

        assertSame(
                rothIra,
                ordered.get(3).getAccount());
    }
}