package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.rmd.AccountRmd;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rmd.RmdAccountCategory;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalStrategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class ProjectedWithdrawalAllocator {

    public ProjectedPortfolio applyAccountRmds(
            ProjectedPortfolio portfolio,
            List<AccountRmd> accountRmds) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                accountRmds,
                "Account RMDs are required.");

        ProjectedPortfolio updatedPortfolio =
                portfolio;

        for (AccountRmd accountRmd : accountRmds) {

            BigDecimal amount =
                    accountRmd.getAmount();

            if (amount.signum() == 0) {
                continue;
            }

            updatedPortfolio =
                    updatedPortfolio.withWithdrawal(
                            accountRmd.getAccount(),
                            amount);
        }

        return updatedPortfolio;
    }

    public ProjectedPortfolio applyIraRmd(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership,
            BigDecimal iraRmd) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        Objects.requireNonNull(
                iraRmd,
                "IRA RMD is required.");

        if (iraRmd.signum() < 0) {
            throw new IllegalArgumentException(
                    "IRA RMD cannot be negative.");
        }

        if (iraRmd.signum() == 0) {
            return portfolio;
        }

        BigDecimal remaining =
                iraRmd;

        ProjectedPortfolio updatedPortfolio =
                portfolio;

        for (ProjectedAccountBalance projected :
                portfolio.getAccountBalances()) {

            Account account =
                    projected.getAccount();

            if (account.getOwnership() != ownership) {
                continue;
            }

            if (!account.getType().isSubjectToOwnerRmd()) {
                continue;
            }

            if (account.getType().getRmdAccountCategory()
                    != RmdAccountCategory.IRA) {
                continue;
            }

            BigDecimal available =
                    updatedPortfolio.getBalance(account);

            BigDecimal withdrawal =
                    available.min(remaining);

            if (withdrawal.signum() > 0) {

                updatedPortfolio =
                        updatedPortfolio.withWithdrawal(
                                account,
                                withdrawal);

                remaining =
                        remaining.subtract(withdrawal);
            }

            if (remaining.signum() == 0) {
                break;
            }
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Insufficient projected IRA balance to satisfy RMD.");
        }

        return updatedPortfolio;
    }

    public ProjectedPortfolio applyHouseholdRmds(
            ProjectedPortfolio portfolio,
            HouseholdRmdResult householdRmdResult) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                householdRmdResult,
                "Household RMD result is required.");

        ProjectedPortfolio updatedPortfolio =
                portfolio;

        /*
         * Apply the primary owner's RMDs.
         */
        updatedPortfolio =
                applyOwnerRmds(
                        updatedPortfolio,
                        AccountOwnership.PRIMARY,
                        householdRmdResult.getPrimaryRmd());

        /*
         * Apply the spouse's RMDs.
         */
        updatedPortfolio =
                applyOwnerRmds(
                        updatedPortfolio,
                        AccountOwnership.SPOUSE,
                        householdRmdResult.getSpouseRmd());

        return updatedPortfolio;
    }

    private ProjectedPortfolio applyOwnerRmds(
            ProjectedPortfolio portfolio,
            AccountOwnership ownership,
            OwnerRmdResult ownerRmdResult) {

        ProjectedPortfolio updatedPortfolio =
                portfolio;

        /*
         * IRA RMDs may be satisfied across the
         * owner's eligible IRA accounts.
         */
        updatedPortfolio =
                applyIraRmd(
                        updatedPortfolio,
                        ownership,
                        ownerRmdResult.getIraRmd());

        /*
         * Employer-plan RMDs remain tied to their
         * specific accounts.
         */
        updatedPortfolio =
                applyAccountRmds(
                        updatedPortfolio,
                        ownerRmdResult
                                .getTraditional401kRmds());

        updatedPortfolio =
                applyAccountRmds(
                        updatedPortfolio,
                        ownerRmdResult
                                .getTraditional403bRmds());

        return updatedPortfolio;
    }

    public ProjectedPortfolio applyAdditionalWithdrawal(
            ProjectedPortfolio portfolio,
            BigDecimal withdrawalAmount,
            WithdrawalStrategy withdrawalStrategy) {

        Objects.requireNonNull(
                portfolio,
                "Projected portfolio is required.");

        Objects.requireNonNull(
                withdrawalAmount,
                "Withdrawal amount is required.");

        Objects.requireNonNull(
                withdrawalStrategy,
                "Withdrawal strategy is required.");

        if (withdrawalAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount cannot be negative.");
        }

        if (withdrawalAmount.signum() == 0) {
            return portfolio;
        }

        BigDecimal remaining =
                withdrawalAmount;

        ProjectedPortfolio updatedPortfolio =
                portfolio;

        /*
         * Strategy determines which accounts are
         * considered first.
         *
         * The allocator remains responsible only
         * for actually changing balances.
         */
        List<ProjectedAccountBalance> orderedAccounts =
                withdrawalStrategy.orderAccounts(
                        portfolio);

        for (ProjectedAccountBalance projected :
                orderedAccounts) {

            Account account =
                    projected.getAccount();

            BigDecimal available =
                    updatedPortfolio.getBalance(
                            account);

            BigDecimal withdrawal =
                    available.min(
                            remaining);

            if (withdrawal.signum() > 0) {

                updatedPortfolio =
                        updatedPortfolio.withWithdrawal(
                                account,
                                withdrawal);

                remaining =
                        remaining.subtract(
                                withdrawal);
            }

            if (remaining.signum() == 0) {
                break;
            }
        }

        if (remaining.signum() > 0) {
            throw new IllegalStateException(
                    "Insufficient projected assets to satisfy withdrawal.");
        }

        return updatedPortfolio;
    }
}