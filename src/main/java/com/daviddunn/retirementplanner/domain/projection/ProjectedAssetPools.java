package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedAssetPools {

    private final BigDecimal taxableBalance;
    private final BigDecimal taxDeferredBalance;
    private final BigDecimal rothBalance;

    public ProjectedAssetPools(
            BigDecimal taxableBalance,
            BigDecimal taxDeferredBalance,
            BigDecimal rothBalance) {

        this.taxableBalance =
                requireNonNegative(
                        taxableBalance,
                        "Taxable balance");

        this.taxDeferredBalance =
                requireNonNegative(
                        taxDeferredBalance,
                        "Tax-deferred balance");

        this.rothBalance =
                requireNonNegative(
                        rothBalance,
                        "Roth balance");
    }

    public BigDecimal getTaxableBalance() {
        return taxableBalance;
    }

    public BigDecimal getTaxDeferredBalance() {
        return taxDeferredBalance;
    }

    public BigDecimal getRothBalance() {
        return rothBalance;
    }

    public BigDecimal getTotalBalance() {

        return taxableBalance
                .add(taxDeferredBalance)
                .add(rothBalance);
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

    @Override
    public String toString() {

        return "ProjectedAssetPools{" +
                "taxableBalance=" + taxableBalance +
                ", taxDeferredBalance=" + taxDeferredBalance +
                ", rothBalance=" + rothBalance +
                '}';
    }
}