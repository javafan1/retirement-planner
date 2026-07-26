package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedAccountSnapshot {

    private final Account account;
    private final BigDecimal endingBalance;

    public ProjectedAccountSnapshot(
            Account account,
            BigDecimal endingBalance) {

        this.account =
                Objects.requireNonNull(
                        account,
                        "Account is required.");

        this.endingBalance =
                Objects.requireNonNull(
                        endingBalance,
                        "Ending balance is required.");

        if (endingBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Ending balance cannot be negative.");
        }
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getEndingBalance() {
        return endingBalance;
    }
}