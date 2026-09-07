package com.daviddunn.retirementplanner.domain.roth;

import java.util.Set;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
                findProjectedDestinationAccount(
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

    /**
     * Executes a household target across eligible accounts in the existing
     * portfolio order, primary accounts first and spouse accounts second.
     * Each source is credited only to a Roth account with the same owner.
     */
    public ProjectedRothConversionResult convertHousehold(
            ProjectedPortfolio portfolio,
            BigDecimal amount) {

        return convertHousehold(portfolio, amount,
                Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
    }

    public ProjectedRothConversionResult convertHousehold(
            ProjectedPortfolio portfolio,
            BigDecimal amount,
            Set<AccountOwnership> eligibleOwners) {
        eligibleOwners = Set.copyOf(Objects.requireNonNull(eligibleOwners));
        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                amount,
                "Conversion amount is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Conversion amount cannot be negative.");
        }

        if (amount.signum() == 0) {
            return new ProjectedRothConversionResult(
                    portfolio,
                    List.of());
        }

        ProjectedPortfolio updatedPortfolio = portfolio;
        BigDecimal remaining = amount;
        List<RothConversionAllocation> allocations = new ArrayList<>();

        for (AccountOwnership ownership : List.of(
                AccountOwnership.PRIMARY,
                AccountOwnership.SPOUSE)) {

            if (!eligibleOwners.contains(ownership)) {
                continue;
            }
            Account destinationAccount = findDestinationAccount(
                    updatedPortfolio,
                    ownership);

            if (destinationAccount == null) {
                continue;
            }

            for (ProjectedAccountBalance projected :
                    updatedPortfolio.getAccountBalances()) {

                if (remaining.signum() == 0) {
                    break;
                }

                Account sourceAccount = projected.getAccount();

                if (sourceAccount.getOwnership() != ownership
                        || !sourceAccount.isEligibleForRothConversion()) {
                    continue;
                }

                BigDecimal available = updatedPortfolio.getBalance(
                        sourceAccount);

                if (available.signum() <= 0) {
                    continue;
                }

                BigDecimal converted = available.min(remaining);

                updatedPortfolio = updatedPortfolio.withWithdrawal(
                        sourceAccount,
                        converted);

                updatedPortfolio = updatedPortfolio.withBalance(
                        destinationAccount,
                        updatedPortfolio.getBalance(destinationAccount)
                                .add(converted));

                allocations.add(new RothConversionAllocation(
                        ownership,
                        sourceAccount,
                        destinationAccount,
                        converted));

                remaining = remaining.subtract(converted);
            }
        }

        return new ProjectedRothConversionResult(
                updatedPortfolio,
                allocations);
    }

    public BigDecimal getMaximumConvertibleAmount(
            ProjectedPortfolio portfolio) {

        return getMaximumConvertibleAmount(portfolio,
                Set.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE));
    }

    public BigDecimal getMaximumConvertibleAmount(
            ProjectedPortfolio portfolio,
            Set<AccountOwnership> eligibleOwners) {
        Set<AccountOwnership> owners = Set.copyOf(Objects.requireNonNull(eligibleOwners));
        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        return portfolio.getAccountBalances().stream()
                .filter(projected -> {
                    AccountOwnership ownership = projected.getAccount()
                            .getOwnership();
                    return owners.contains(ownership) && ownership != AccountOwnership.JOINT
                            && findDestinationAccount(portfolio, ownership)
                            != null;
                })
                .filter(projected -> projected.getAccount()
                        .isEligibleForRothConversion())
                .map(ProjectedAccountBalance::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
                                .isEligibleForRothConversion())
                .filter(projected ->
                        projected.getBalance()
                                .compareTo(amount) >= 0)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No eligible tax-deferred account found."));
    }

    private ProjectedAccountBalance findProjectedDestinationAccount(
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

    private Account findDestinationAccount(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership) {

        return portfolio.getAccountBalances()
                .stream()
                .map(ProjectedAccountBalance::getAccount)
                .filter(account -> account.getOwnership() == ownership)
                .filter(account -> account.getProjectionAssetType()
                        == ProjectionAssetType.ROTH)
                .findFirst()
                .orElse(null);
    }
}
