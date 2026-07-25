package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class OwnerRmdCalculator {

    private final OwnerRmdService ownerRmdService;
    private final EmployerPlanRmdService employerPlanRmdService;

    public OwnerRmdCalculator() {

        this(
                new OwnerRmdService(),
                new EmployerPlanRmdService());
    }

    public OwnerRmdCalculator(
            OwnerRmdService ownerRmdService,
            EmployerPlanRmdService employerPlanRmdService) {

        this.ownerRmdService =
                Objects.requireNonNull(
                        ownerRmdService,
                        "Owner RMD service is required.");

        this.employerPlanRmdService =
                Objects.requireNonNull(
                        employerPlanRmdService,
                        "Employer plan RMD service is required.");
    }

    /*
     * Existing calculation path.
     *
     * Uses the current balances stored in the
     * AccountPortfolio.
     *
     * Retained for existing callers and tests.
     */
    public OwnerRmdResult calculate(
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

        BigDecimal iraRmd =
                ownerRmdService.calculateIraRmd(
                        portfolio,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        List<AccountRmd> traditional401kRmds =
                employerPlanRmdService.calculate401kRmds(
                        portfolio,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        List<AccountRmd> traditional403bRmds =
                employerPlanRmdService.calculate403bRmds(
                        portfolio,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        return new OwnerRmdResult(
                iraRmd,
                traditional401kRmds,
                traditional403bRmds);
    }

    /*
     * Projection-safe calculation path.
     *
     * Uses account balances captured in the
     * applicable prior December 31 snapshot.
     */
    public OwnerRmdResult calculate(
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

        BigDecimal iraRmd =
                ownerRmdService.calculateIraRmd(
                        portfolio,
                        balanceSnapshot,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        List<AccountRmd> traditional401kRmds =
                employerPlanRmdService.calculate401kRmds(
                        portfolio,
                        balanceSnapshot,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        List<AccountRmd> traditional403bRmds =
                employerPlanRmdService.calculate403bRmds(
                        portfolio,
                        balanceSnapshot,
                        ownership,
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        return new OwnerRmdResult(
                iraRmd,
                traditional401kRmds,
                traditional403bRmds);
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