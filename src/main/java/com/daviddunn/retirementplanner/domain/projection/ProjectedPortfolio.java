package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ProjectedPortfolio {

    private final List<ProjectedAccountBalance> accountBalances;
    private final BigDecimal retainedNonQualifiedAssets;

    public ProjectedPortfolio(
            List<ProjectedAccountBalance> accountBalances) {

        this(
                accountBalances,
                BigDecimal.ZERO);
    }

    public ProjectedPortfolio(
            List<ProjectedAccountBalance> accountBalances,
            BigDecimal retainedNonQualifiedAssets) {

        Objects.requireNonNull(
                accountBalances,
                "Projected account balances are required.");

        this.retainedNonQualifiedAssets =
                Objects.requireNonNull(
                        retainedNonQualifiedAssets,
                        "Retained non-qualified assets are required.");

        if (retainedNonQualifiedAssets.signum() < 0) {
            throw new IllegalArgumentException(
                    "Retained non-qualified assets cannot be negative.");
        }

        this.accountBalances =
                List.copyOf(accountBalances);
    }

    /*
     * Creates the initial projection snapshot from
     * the user's actual account portfolio.
     *
     * No Account objects are modified.
     */
    public static ProjectedPortfolio from(
            AccountPortfolio portfolio) {

        Objects.requireNonNull(
                portfolio,
                "Account portfolio is required.");

        List<ProjectedAccountBalance> balances =
                new ArrayList<>();

        for (Account account :
                portfolio.getAccounts()) {

            balances.add(
                    new ProjectedAccountBalance(
                            account,
                            account.getCurrentBalance()));
        }

        return new ProjectedPortfolio(
                balances);
    }

    public BigDecimal getUnallocatedCash() {
        return getRetainedNonQualifiedAssets();
    }

    public BigDecimal getRetainedNonQualifiedAssets() {
        return retainedNonQualifiedAssets;
    }

    public List<ProjectedAccountBalance> getAccountBalances() {
        return accountBalances;
    }

    public BigDecimal getTotalBalance() {

        return getAccountBalanceTotal().add(
                retainedNonQualifiedAssets);
    }

    public BigDecimal getAccountBalanceTotal() {

        return accountBalances
                .stream()
                .map(ProjectedAccountBalance::getBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    public BigDecimal getBalance(
            AccountOwnership ownership,
            RmdAccountCategory category) {

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        Objects.requireNonNull(
                category,
                "RMD account category is required.");

        return accountBalances
                .stream()
                .filter(projected ->
                        projected
                                .getAccount()
                                .getOwnership()
                                == ownership)
                .filter(projected ->
                        projected
                                .getAccount()
                                .getType()
                                .isSubjectToOwnerRmd())
                .filter(projected ->
                        projected
                                .getAccount()
                                .getType()
                                .getRmdAccountCategory()
                                == category)
                .map(ProjectedAccountBalance::getBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    public ProjectedPortfolio withBalance(
            Account account,
            BigDecimal newBalance) {

        Objects.requireNonNull(
                account,
                "Account is required.");

        Objects.requireNonNull(
                newBalance,
                "New balance is required.");

        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Projected account balance cannot be negative.");
        }

        List<ProjectedAccountBalance> updatedBalances =
                accountBalances
                        .stream()
                        .map(projected -> {

                            if (projected.getAccount() == account) {

                                return new ProjectedAccountBalance(
                                        account,
                                        newBalance);
                            }

                            return projected;
                        })
                        .toList();

        return new ProjectedPortfolio(
                updatedBalances,
                retainedNonQualifiedAssets);
    }

    public BigDecimal getBalance(
            Account account) {

        Objects.requireNonNull(
                account,
                "Account is required.");

        return accountBalances
                .stream()
                .filter(projected ->
                        projected.getAccount() == account)
                .map(ProjectedAccountBalance::getBalance)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Account is not part of projected portfolio."));
    }

    public ProjectedPortfolio applyGrowth(
            BigDecimal annualReturnRate) {

        Objects.requireNonNull(
                annualReturnRate,
                "Annual return rate is required.");

        List<ProjectedAccountBalance> updatedBalances =
                accountBalances
                        .stream()
                        .map(projected -> {

                            BigDecimal currentBalance =
                                    projected.getBalance();

                            BigDecimal growth =
                                    currentBalance.multiply(
                                            annualReturnRate);

                            BigDecimal newBalance =
                                    currentBalance.add(growth);

                            return new ProjectedAccountBalance(
                                    projected.getAccount(),
                                    newBalance);
                        })
                        .toList();

        return new ProjectedPortfolio(
                updatedBalances,
                retainedNonQualifiedAssets.multiply(
                        BigDecimal.ONE.add(annualReturnRate)));
    }

    public ProjectedPortfolio withAdditionalCash(
            BigDecimal amount) {

        return withAdditionalRetainedNonQualifiedAssets(amount);
    }

    public ProjectedPortfolio withAdditionalRetainedNonQualifiedAssets(
            BigDecimal amount) {

        Objects.requireNonNull(
                amount,
                "Cash amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Cash amount cannot be negative.");
        }

        return new ProjectedPortfolio(
                accountBalances,
                retainedNonQualifiedAssets.add(amount));
    }

    public ProjectedPortfolio withRetainedNonQualifiedAssetWithdrawal(
            BigDecimal amount) {

        Objects.requireNonNull(amount, "Withdrawal amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be negative.");
        }

        if (amount.compareTo(retainedNonQualifiedAssets) > 0) {
            throw new IllegalArgumentException(
                    "Withdrawal cannot exceed retained non-qualified assets.");
        }

        return new ProjectedPortfolio(
                accountBalances,
                retainedNonQualifiedAssets.subtract(amount));
    }

    public ProjectedPortfolio withWithdrawal(
            Account account,
            BigDecimal amount) {

        Objects.requireNonNull(
                account,
                "Account is required.");

        Objects.requireNonNull(
                amount,
                "Withdrawal amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be negative.");
        }

        BigDecimal currentBalance =
                getBalance(account);

        if (amount.compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException(
                    "Withdrawal cannot exceed projected account balance.");
        }

        BigDecimal newBalance =
                currentBalance.subtract(amount);

        return withBalance(
                account,
                newBalance);
    }

    public ProjectedPortfolio withGrowth(
            BigDecimal growthAmount) {

        Objects.requireNonNull(
                growthAmount,
                "Growth amount is required.");

        if (getAccountBalanceTotal().signum() == 0) {
            return withGrowth(
                    BigDecimal.ZERO,
                    growthAmount);
        }

        return withGrowth(
                growthAmount,
                BigDecimal.ZERO);
    }

    /*
     * Applies the already-calculated aggregate investment
     * growth to its two projected asset components. Retained
     * RMD assets earn the same general investment return as
     * accounts, but must not have their growth attributed to
     * an account (especially a tax-deferred account).
     */
    public ProjectedPortfolio withGrowth(
            BigDecimal accountGrowth,
            BigDecimal retainedNonQualifiedAssetGrowth) {

        Objects.requireNonNull(
                accountGrowth,
                "Account growth is required.");

        Objects.requireNonNull(
                retainedNonQualifiedAssetGrowth,
                "Retained non-qualified asset growth is required.");

        BigDecimal accountTotal = getAccountBalanceTotal();

        if (accountTotal.signum() == 0) {
            if (accountGrowth.signum() != 0) {
                throw new IllegalArgumentException(
                        "Account growth requires a positive account balance.");
            }

            return new ProjectedPortfolio(
                    accountBalances,
                    retainedNonQualifiedAssets.add(
                            retainedNonQualifiedAssetGrowth));
        }

        List<ProjectedAccountBalance> updatedBalances =
                accountBalances
                        .stream()
                        .map(projected -> {

                            BigDecimal share =
                                    projected
                                            .getBalance()
                                            .divide(
                                                    accountTotal,
                                                    12,
                                                    RoundingMode.HALF_UP);

                            BigDecimal accountBalanceGrowth =
                                    accountGrowth
                                            .multiply(share);

                            BigDecimal newBalance =
                                    projected
                                            .getBalance()
                                            .add(accountBalanceGrowth);

                            return new ProjectedAccountBalance(
                                    projected.getAccount(),
                                    newBalance);
                        })
                        .toList();

        return new ProjectedPortfolio(
                updatedBalances,
                retainedNonQualifiedAssets.add(
                        retainedNonQualifiedAssetGrowth));
    }



}
