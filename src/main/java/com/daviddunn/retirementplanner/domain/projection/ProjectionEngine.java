package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;
import java.time.Year;

public class ProjectionEngine {

    public Projection project(RetirementPlan plan) {

        Projection projection = new Projection();

        Household household = plan.getHousehold();
        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        BigDecimal beginningAssets =
                household.getTotalAssets();

        int startYear = Year.now().getValue();

        int projectionLength =
                assumptions.getProjectionLengthYears();

        for (int projectionYear = 0;
             projectionYear < projectionLength;
             projectionYear++) {

            ProjectionYear year = projectYear(
                    projectionYear,
                    startYear + projectionYear,
                    beginningAssets,
                    household,
                    assumptions);

            projection.addYear(year);

            beginningAssets =
                    year.getEndingInvestableAssets();
        }

        return projection;
    }

    private ProjectionYear projectYear(
            int projectionYear,
            int calendarYear,
            BigDecimal beginningAssets,
            Household household,
            PlanningAssumptions assumptions) {

        BigDecimal investmentGrowth =
                calculateInvestmentGrowth(
                        beginningAssets,
                        assumptions);

        BigDecimal totalIncome =
                calculateTotalIncome(
                        household);

        BigDecimal projectedExpenses =
                calculateProjectedExpenses(
                        household,
                        assumptions,
                        projectionYear);

        BigDecimal endingAssets =
                calculateEndingAssets(
                        beginningAssets,
                        investmentGrowth,
                        totalIncome,
                        projectedExpenses);

        return new ProjectionYear(
                projectionYear,
                calendarYear,
                beginningAssets,
                investmentGrowth,
                totalIncome,
                projectedExpenses,
                endingAssets);
    }

    private BigDecimal calculateInvestmentGrowth(
            BigDecimal beginningAssets,
            PlanningAssumptions assumptions) {

        return beginningAssets.multiply(
                assumptions.getExpectedAnnualInvestmentReturn());
    }

    private BigDecimal calculateTotalIncome(
            Household household) {

        return household.getGuaranteedIncome();
    }

    private BigDecimal calculateProjectedExpenses(
            Household household,
            PlanningAssumptions assumptions,
            int projectionYear) {

        // Inflation will be implemented in the next step.
        return household.getTotalAnnualExpenses();
    }

    private BigDecimal calculateEndingAssets(
            BigDecimal beginningAssets,
            BigDecimal investmentGrowth,
            BigDecimal totalIncome,
            BigDecimal projectedExpenses) {

        return beginningAssets
                .add(investmentGrowth)
                .add(totalIncome)
                .subtract(projectedExpenses);
    }
}