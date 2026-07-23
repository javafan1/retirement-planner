package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalCalculator;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class ProjectionEngine {

    private final WithdrawalCalculator
            withdrawalCalculator =
            new WithdrawalCalculator();

    public Projection project(RetirementPlan plan) {

        Projection projection =
                new Projection();

        BigDecimal beginningAssets =
                plan.getAccountPortfolio()
                        .getTotalBalance();

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        int startYear =
                projectionStartDate.getYear();

        int projectionLength =
                assumptions.getProjectionLengthYears();

        for (int yearOffset = 0;
             yearOffset < projectionLength;
             yearOffset++) {

            int calendarYear =
                    startYear + yearOffset;

            ProjectionYear projectionYear =
                    calculateProjectionYear(
                            plan,
                            yearOffset,
                            calendarYear,
                            beginningAssets);

            projection.addYear(
                    projectionYear);

            beginningAssets =
                    projectionYear
                            .getEndingInvestableAssets();
        }

        return projection;
    }

    private ProjectionYear calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            BigDecimal beginningAssets) {

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        /*
         * The first projection year begins on the
         * actual projection start date.
         *
         * Subsequent years begin January 1.
         */
        LocalDate projectionDate =
                yearOffset == 0
                        ? projectionStartDate
                        : LocalDate.of(
                        calendarYear,
                        1,
                        1);

        Household household =
                plan.getHousehold();

        BigDecimal investmentGrowth =
                calculateInvestmentGrowth(
                        beginningAssets,
                        assumptions,
                        yearOffset,
                        projectionStartDate);

        BigDecimal guaranteedIncome =
                calculateTotalIncome(
                        household,
                        projectionDate);

        BigDecimal annualExpenses =
                calculateProjectedExpenses(
                        household,
                        assumptions,
                        yearOffset,
                        projectionStartDate);

        BigDecimal portfolioWithdrawal =
                calculatePortfolioWithdrawal(
                        guaranteedIncome,
                        annualExpenses);

        BigDecimal endingAssets =
                calculateEndingAssets(
                        beginningAssets,
                        investmentGrowth,
                        portfolioWithdrawal);

        return new ProjectionYear(
                yearOffset,
                calendarYear,
                beginningAssets,
                investmentGrowth,
                guaranteedIncome,
                annualExpenses,
                portfolioWithdrawal,
                endingAssets);
    }

    private BigDecimal calculateInvestmentGrowth(
            BigDecimal beginningAssets,
            PlanningAssumptions assumptions,
            int yearOffset,
            LocalDate projectionStartDate) {

        BigDecimal investmentGrowth =
                beginningAssets.multiply(
                        assumptions
                                .getExpectedAnnualInvestmentReturn());

        /*
         * Prorate investment growth for the
         * partial first calendar year.
         */
        if (yearOffset == 0) {

            int activeMonths =
                    13 -
                            projectionStartDate
                                    .getMonthValue();

            investmentGrowth =
                    investmentGrowth
                            .multiply(
                                    BigDecimal.valueOf(activeMonths))
                            .divide(
                                    BigDecimal.valueOf(12),
                                    2,
                                    RoundingMode.HALF_UP);
        }

        return investmentGrowth.setScale(
                2,
                RoundingMode.HALF_UP);
    }
    private BigDecimal calculateTotalIncome(
            Household household,
            LocalDate projectionDate) {

        BigDecimal total =
                BigDecimal.ZERO;

        total = total.add(
                calculateIncome(
                        household.getPrimaryPerson(),
                        projectionDate));

        total = total.add(
                calculateIncome(
                        household.getSpouse(),
                        projectionDate));

        return total;
    }

    private BigDecimal calculateIncome(
            Person person,
            LocalDate projectionDate) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            total = total.add(
                    income.getAnnualIncome(
                            projectionDate));
        }

        return total;
    }

    private BigDecimal calculateProjectedExpenses(
            Household household,
            PlanningAssumptions assumptions,
            int yearOffset,
            LocalDate projectionStartDate) {

        BigDecimal inflationMultiplier =
                BigDecimal.ONE
                        .add(
                                assumptions
                                        .getExpectedAnnualInflationRate())
                        .pow(yearOffset);

        BigDecimal annualExpenses =
                household
                        .getTotalAnnualExpenses()
                        .multiply(
                                inflationMultiplier);

        /*
         * Only the first projection year can
         * represent a partial calendar year.
         *
         * Example:
         *
         * June start:
         * June through December = 7 months.
         */
        if (yearOffset == 0) {

            int activeMonths =
                    13 -
                            projectionStartDate
                                    .getMonthValue();

            annualExpenses =
                    annualExpenses
                            .multiply(
                                    BigDecimal.valueOf(
                                            activeMonths))
                            .divide(
                                    BigDecimal.valueOf(12),
                                    2,
                                    RoundingMode.HALF_UP);
        }

        return annualExpenses
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePortfolioWithdrawal(
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses) {

        WithdrawalResult withdrawalResult =
                withdrawalCalculator
                        .calculateWithdrawal(
                                guaranteedIncome,
                                annualExpenses);

        return withdrawalResult
                .getTotalWithdrawal();
    }

    private BigDecimal calculateEndingAssets(
            BigDecimal beginningAssets,
            BigDecimal investmentGrowth,
            BigDecimal portfolioWithdrawal) {

        return beginningAssets
                .add(investmentGrowth)
                .subtract(portfolioWithdrawal);
    }
}