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

    public ProjectedAssetPools convertTaxDeferredToRoth(
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
                    "Insufficient tax-deferred assets to perform Roth conversion.");
        }

        return new ProjectedAssetPools(
                taxableBalance,
                taxDeferredBalance.subtract(amount),
                rothBalance.add(amount));
    }

    public ProjectedAssetPools withdrawFromTaxable(
            BigDecimal amount) {

        Objects.requireNonNull(
                amount,
                "Withdrawal amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be negative.");
        }

        if (amount.compareTo(taxableBalance) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient taxable assets.");
        }

        return new ProjectedAssetPools(
                taxableBalance.subtract(amount),
                taxDeferredBalance,
                rothBalance);
    }

    public static ProjectedAssetPools from(
            ProjectedPortfolio portfolio) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        BigDecimal taxable =
                BigDecimal.ZERO;

        BigDecimal taxDeferred =
                BigDecimal.ZERO;

        BigDecimal roth =
                BigDecimal.ZERO;

        for (ProjectedAccountBalance projected :
                portfolio.getAccountBalances()) {

            BigDecimal balance =
                    projected.getBalance();

            switch (projected.getAccount()
                    .getType()
                    .getProjectionAssetType()) {

                case TAXABLE ->
                        taxable = taxable.add(balance);

                case TAX_DEFERRED ->
                        taxDeferred = taxDeferred.add(balance);

                case ROTH ->
                        roth = roth.add(balance);
            }
        }

        return new ProjectedAssetPools(
                taxable,
                taxDeferred,
                roth);
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