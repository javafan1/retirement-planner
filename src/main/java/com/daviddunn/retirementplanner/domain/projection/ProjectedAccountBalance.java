package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedAccountBalance {

    private final Account account;
    private final BigDecimal balance;

    public ProjectedAccountBalance(
            Account account,
            BigDecimal balance) {

        this.account =
                Objects.requireNonNull(
                        account,
                        "Account is required.");

        this.balance =
                Objects.requireNonNull(
                        balance,
                        "Balance is required.");

        if (balance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Projected account balance cannot be negative.");
        }
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}