package com.daviddunn.retirementplanner.domain.roth;

import java.util.Set;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountBalance;
import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProjectedPortfolioRothConverter {

    private final RothConversionEligibilityPolicy eligibility = new RothConversionEligibilityPolicy();

    public ProjectedPortfolio convert(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership,
            BigDecimal amount) {
        return convertForYear(portfolio, ownership, amount, null);
    }

    public ProjectedPortfolio convert(ProjectedPortfolio portfolio, AccountOwnership ownership,
            BigDecimal amount, int calendarYear) {
        return convertForYear(portfolio, ownership, amount, calendarYear);
    }

    private ProjectedPortfolio convertForYear(ProjectedPortfolio portfolio, AccountOwnership ownership,
            BigDecimal amount, Integer calendarYear) {

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
                        amount, calendarYear);

        ProjectedAccountBalance destination =
                findProjectedDestinationAccount(
                        portfolio,
                        ownership);

        Account sourceAccount =
                source.getAccount();

        Account destinationAccount =
                destination.getAccount();

        if (assess(sourceAccount, destinationAccount, Set.of(ownership), source.getBalance(), calendarYear)
                .maximumConvertibleAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("No eligible tax-deferred account found.");
        }

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
        return convertHouseholdForYear(portfolio, amount, eligibleOwners, null);
    }

    public ProjectedRothConversionResult convertHousehold(ProjectedPortfolio portfolio, BigDecimal amount,
            Set<AccountOwnership> eligibleOwners, int calendarYear) {
        return convertHouseholdForYear(portfolio, amount, eligibleOwners, calendarYear);
    }

    private ProjectedRothConversionResult convertHouseholdForYear(ProjectedPortfolio portfolio, BigDecimal amount,
            Set<AccountOwnership> eligibleOwners, Integer calendarYear) {
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

                if (sourceAccount.getOwnership() != ownership) {
                    continue;
                }

                BigDecimal available = assess(
                        sourceAccount, destinationAccount, eligibleOwners,
                        updatedPortfolio.getBalance(sourceAccount), calendarYear).maximumConvertibleAmount();

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
        return maximumForYear(portfolio, eligibleOwners, null);
    }

    public BigDecimal getMaximumConvertibleAmount(ProjectedPortfolio portfolio,
            Set<AccountOwnership> eligibleOwners, int calendarYear) {
        return maximumForYear(portfolio, eligibleOwners, calendarYear);
    }

    private BigDecimal maximumForYear(ProjectedPortfolio portfolio, Set<AccountOwnership> eligibleOwners,
            Integer calendarYear) {
        Set<AccountOwnership> owners = Set.copyOf(Objects.requireNonNull(eligibleOwners));
        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        return portfolio.getAccountBalances().stream()
                .map(projected -> assess(
                        projected.getAccount(),
                        findDestinationAccount(portfolio, projected.getAccount().getOwnership()),
                        owners, projected.getBalance(), calendarYear).maximumConvertibleAmount())
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
            BigDecimal amount, Integer calendarYear) {

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
                        RothConversionEligibilityPolicy.isSupportedPretaxSource(projected.getAccount().getType()))
                .filter(projected -> eligibility.availabilityAt(projected.getAccount(), calendarYear)
                        != RothConversionEligibilityPolicy.EmployerDistributionAvailability.UNAVAILABLE)
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
                        RothConversionEligibilityPolicy.isSupportedDestination(projected.getAccount().getType()))
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
                .filter(account -> RothConversionEligibilityPolicy.isSupportedDestination(account.getType()))
                .findFirst()
                .orElse(null);
    }

    private RothConversionEligibilityPolicy.Assessment assess(Account source, Account destination,
            Set<AccountOwnership> owners, BigDecimal balance, Integer calendarYear) {
        return calendarYear == null
                ? eligibility.assessUnderLegacyEmployerAssumption(source, destination, owners, balance)
                : eligibility.assessForYear(source, destination, owners, balance, calendarYear);
    }
}
