package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public final class WithdrawalDisposition {

    private final BigDecimal spent;
    private final BigDecimal reinvested;

    public WithdrawalDisposition(
            BigDecimal spent,
            BigDecimal reinvested) {

        this.spent =
                Objects.requireNonNull(
                        spent,
                        "Spent amount is required.");

        this.reinvested =
                Objects.requireNonNull(
                        reinvested,
                        "Reinvested amount is required.");

        if (spent.signum() < 0) {
            throw new IllegalArgumentException(
                    "Spent amount cannot be negative.");
        }

        if (reinvested.signum() < 0) {
            throw new IllegalArgumentException(
                    "Reinvested amount cannot be negative.");
        }
    }

    public BigDecimal getSpent() {
        return spent;
    }

    public BigDecimal getReinvested() {
        return reinvested;
    }

    public BigDecimal getTotalDistributed() {
        return spent.add(reinvested);
    }
}