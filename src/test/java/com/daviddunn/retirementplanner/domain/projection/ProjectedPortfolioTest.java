package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.Traditional401K;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectedPortfolioTest {

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

        portfolio.addAccount(
                new TraditionalIRA(
                        "Spouse IRA",
                        AccountOwnership.SPOUSE,
                        new BigDecimal("700000")));

        portfolio.addAccount(
                new Traditional401K(
                        "Primary 401(k)",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("400000")));

        portfolio.addAccount(
                new BrokerageAccount(
                        "Joint Brokerage",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("300000")));

        projectedPortfolio =
                ProjectedPortfolio.from(
                        portfolio);
    }

    @Test
    void createsSnapshotFromAccountPortfolio() {

        assertEquals(
                5,
                projectedPortfolio
                        .getAccountBalances()
                        .size());
    }

    @Test
    void calculatesTotalProjectedBalance() {

        /*
         * $1,000,000 Traditional IRA
         *    500,000 Roth IRA
         *    700,000 Spouse IRA
         *    400,000 401(k)
         *    300,000 Brokerage
         * ----------
         * $2,900,000
         */

        assertEquals(
                0,
                new BigDecimal("2900000")
                        .compareTo(
                                projectedPortfolio
                                        .getTotalBalance()));
    }

    @Test
    void calculatesPrimaryIraRmdBalance() {

        BigDecimal balance =
                projectedPortfolio.getBalance(
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.IRA);

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(balance));
    }

    @Test
    void calculatesSpouseIraRmdBalanceSeparately() {

        BigDecimal balance =
                projectedPortfolio.getBalance(
                        AccountOwnership.SPOUSE,
                        RmdAccountCategory.IRA);

        assertEquals(
                0,
                new BigDecimal("700000")
                        .compareTo(balance));
    }

    @Test
    void calculatesPrimary401kBalanceSeparately() {

        BigDecimal balance =
                projectedPortfolio.getBalance(
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.TRADITIONAL_401K);

        assertEquals(
                0,
                new BigDecimal("400000")
                        .compareTo(balance));
    }

    @Test
    void rothAndBrokerageAreExcludedFromIraRmdBalance() {

        BigDecimal balance =
                projectedPortfolio.getBalance(
                        AccountOwnership.PRIMARY,
                        RmdAccountCategory.IRA);

        /*
         * The $500,000 Roth IRA and
         * $300,000 brokerage account must
         * not be included.
         */

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(balance));
    }

    @Test
    void createsNewSnapshotWithUpdatedBalance() {

        var account =
                portfolio.getAccounts().get(0);

        ProjectedPortfolio updated =
                projectedPortfolio.withBalance(
                        account,
                        new BigDecimal("900000"));

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                projectedPortfolio
                                        .getBalance(account)));

        assertEquals(
                0,
                new BigDecimal("900000")
                        .compareTo(
                                updated.getBalance(account)));
    }

    @Test
    void updatingProjectedBalanceDoesNotModifyActualAccount() {

        var account =
                portfolio.getAccounts().get(0);

        projectedPortfolio.withBalance(
                account,
                new BigDecimal("900000"));

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                account.getCurrentBalance()));
    }

    @Test
    void rejectsNegativeProjectedBalance() {

        var account =
                portfolio.getAccounts().get(0);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        projectedPortfolio.withBalance(
                                account,
                                new BigDecimal("-1")));
    }


    @Test
    void appliesInvestmentGrowthToEachAccount() {

        ProjectedPortfolio grown =
                projectedPortfolio.applyGrowth(
                        new BigDecimal("0.06"));

        /*
         * Primary IRA:
         *
         * $1,000,000 * 1.06
         * = $1,060,000
         */

        var primaryIra =
                portfolio.getAccounts().get(0);

        assertEquals(
                0,
                new BigDecimal("1060000.00")
                        .compareTo(
                                grown.getBalance(
                                        primaryIra)));

        /*
         * Original snapshot remains unchanged.
         */

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                projectedPortfolio
                                        .getBalance(
                                                primaryIra)));

        /*
         * Actual Account also remains unchanged.
         */

        assertEquals(
                0,
                new BigDecimal("1000000")
                        .compareTo(
                                primaryIra
                                        .getCurrentBalance()));
    }

    @Test
    void investmentGrowthChangesTotalPortfolioBalance() {

        ProjectedPortfolio grown =
                projectedPortfolio.applyGrowth(
                        new BigDecimal("0.06"));

        /*
         * Beginning portfolio = $2,900,000
         *
         * 6% growth = $174,000
         *
         * Ending after growth = $3,074,000
         */

        assertEquals(
                0,
                new BigDecimal("3074000.00")
                        .compareTo(
                                grown.getTotalBalance()));
    }

    @Test
    void includesUnallocatedCashInTotalBalance() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(),
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("10000.00")
                        .compareTo(
                                portfolio.getTotalBalance()));
    }

    @Test
    void defaultsUnallocatedCashToZero() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        portfolio.getUnallocatedCash()));
    }

    @Test
    void addsCashToUnallocatedCash() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(),
                        new BigDecimal("5000.00"));

        ProjectedPortfolio updated =
                portfolio.withAdditionalCash(
                        new BigDecimal("9430.89"));

        assertEquals(
                0,
                new BigDecimal("14430.89")
                        .compareTo(
                                updated.getUnallocatedCash()));

        assertEquals(
                0,
                new BigDecimal("14430.89")
                        .compareTo(
                                updated.getTotalBalance()));
    }

    @Test
    void addingCashDoesNotModifyOriginalPortfolio() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(),
                        new BigDecimal("5000.00"));

        ProjectedPortfolio updated =
                portfolio.withAdditionalCash(
                        new BigDecimal("9430.89"));

        assertEquals(
                0,
                new BigDecimal("5000.00")
                        .compareTo(
                                portfolio.getUnallocatedCash()));

        assertEquals(
                0,
                new BigDecimal("14430.89")
                        .compareTo(
                                updated.getUnallocatedCash()));
    }

    @Test
    void rejectsNegativeAdditionalCash() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> portfolio.withAdditionalCash(
                        new BigDecimal("-1.00")));
    }

    @Test
    void withdrawsFromProjectedAccount() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("100000.00"))));

        ProjectedPortfolio updated =
                portfolio.withWithdrawal(
                        ira,
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("90000.00")
                        .compareTo(
                                updated.getBalance(ira)));
    }

    @Test
    void withdrawalDoesNotModifyOriginalPortfolio() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("100000.00"))));

        ProjectedPortfolio updated =
                portfolio.withWithdrawal(
                        ira,
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("100000.00")
                        .compareTo(
                                portfolio.getBalance(ira)));

        assertEquals(
                0,
                new BigDecimal("90000.00")
                        .compareTo(
                                updated.getBalance(ira)));
    }

    @Test
    void rejectsWithdrawalGreaterThanAccountBalance() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("100000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("100000.00"))));

        assertThrows(
                IllegalArgumentException.class,
                () -> portfolio.withWithdrawal(
                        ira,
                        new BigDecimal("100000.01")));
    }
    @Test
    void allocatesGrowthProportionallyAcrossAccounts() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("75000.00"));

        RothIRA roth =
                new RothIRA(
                        "Roth IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("25000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("75000.00")),
                                new ProjectedAccountBalance(
                                        roth,
                                        new BigDecimal("25000.00"))));

        ProjectedPortfolio updated =
                portfolio.withGrowth(
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("82500.00")
                        .compareTo(
                                updated.getBalance(ira)));

        assertEquals(
                0,
                new BigDecimal("27500.00")
                        .compareTo(
                                updated.getBalance(roth)));

        assertEquals(
                0,
                new BigDecimal("110000.00")
                        .compareTo(
                                updated.getTotalBalance()));
    }

    @Test
    void allocatesTotalInvestmentGrowthBetweenAccountsAndRetainedRmdAssets() {

        TraditionalIRA ira =
                new TraditionalIRA(
                        "Traditional IRA",
                        AccountOwnership.PRIMARY,
                        new BigDecimal("1000000.00"));

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(
                                new ProjectedAccountBalance(
                                        ira,
                                        new BigDecimal("1000000.00"))),
                        new BigDecimal("100000.00"));

        /*
         * $1,000,000 invested account balance
         * +  100,000 retained excess-RMD assets
         * --------------------------------------
         * $1,100,000 total investable assets
         *
         * At 10%, ProjectionEngine supplies one total
         * growth amount of $110,000, allocated as
         * $100,000 to the account and $10,000 to the
         * retained RMD assets.
         */
        ProjectedPortfolio updated =
                portfolio.withGrowth(
                        new BigDecimal("100000.00"),
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("1210000.00")
                        .compareTo(
                                updated.getTotalBalance()));

        assertEquals(
                0,
                new BigDecimal("110000.00")
                        .compareTo(
                                updated.getUnallocatedCash()));

        assertEquals(
                0,
                new BigDecimal("1100000.00")
                        .compareTo(
                                updated.getBalance(ira)));
    }

    @Test
    void growsRetainedRmdAssetsWhenThereAreNoProjectedAccounts() {

        ProjectedPortfolio portfolio =
                new ProjectedPortfolio(
                        List.of(),
                        new BigDecimal("100000.00"));

        ProjectedPortfolio updated =
                portfolio.withGrowth(
                        new BigDecimal("10000.00"));

        assertEquals(
                0,
                new BigDecimal("110000.00")
                        .compareTo(
                                updated.getUnallocatedCash()));

        assertEquals(
                0,
                new BigDecimal("110000.00")
                        .compareTo(
                                updated.getTotalBalance()));
    }
}
