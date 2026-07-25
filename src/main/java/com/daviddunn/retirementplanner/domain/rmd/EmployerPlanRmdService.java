package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class EmployerPlanRmdService {

    private final RmdService rmdService;

    public EmployerPlanRmdService() {
        this(new RmdService());
    }

    public EmployerPlanRmdService(
            RmdService rmdService) {

        this.rmdService =
                Objects.requireNonNull(
                        rmdService,
                        "RMD service is required.");
    }

    /*
     * Existing 401(k) calculation path.
     *
     * Uses the current balance stored in each
     * Account. Retained for existing callers
     * and tests.
     */
    public List<AccountRmd> calculate401kRmds(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                portfolio,
                ownership,
                dateOfBirth,
                governmentRules);

        return portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account ->
                        account.getType()
                                == AccountType.TRADITIONAL_401K)
                .map(account ->
                        calculateAccountRmd(
                                account,
                                account.getCurrentBalance(),
                                dateOfBirth,
                                projectionYear,
                                governmentRules))
                .toList();
    }

    /*
     * Projection-safe 401(k) calculation path.
     *
     * Uses the balance from the applicable
     * prior December 31 snapshot.
     */
    public List<AccountRmd> calculate401kRmds(
            AccountPortfolio portfolio,
            RmdBalanceSnapshot balanceSnapshot,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                portfolio,
                ownership,
                dateOfBirth,
                governmentRules);

        Objects.requireNonNull(
                balanceSnapshot,
                "RMD balance snapshot is required.");

        return portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account ->
                        account.getType()
                                == AccountType.TRADITIONAL_401K)
                .map(account ->
                        calculateAccountRmd(
                                account,
                                balanceSnapshot.getBalance(account),
                                dateOfBirth,
                                projectionYear,
                                governmentRules))
                .toList();
    }

    /*
     * Existing 403(b) calculation path.
     *
     * Uses current Account balances.
     */
    public List<AccountRmd> calculate403bRmds(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                portfolio,
                ownership,
                dateOfBirth,
                governmentRules);

        return portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account ->
                        account.getType()
                                == AccountType.TRADITIONAL_403B)
                .map(account ->
                        calculateAccountRmd(
                                account,
                                account.getCurrentBalance(),
                                dateOfBirth,
                                projectionYear,
                                governmentRules))
                .toList();
    }

    /*
     * Projection-safe 403(b) calculation path.
     *
     * Uses prior December 31 snapshot balances.
     */
    public List<AccountRmd> calculate403bRmds(
            AccountPortfolio portfolio,
            RmdBalanceSnapshot balanceSnapshot,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        validateArguments(
                portfolio,
                ownership,
                dateOfBirth,
                governmentRules);

        Objects.requireNonNull(
                balanceSnapshot,
                "RMD balance snapshot is required.");

        return portfolio
                .getAccounts(ownership)
                .stream()
                .filter(account ->
                        account.getType()
                                == AccountType.TRADITIONAL_403B)
                .map(account ->
                        calculateAccountRmd(
                                account,
                                balanceSnapshot.getBalance(account),
                                dateOfBirth,
                                projectionYear,
                                governmentRules))
                .toList();
    }

    /*
     * Common account-level calculation.
     *
     * Notice that balance is now explicitly
     * supplied rather than read inside this
     * method from Account.getCurrentBalance().
     */
    private AccountRmd calculateAccountRmd(
            Account account,
            BigDecimal balance,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                balance,
                "RMD account balance is required.");

        BigDecimal amount =
                rmdService.calculateRmd(
                        dateOfBirth,
                        projectionYear,
                        balance,
                        governmentRules);

        return new AccountRmd(
                account,
                amount);
    }

    public BigDecimal getTotalRmd(
            List<AccountRmd> accountRmds) {

        Objects.requireNonNull(
                accountRmds,
                "Account RMDs are required.");

        return accountRmds
                .stream()
                .map(AccountRmd::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    private void validateArguments(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                portfolio,
                "Account portfolio is required.");

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        Objects.requireNonNull(
                dateOfBirth,
                "Date of birth is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");
    }
}