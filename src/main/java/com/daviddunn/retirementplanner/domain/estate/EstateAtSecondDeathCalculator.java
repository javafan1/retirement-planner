package com.daviddunn.retirementplanner.domain.estate;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import java.time.LocalDate;
import java.util.Objects;

/** Selects the assets immediately before a modeled January 1 household second death. */
public final class EstateAtSecondDeathCalculator {
    private final AfterTaxEstateCalculator estateCalculator = new AfterTaxEstateCalculator();

    public EstateAtSecondDeathSnapshot calculate(
            RetirementPlan plan, Projection projection, LocalDate secondDeathDate) {
        Objects.requireNonNull(plan, "Plan is required.");
        Objects.requireNonNull(projection, "Projection is required.");
        validateCoverageStart(plan, secondDeathDate);
        var start = plan.getPlanningAssumptions().getProjectionStartDate();
        if (secondDeathDate.equals(start)) {
            var portfolio = ProjectedPortfolio.from(plan.getAccountPortfolio());
            var accounts = portfolio.getAccountBalances().stream()
                    .map(balance -> new ProjectedAccountSnapshot(balance.getAccount(), balance.getBalance()))
                    .toList();
            var tax = estateCalculator.calculateEstimatedTax(accounts, plan.getPlanningAssumptions()
                    .getTaxAssumptions().getEstimatedHeirTaxRateOnTaxDeferredAssets());
            return new EstateAtSecondDeathSnapshot(secondDeathDate, start, portfolio.getTotalBalance(),
                    tax, estateCalculator.calculateAfterTaxEstateValue(portfolio.getTotalBalance(), tax));
        }
        var row = projection.getYears().stream()
                .filter(year -> year.getCalendarYear() == secondDeathDate.getYear() - 1)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "No ending-balance snapshot available before second death " + secondDeathDate));
        return new EstateAtSecondDeathSnapshot(secondDeathDate, secondDeathDate.minusDays(1),
                row.getEndingInvestableAssets(), row.getEstimatedHeirTax(), row.getAfterTaxEstateValue());
    }

    public void validateCoverageStart(RetirementPlan plan, LocalDate secondDeathDate) {
        Objects.requireNonNull(secondDeathDate, "Second death date is required.");
        if (secondDeathDate.getDayOfYear() != 1) {
            throw new IllegalArgumentException("Modeled second death must be January 1.");
        }
        if (secondDeathDate.isBefore(plan.getPlanningAssumptions().getProjectionStartDate())) {
            throw new IllegalArgumentException("Second death " + secondDeathDate
                    + " precedes available opening balances.");
        }
    }
}
