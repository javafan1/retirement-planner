package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.RolloverIRA;

import com.daviddunn.retirementplanner.domain.financial.Traditional401K;


import com.daviddunn.retirementplanner.domain.rmd.AccountRmd;

import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult;


import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.withdrawal.TaxableFirstWithdrawalStrategy;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalBreakdown;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedWithdrawalAllocatorTest {

    @Test
    void appliesAccountSpecificRmd() {

        Traditional401K account =
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        account,
                                        new BigDecimal("500000.00"))));

        AccountRmd accountRmd =
                new AccountRmd(
                        account,
                        new BigDecimal("20000.00"));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyAccountRmds(
                        portfolio,
                        List.of(accountRmd));

        assertEquals(
                0,
                new BigDecimal("480000.00")
                        .compareTo(
                                updated.getBalance(account)));
    }

    @Test
    void retainedNonQualifiedAssetsFundAdditionalWithdrawalBeforeAccounts() {

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        brokerage,
                                        new BigDecimal("100000.00"))),
                        new BigDecimal("20000.00"));

        ProjectedWithdrawalAllocation allocation =
                new ProjectedWithdrawalAllocator()
                        .allocateAdditionalWithdrawal(
                                portfolio,
                                new BigDecimal("30000.00"),
                                new TaxableFirstWithdrawalStrategy());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        allocation.getPortfolio()
                                .getRetainedNonQualifiedAssets()));
        assertEquals(
                0,
                new BigDecimal("90000.00").compareTo(
                        allocation.getPortfolio().getBalance(brokerage)));
        assertEquals(
                0,
                new BigDecimal("20000.00").compareTo(
                        allocation.getWithdrawalBreakdown()
                                .getCashWithdrawal()));
        assertEquals(
                0,
                new BigDecimal("10000.00").compareTo(
                        allocation.getWithdrawalBreakdown()
                                .getTaxableWithdrawal()));
    }

    @Test
    void applyingAccountRmdDoesNotModifyOriginalPortfolio() {

        Traditional401K account =
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("500000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        account,
                                        new BigDecimal("500000.00"))));

        AccountRmd accountRmd =
                new AccountRmd(
                        account,
                        new BigDecimal("20000.00"));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyAccountRmds(
                        portfolio,
                        List.of(accountRmd));

        assertEquals(
                0,
                new BigDecimal("500000.00")
                        .compareTo(
                                portfolio.getBalance(account)));

        assertEquals(
                0,
                new BigDecimal("480000.00")
                        .compareTo(
                                updated.getBalance(account)));
    }

    @Test
    void appliesIraRmdAcrossEligibleIrasInPortfolioOrder() {

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("20000.00"));

        RolloverIRA rolloverIra =
                new RolloverIRA(
                        "Rollover IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("80000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("20000.00")),
                                new ProjectedAccountBalance(
                                        rolloverIra,
                                        new BigDecimal("80000.00"))));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        new BigDecimal("30000.00"));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        updated.getBalance(
                                traditionalIra)));

        assertEquals(
                0,
                new BigDecimal("70000.00")
                        .compareTo(
                                updated.getBalance(
                                        rolloverIra)));
    }

    @Test
    void primaryIraRmdDoesNotWithdrawFromSpouseIra() {

        TraditionalIRA primaryIra =
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000.00"));

        TraditionalIRA spouseIra =
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        primaryIra,
                                        new BigDecimal("50000.00")),
                                new ProjectedAccountBalance(
                                        spouseIra,
                                        new BigDecimal("100000.00"))));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        new BigDecimal("20000.00"));

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                updated.getBalance(
                                        primaryIra)));

        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(
                                updated.getBalance(
                                        spouseIra)));
    }

    @Test
    void iraRmdDoesNotWithdrawFromRothIra() {

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000.00"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("50000.00")),
                                new ProjectedAccountBalance(
                                        rothIra,
                                        new BigDecimal("100000.00"))));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyIraRmd(
                        portfolio,
                        AccountOwnership.PRIMARY,
                        new BigDecimal("20000.00"));

        assertEquals(
                0,
                new BigDecimal("30000.00")
                        .compareTo(
                                updated.getBalance(
                                        traditionalIra)));

        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(
                                updated.getBalance(
                                        rothIra)));
    }

    @Test
    void appliesCompleteHouseholdRmdResult() {

        TraditionalIRA primaryIra =
                new TraditionalIRA(
                        "Primary IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        Traditional401K primary401k =
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("200000.00"));

        TraditionalIRA spouseIra =
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("150000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        primaryIra,
                                        new BigDecimal("100000.00")),
                                new ProjectedAccountBalance(
                                        primary401k,
                                        new BigDecimal("200000.00")),
                                new ProjectedAccountBalance(
                                        spouseIra,
                                        new BigDecimal("150000.00"))));

        /*
         * Primary:
         *
         * IRA RMD     = $10,000
         * 401(k) RMD  = $20,000
         * Total       = $30,000
         */
        OwnerRmdResult primaryRmd =
                new OwnerRmdResult(
                        new BigDecimal("10000.00"),
                        List.of(
                                new AccountRmd(
                                        primary401k,
                                        new BigDecimal("20000.00"))),
                        List.of());

        /*
         * Spouse:
         *
         * IRA RMD = $15,000
         */
        OwnerRmdResult spouseRmd =
                new OwnerRmdResult(
                        new BigDecimal("15000.00"),
                        List.of(),
                        List.of());

        HouseholdRmdResult householdRmd =
                new HouseholdRmdResult(
                        primaryRmd,
                        spouseRmd);

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyHouseholdRmds(
                        portfolio,
                        householdRmd);

        /*
         * Primary IRA:
         *
         * $100,000 - $10,000
         * = $90,000
         */
        assertEquals(
                0,
                new BigDecimal("90000.00")
                        .compareTo(
                                updated.getBalance(
                                        primaryIra)));

        /*
         * Primary 401(k):
         *
         * $200,000 - $20,000
         * = $180,000
         */
        assertEquals(
                0,
                new BigDecimal("180000.00")
                        .compareTo(
                                updated.getBalance(
                                        primary401k)));

        /*
         * Spouse IRA:
         *
         * $150,000 - $15,000
         * = $135,000
         */
        assertEquals(
                0,
                new BigDecimal("135000.00")
                        .compareTo(
                                updated.getBalance(
                                        spouseIra)));

        /*
         * Household RMD:
         *
         * $10,000 + $20,000 + $15,000
         * = $45,000
         *
         * Projected account balances:
         *
         * $90,000 + $180,000 + $135,000
         * = $405,000
         */
        assertEquals(
                0,
                new BigDecimal("405000.00")
                        .compareTo(
                                updated.getTotalBalance()));

        /*
         * The original projected portfolio must
         * remain unchanged.
         */
        assertEquals(
                0,
                new BigDecimal("450000.00")
                        .compareTo(
                                portfolio.getTotalBalance()));
    }

    @Test
    void appliesAdditionalWithdrawalUsingWithdrawalStrategy() {

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("80000"));

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("20000"));

        /*
         * Deliberately put Roth FIRST.
         *
         * Portfolio order therefore conflicts with
         * our withdrawal strategy.
         */
        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        rothIra,
                                        new BigDecimal("80000")),
                                new ProjectedAccountBalance(
                                        traditionalIra,
                                        new BigDecimal("20000"))));

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedPortfolio updated =
                allocator.applyAdditionalWithdrawal(
                        portfolio,
                        new BigDecimal("30000"),
                        new TaxableFirstWithdrawalStrategy());

        /*
         * Strategy says TAX_DEFERRED comes before
         * ROTH, even though Roth appeared first in
         * the portfolio.
         *
         * Traditional IRA supplies its entire
         * $20,000 balance.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        updated.getBalance(
                                traditionalIra)));

        /*
         * Remaining $10,000 comes from Roth.
         *
         * $80,000 - $10,000 = $70,000
         */
        assertEquals(
                0,
                new BigDecimal("70000")
                        .compareTo(
                                updated.getBalance(
                                        rothIra)));

        assertEquals(
                0,
                new BigDecimal("70000")
                        .compareTo(
                                updated.getTotalBalance()));

        /*
         * Original portfolio remains unchanged.
         */
        assertEquals(
                0,
                new BigDecimal("100000")
                        .compareTo(
                                portfolio.getTotalBalance()));
    }

    @Test
    void additionalWithdrawalReportsBreakdownByTaxTreatment() {

        BrokerageAccount brokerage =
                new BrokerageAccount(
                        "Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("20000"));

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000"));

        RothIRA rothIra =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("50000"));

        AccountPortfolio accountPortfolio =
                new AccountPortfolio();

        /*
         * Deliberately put the IRA before the brokerage
         * account so portfolio order cannot accidentally
         * make the test pass.
         */
        accountPortfolio.addAccount(
                traditionalIra);

        accountPortfolio.addAccount(
                brokerage);

        accountPortfolio.addAccount(
                rothIra);

        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        accountPortfolio);

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        WithdrawalStrategy strategy =
                new TaxableFirstWithdrawalStrategy();

        /*
         * $30,000 required:
         *
         * Brokerage supplies first $20,000.
         * Traditional IRA supplies remaining $10,000.
         * Roth is untouched.
         */
        ProjectedWithdrawalAllocation allocation =
                allocator.allocateAdditionalWithdrawal(
                        projectedPortfolio,
                        new BigDecimal("30000"),
                        strategy);

        WithdrawalBreakdown breakdown =
                allocation.getWithdrawalBreakdown();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        breakdown.getCashWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("20000")
                        .compareTo(
                                breakdown.getTaxableWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("10000")
                        .compareTo(
                                breakdown.getTaxDeferredWithdrawal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        breakdown.getRothWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("30000")
                        .compareTo(
                                breakdown.getTotalWithdrawal()));

        /*
         * Verify that the returned portfolio agrees
         * with the reported breakdown.
         */
        ProjectedPortfolio endingPortfolio =
                allocation.getPortfolio();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        endingPortfolio.getBalance(
                                brokerage)));

        assertEquals(
                0,
                new BigDecimal("40000")
                        .compareTo(
                                endingPortfolio.getBalance(
                                        traditionalIra)));

        assertEquals(
                0,
                new BigDecimal("50000")
                        .compareTo(
                                endingPortfolio.getBalance(
                                        rothIra)));
    }

    @Test
    void householdRmdReportsTaxDeferredWithdrawal() {

        TraditionalIRA traditionalIra =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000"));

        AccountPortfolio accountPortfolio =
                new AccountPortfolio();

        accountPortfolio.addAccount(
                traditionalIra);

        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        accountPortfolio);

        /*
         * Primary owner has a $10,000 IRA RMD.
         */
        OwnerRmdResult primaryRmd =
                new OwnerRmdResult(
                        new BigDecimal("10000"),
                        List.of(),
                        List.of());

        OwnerRmdResult spouseRmd =
                OwnerRmdResult.zero();

        HouseholdRmdResult householdRmd =
                new HouseholdRmdResult(
                        primaryRmd,
                        spouseRmd);

        ProjectedWithdrawalAllocator allocator =
                new ProjectedWithdrawalAllocator();

        ProjectedWithdrawalAllocation allocation =
                allocator.allocateHouseholdRmds(
                        projectedPortfolio,
                        householdRmd);

        WithdrawalBreakdown breakdown =
                allocation.getWithdrawalBreakdown();

        /*
         * The entire $10,000 RMD came from
         * a tax-deferred Traditional IRA.
         */
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        breakdown.getCashWithdrawal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        breakdown.getTaxableWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("10000")
                        .compareTo(
                                breakdown
                                        .getTaxDeferredWithdrawal()));

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        breakdown.getRothWithdrawal()));

        assertEquals(
                0,
                new BigDecimal("10000")
                        .compareTo(
                                breakdown.getTotalWithdrawal()));

        /*
         * The actual projected IRA balance must
         * agree with the reported withdrawal.
         */
        assertEquals(
                0,
                new BigDecimal("90000")
                        .compareTo(
                                allocation
                                        .getPortfolio()
                                        .getBalance(
                                                traditionalIra)));
    }
}
