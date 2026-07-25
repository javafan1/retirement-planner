package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class OwnerRmdService {

    private final RmdBalanceAggregator balanceAggregator;
    private final RmdService rmdService;

    public OwnerRmdService() {

        this(
                new RmdBalanceAggregator(),
                new RmdService());
    }

    public OwnerRmdService(
            RmdBalanceAggregator balanceAggregator,
            RmdService rmdService) {

        this.balanceAggregator =
                Objects.requireNonNull(
                        balanceAggregator,
                        "RMD balance aggregator is required.");

        this.rmdService =
                Objects.requireNonNull(
                        rmdService,
                        "RMD service is required.");
    }

    /*
     * Existing calculation path.
     *
     * This method uses the current balances stored
     * in AccountPortfolio. We are retaining it for
     * existing callers and tests.
     */
    public BigDecimal calculateIraRmd(
            AccountPortfolio portfolio,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
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

        BigDecimal iraBalance =
                balanceAggregator.getBalance(
                        portfolio,
                        ownership,
                        RmdAccountCategory.IRA);

        return rmdService.calculateRmd(
                dateOfBirth,
                projectionYear,
                iraBalance,
                governmentRules);
    }

    /*
     * Projection-safe calculation path.
     *
     * AccountPortfolio determines which accounts
     * belong to the owner and which accounts are
     * part of the IRA RMD pool.
     *
     * RmdBalanceSnapshot supplies the balance that
     * existed on the applicable prior December 31.
     */
    public BigDecimal calculateIraRmd(
            AccountPortfolio portfolio,
            RmdBalanceSnapshot balanceSnapshot,
            AccountOwnership ownership,
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                portfolio,
                "Account portfolio is required.");

        Objects.requireNonNull(
                balanceSnapshot,
                "RMD balance snapshot is required.");

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        Objects.requireNonNull(
                dateOfBirth,
                "Date of birth is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        BigDecimal iraBalance =
                portfolio
                        .getAccounts(ownership)
                        .stream()
                        .filter(account ->
                                account
                                        .getType()
                                        .isSubjectToOwnerRmd())
                        .filter(account ->
                                account
                                        .getType()
                                        .getRmdAccountCategory()
                                        == RmdAccountCategory.IRA)
                        .map(balanceSnapshot::getBalance)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add);

        return rmdService.calculateRmd(
                dateOfBirth,
                projectionYear,
                iraBalance,
                governmentRules);
    }
}
