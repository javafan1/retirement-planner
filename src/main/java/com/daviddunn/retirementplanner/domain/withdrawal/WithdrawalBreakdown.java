package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public final class WithdrawalBreakdown {

    private final BigDecimal cashWithdrawal;
    private final BigDecimal taxableWithdrawal;
    private final BigDecimal taxDeferredWithdrawal;
    private final BigDecimal rothWithdrawal;

    public WithdrawalBreakdown(
            BigDecimal cashWithdrawal,
            BigDecimal taxableWithdrawal,
            BigDecimal taxDeferredWithdrawal,
            BigDecimal rothWithdrawal) {

        this.cashWithdrawal =
                requireNonNegative(
                        cashWithdrawal,
                        "Cash withdrawal");

        this.taxableWithdrawal =
                requireNonNegative(
                        taxableWithdrawal,
                        "Taxable withdrawal");

        this.taxDeferredWithdrawal =
                requireNonNegative(
                        taxDeferredWithdrawal,
                        "Tax-deferred withdrawal");

        this.rothWithdrawal =
                requireNonNegative(
                        rothWithdrawal,
                        "Roth withdrawal");
    }

    public BigDecimal getCashWithdrawal() {
        return cashWithdrawal;
    }

    public BigDecimal getTaxableWithdrawal() {
        return taxableWithdrawal;
    }

    public BigDecimal getTaxDeferredWithdrawal() {
        return taxDeferredWithdrawal;
    }

    public BigDecimal getRothWithdrawal() {
        return rothWithdrawal;
    }

    public BigDecimal getTotalWithdrawal() {

        return cashWithdrawal
                .add(taxableWithdrawal)
                .add(taxDeferredWithdrawal)
                .add(rothWithdrawal);
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(
                amount,
                description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }

    public WithdrawalBreakdown plus(
            WithdrawalBreakdown other) {

        Objects.requireNonNull(
                other,
                "Withdrawal breakdown is required.");

        return new WithdrawalBreakdown(
                cashWithdrawal.add(
                        other.cashWithdrawal),
                taxableWithdrawal.add(
                        other.taxableWithdrawal),
                taxDeferredWithdrawal.add(
                        other.taxDeferredWithdrawal),
                rothWithdrawal.add(
                        other.rothWithdrawal));
    }

    public static WithdrawalBreakdown zero() {

        return new WithdrawalBreakdown(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}