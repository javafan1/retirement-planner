package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalCalculator;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalResult;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class ProjectionEngine {

    private final WithdrawalCalculator withdrawalCalculator;
    private final HouseholdRmdCalculator householdRmdCalculator;
    private final GovernmentRules governmentRules;

    public ProjectionEngine() {

        this.withdrawalCalculator =
                new WithdrawalCalculator();

        this.householdRmdCalculator =
                new HouseholdRmdCalculator();

        try {

            GovernmentRulesRepository repository =
                    new GovernmentRulesRepository();

            this.governmentRules =
                    repository.load(
                            "/rules/government-rules-2026.json");

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to load government rules.",
                    e);
        }
    }

    public Projection project(
            RetirementPlan plan) {

        Projection projection =
                new Projection();

        /*
         * Create an independent projected portfolio
         * from the user's actual account balances.
         *
         * The RetirementPlan accounts themselves
         * are never modified by the projection.
         */
        ProjectedPortfolio projectedPortfolio =
                ProjectedPortfolio.from(
                        plan.getAccountPortfolio());

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        int startYear =
                projectionStartDate.getYear();

        int projectionLength =
                assumptions.getProjectionLengthYears();

        /*
         * There is intentionally no prior-year-end
         * snapshot for the first projection year.
         *
         * The starting portfolio represents balances
         * as of the projection start date, not
         * necessarily the previous December 31.
         */
        RmdBalanceSnapshot priorYearEndSnapshot =
                null;

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
                            projectedPortfolio,
                            priorYearEndSnapshot);

            projection.addYear(
                    projectionYear);

            /*
             * Temporary bridge.
             *
             * We have not yet implemented account-level
             * withdrawals, so carry the aggregate ending
             * balance into the next account-level snapshot
             * proportionally.
             */
            ProjectedPortfolio endingPortfolio =
                    createNextPortfolio(
                            projectedPortfolio,
                            projectionYear
                                    .getEndingInvestableAssets());

            /*
             * The ending portfolio represents the
             * modeled December 31 balances for this
             * calendar year.
             *
             * Those balances become the RMD balance
             * snapshot used by the following year.
             */
            priorYearEndSnapshot =
                    RmdBalanceSnapshot.from(
                            LocalDate.of(
                                    calendarYear,
                                    12,
                                    31),
                            endingPortfolio);

            projectedPortfolio =
                    endingPortfolio;
        }

        return projection;
    }

    private ProjectionYear calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            ProjectedPortfolio projectedPortfolio,
            RmdBalanceSnapshot priorYearEndSnapshot) {

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        /*
         * The account-level projected portfolio
         * is the source of beginning assets.
         */
        BigDecimal beginningAssets =
                projectedPortfolio
                        .getTotalBalance();

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

        /*
         * RMD is currently reported only.
         *
         * It does not yet change:
         *
         * - portfolioWithdrawal
         * - taxable income
         * - cash flow
         * - ending assets
         *
         * Those effects will be integrated
         * separately.
         */
        BigDecimal requiredMinimumDistribution =
                calculateRequiredMinimumDistribution(
                        plan,
                        calendarYear,
                        priorYearEndSnapshot);

        return new ProjectionYear(
                yearOffset,
                calendarYear,
                beginningAssets,
                investmentGrowth,
                guaranteedIncome,
                annualExpenses,
                portfolioWithdrawal,
                requiredMinimumDistribution,
                endingAssets);
    }

    private BigDecimal calculateRequiredMinimumDistribution(
            RetirementPlan plan,
            int calendarYear,
            RmdBalanceSnapshot priorYearEndSnapshot) {

        /*
         * The first projection year does not have
         * a modeled prior December 31 balance.
         *
         * Therefore we cannot safely calculate its
         * RMD from the projection data currently
         * available.
         */
        if (priorYearEndSnapshot == null) {
            return BigDecimal.ZERO;
        }

        HouseholdRmdResult result =
                householdRmdCalculator.calculate(
                        plan,
                        priorYearEndSnapshot,
                        calendarYear,
                        governmentRules);

        return result
                .getTotalRmd()
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
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
                                    BigDecimal.valueOf(
                                            activeMonths))
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

    /*
     * Temporary account-level bridge.
     *
     * Until we implement an actual withdrawal
     * strategy, preserve each account's relative
     * share of the total portfolio.
     */
    private ProjectedPortfolio createNextPortfolio(
            ProjectedPortfolio currentPortfolio,
            BigDecimal endingTotal) {

        BigDecimal currentTotal =
                currentPortfolio
                        .getTotalBalance();

        if (currentTotal.signum() == 0) {
            return currentPortfolio;
        }

        BigDecimal ratio =
                endingTotal.divide(
                        currentTotal,
                        12,
                        RoundingMode.HALF_UP);

        List<ProjectedAccountBalance> updatedBalances =
                currentPortfolio
                        .getAccountBalances()
                        .stream()
                        .map(projected -> {

                            BigDecimal newBalance =
                                    projected
                                            .getBalance()
                                            .multiply(ratio)
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP);

                            return new ProjectedAccountBalance(
                                    projected
                                            .getAccount(),
                                    newBalance);
                        })
                        .toList();

        return new ProjectedPortfolio(
                updatedBalances);
    }
}