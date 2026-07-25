package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;

import java.math.BigDecimal;
import java.util.Objects;

public final class AccountRmd {

    private final Account account;
    private final BigDecimal amount;

    public AccountRmd(
            Account account,
            BigDecimal amount) {

        this.account =
                Objects.requireNonNull(
                        account,
                        "Account is required.");

        this.amount =
                Objects.requireNonNull(
                        amount,
                        "RMD amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "RMD amount cannot be negative.");
        }
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String toString() {

        return "AccountRmd{" +
                "account=" + account.getName() +
                ", amount=" + amount +
                '}';
    }
}