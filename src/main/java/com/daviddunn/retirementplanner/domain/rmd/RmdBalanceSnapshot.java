package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
/*
we can move to the reason we made this change: preserving the prior December 31 account balances for RMD calculations.

One subtlety matters here. For a projection starting partway through 2026, the balances entered into the plan are effectively our starting/current balances; they are not necessarily the December 31, 2025 balances needed to calculate a 2026 RMD. We should not silently pretend they are.

For subsequent projection years, however, the previous year's ending ProjectedPortfolio is our modeled December 31 balance.
 */
public final class RmdBalanceSnapshot {

    private final LocalDate snapshotDate;
    private final List<ProjectedAccountBalance> accountBalances;

    public RmdBalanceSnapshot(
            LocalDate snapshotDate,
            List<ProjectedAccountBalance> accountBalances) {

        this.snapshotDate =
                Objects.requireNonNull(
                        snapshotDate,
                        "Snapshot date is required.");

        this.accountBalances =
                List.copyOf(
                        Objects.requireNonNull(
                                accountBalances,
                                "Account balances are required."));
    }

    public static RmdBalanceSnapshot from(
            LocalDate snapshotDate,
            ProjectedPortfolio portfolio) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        return new RmdBalanceSnapshot(
                snapshotDate,
                portfolio.getAccountBalances());
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public List<ProjectedAccountBalance> getAccountBalances() {
        return accountBalances;
    }

    public BigDecimal getBalance(
            Account account) {

        Objects.requireNonNull(
                account,
                "Account is required.");

        return accountBalances
                .stream()
                .filter(projected ->
                        projected.getAccount() == account)
                .map(ProjectedAccountBalance::getBalance)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Account is not part of RMD balance snapshot."));
    }

    public BigDecimal getTotalBalance() {

        return accountBalances
                .stream()
                .map(ProjectedAccountBalance::getBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }
}