package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public class WithdrawalResult {

    private final BigDecimal cashFlowNeed;
    private final BigDecimal requiredMinimumDistribution;
    private final BigDecimal totalWithdrawal;

    public WithdrawalResult(
            BigDecimal totalWithdrawal) {

        this(
                totalWithdrawal,
                BigDecimal.ZERO,
                totalWithdrawal);
    }

    public WithdrawalResult(
            BigDecimal cashFlowNeed,
            BigDecimal requiredMinimumDistribution,
            BigDecimal totalWithdrawal) {

        this.cashFlowNeed =
                Objects.requireNonNull(
                        cashFlowNeed,
                        "Cash flow need is required.");

        this.requiredMinimumDistribution =
                Objects.requireNonNull(
                        requiredMinimumDistribution,
                        "Required minimum distribution is required.");

        this.totalWithdrawal =
                Objects.requireNonNull(
                        totalWithdrawal,
                        "Total withdrawal is required.");

        if (cashFlowNeed.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cash flow need cannot be negative.");
        }

        if (requiredMinimumDistribution.signum() < 0) {
            throw new IllegalArgumentException(
                    "Required minimum distribution cannot be negative.");
        }

        if (totalWithdrawal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Total withdrawal cannot be negative.");
        }
    }

    public BigDecimal getCashFlowNeed() {
        return cashFlowNeed;
    }

    public BigDecimal getRequiredMinimumDistribution() {
        return requiredMinimumDistribution;
    }

    public BigDecimal getTotalWithdrawal() {
        return totalWithdrawal;
    }

    public BigDecimal getExcessRmd() {

        return requiredMinimumDistribution
                .subtract(cashFlowNeed)
                .max(BigDecimal.ZERO);
    }

    public WithdrawalDisposition getDisposition() {

        return new WithdrawalDisposition(
                cashFlowNeed.min(totalWithdrawal),
                getExcessRmd());
    }

    public BigDecimal getAdditionalWithdrawalRequired() {

        return cashFlowNeed
                .subtract(requiredMinimumDistribution)
                .max(BigDecimal.ZERO);
    }
}