package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProjectedPortfolio {

    private final List<ProjectedAccountBalance> accountBalances;

    public ProjectedPortfolio(
            List<ProjectedAccountBalance> accountBalances) {

        Objects.requireNonNull(
                accountBalances,
                "Projected account balances are required.");

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

    public List<ProjectedAccountBalance> getAccountBalances() {
        return accountBalances;
    }

    public BigDecimal getTotalBalance() {

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
                updatedBalances);
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
                updatedBalances);
    }

}