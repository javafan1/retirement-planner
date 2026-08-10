package com.daviddunn.retirementplanner.domain.roth;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;

import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectedPortfolioRothConverter {

    public ProjectedPortfolio convert(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership,
            BigDecimal amount) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                ownership,
                "Ownership is required.");

        Objects.requireNonNull(
                amount,
                "Conversion amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Conversion amount cannot be negative.");
        }

        if (amount.signum() == 0) {
            return portfolio;
        }

        ProjectedAccountBalance source =
                findSourceAccount(
                        portfolio,
                        ownership,
                        amount);

        ProjectedAccountBalance destination =
                findDestinationAccount(
                        portfolio,
                        ownership);

        Account sourceAccount =
                source.getAccount();

        Account destinationAccount =
                destination.getAccount();

        ProjectedPortfolio updatedPortfolio =
                portfolio.withWithdrawal(
                        sourceAccount,
                        amount);

        BigDecimal destinationBalance =
                updatedPortfolio.getBalance(
                        destinationAccount);

        return updatedPortfolio.withBalance(
                destinationAccount,
                destinationBalance.add(amount));
    }

//    private ProjectedAccountBalance findSourceAccount(
//            ProjectedPortfolio portfolio,
//            AccountOwnership ownership,
//            BigDecimal amount) {
//
//        return portfolio.getAccountBalances()
//                .stream()
//                .filter(projected ->
//                        projected.getAccount().getOwnership()
//                                == ownership)
//                .filter(projected ->
//                        projected.getAccount()
//                                .getProjectionAssetType()
//                                == ProjectionAssetType.TAX_DEFERRED)
//                .peek(projected ->
//                        System.out.println(
//                                "ROTH SOURCE CHECK: "
//                                        + projected.getAccount().getName()
//                                        + " balance="
//                                        + projected.getBalance()
//                                        + " requested="
//                                        + amount))
//                .filter(projected ->
//                        projected.getBalance()
//                                .compareTo(amount) >= 0)
//                .findFirst()
//                .orElseThrow(() ->
//                        new IllegalStateException(
//                                "No eligible tax-deferred account found."));
//        return portfolio.getAccountBalances()
//                .stream()
//                .filter(projected ->
//                        projected.getAccount().getOwnership()
//                                == ownership)
//                .filter(projected ->
//                        projected.getAccount()
//                                .getProjectionAssetType()
//                                == ProjectionAssetType.TAX_DEFERRED)
//                .filter(projected ->
//                        projected.getBalance()
//                                .compareTo(amount) >= 0)
//                .findFirst()
//                .orElseThrow(() ->
//                        new IllegalStateException(
//                                "No eligible tax-deferred account found."));
//    }

    private ProjectedAccountBalance findSourceAccount(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership,
            BigDecimal amount) {

//        System.out.println();
//        System.out.println("===== ROTH CONVERSION SOURCE CHECK =====");
//        System.out.println("Requested ownership = " + ownership);
//        System.out.println("Requested amount    = " + amount);
//
//        for (ProjectedAccountBalance projected :
//                portfolio.getAccountBalances()) {
//
//            Account account =
//                    projected.getAccount();
//
//            System.out.println(
//                    "Account = "
//                            + account.getName()
//                            + " | ownership = "
//                            + account.getOwnership()
//                            + " | assetType = "
//                            + account.getProjectionAssetType()
//                            + " | balance = "
//                            + projected.getBalance());
//        }
//
//        System.out.println("========================================");
//        System.out.println();

        return portfolio.getAccountBalances()
                .stream()
                .filter(projected ->
                        projected.getAccount().getOwnership()
                                == ownership)
                .filter(projected ->
                        projected.getAccount()
                                .getProjectionAssetType()
                                == ProjectionAssetType.TAX_DEFERRED)
                .filter(projected ->
                        projected.getBalance()
                                .compareTo(amount) >= 0)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No eligible tax-deferred account found."));
    }

    private ProjectedAccountBalance findDestinationAccount(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership) {

        return portfolio.getAccountBalances()
                .stream()
                .filter(projected ->
                        projected.getAccount().getOwnership()
                                == ownership)
                .filter(projected ->
                        projected.getAccount()
                                .getProjectionAssetType()
                                == ProjectionAssetType.ROTH)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No eligible Roth account found."));
    }
}