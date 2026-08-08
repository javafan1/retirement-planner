package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedPersonAssetPools {

    private final BigDecimal taxableBalance;
    private final BigDecimal taxDeferredBalance;
    private final BigDecimal rothBalance;

    public ProjectedPersonAssetPools(
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

    public ProjectedPersonAssetPools convertTaxDeferredToRoth(
            BigDecimal amount) {

        Objects.requireNonNull(
                amount,
                "Conversion amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Conversion amount cannot be negative.");
        }

        if (amount.compareTo(taxDeferredBalance) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient tax-deferred assets.");
        }

        return new ProjectedPersonAssetPools(
                taxableBalance,
                taxDeferredBalance.subtract(amount),
                rothBalance.add(amount));
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
    public boolean equals(Object o) {

        if (this == o) return true;
        if (!(o instanceof ProjectedPersonAssetPools other)) return false;

        return taxableBalance.equals(other.taxableBalance)
                && taxDeferredBalance.equals(other.taxDeferredBalance)
                && rothBalance.equals(other.rothBalance);
    }

    @Override
    public int hashCode() {

        return Objects.hash(
                taxableBalance,
                taxDeferredBalance,
                rothBalance);
    }

    @Override
    public String toString() {

        return "ProjectedPersonAssetPools{" +
                "taxableBalance=" + taxableBalance +
                ", taxDeferredBalance=" + taxDeferredBalance +
                ", rothBalance=" + rothBalance +
                '}';
    }
}