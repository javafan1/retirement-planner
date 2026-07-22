package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public class WithdrawalResult {

    private final BigDecimal totalWithdrawal;

    public WithdrawalResult(BigDecimal totalWithdrawal) {
        this.totalWithdrawal =
                Objects.requireNonNull(totalWithdrawal);
    }

    public BigDecimal getTotalWithdrawal() {
        return totalWithdrawal;
    }
}