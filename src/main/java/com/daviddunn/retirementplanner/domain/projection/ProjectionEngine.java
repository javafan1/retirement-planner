package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalCalculator;
import com.daviddunn.retirementplanner.domain.withdrawal.WithdrawalDisposition;
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
    private final ProjectedWithdrawalAllocator withdrawalAllocator;

    public ProjectionEngine() {

        this.withdrawalCalculator =
                new WithdrawalCalculator();

        this.householdRmdCalculator =
                new HouseholdRmdCalculator();

        this.withdrawalAllocator =
                new ProjectedWithdrawalAllocator();

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

            ProjectionYearCalculation calculation =
                    calculateProjectionYear(
                            plan,
                            yearOffset,
                            calendarYear,
                            projectedPortfolio,
                            priorYearEndSnapshot);

            ProjectionYear projectionYear =
                    calculation.getProjectionYear();

            ProjectedPortfolio endingPortfolio =
                    calculation.getEndingPortfolio();

            projection.addYear(
                    projectionYear);


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

//    private ProjectionYearCalculation calculateProjectionYear(
//            RetirementPlan plan,
//            int yearOffset,
//            int calendarYear,
//            ProjectedPortfolio projectedPortfolio,
//            RmdBalanceSnapshot priorYearEndSnapshot) {
//
//        PlanningAssumptions assumptions =
//                plan.getPlanningAssumptions();
//
//        LocalDate projectionStartDate =
//                assumptions.getProjectionStartDate();
//
//        /*
//         * The account-level projected portfolio
//         * is the source of beginning assets.
//         */
//        BigDecimal beginningAssets =
//                projectedPortfolio
//                        .getTotalBalance();
//
//        /*
//         * The first projection year begins on the
//         * actual projection start date.
//         *
//         * Subsequent years begin January 1.
//         */
//        LocalDate projectionDate =
//                yearOffset == 0
//                        ? projectionStartDate
//                        : LocalDate.of(
//                        calendarYear,
//                        1,
//                        1);
//
//        Household household =
//                plan.getHousehold();
//
//        BigDecimal investmentGrowth =
//                calculateInvestmentGrowth(
//                        beginningAssets,
//                        assumptions,
//                        yearOffset,
//                        projectionStartDate);
//
//        BigDecimal guaranteedIncome =
//                calculateTotalIncome(
//                        household,
//                        projectionDate);
//
//        BigDecimal annualExpenses =
//                calculateProjectedExpenses(
//                        household,
//                        assumptions,
//                        yearOffset,
//                        projectionStartDate);
//
//        HouseholdRmdResult householdRmdResult =
//                calculateRequiredMinimumDistribution(
//                        plan,
//                        calendarYear,
//                        priorYearEndSnapshot);
//
//        BigDecimal requiredMinimumDistribution =
//                householdRmdResult
//                        .getTotalRmd()
//                        .setScale(
//                                2,
//                                RoundingMode.HALF_UP);
//
//        WithdrawalResult withdrawalResult =
//                calculatePortfolioWithdrawal(
//                        guaranteedIncome,
//                        annualExpenses,
//                        requiredMinimumDistribution);
//
//
//
//        BigDecimal portfolioWithdrawal =
//                withdrawalResult
//                        .getTotalWithdrawal();
//
//        ProjectedPortfolio portfolioAfterRmd =
//                withdrawalAllocator.applyHouseholdRmds(
//                        projectedPortfolio,
//                        householdRmdResult);
//
//        ProjectedPortfolio portfolioAfterExcessRmd =
//                portfolioAfterRmd.withAdditionalCash(
//                        withdrawalResult.getExcessRmd());
//
//        /*
//         * Separate the portfolio distribution into
//         * money consumed by household spending and
//         * money that remains household wealth.
//         */
//        WithdrawalDisposition withdrawalDisposition =
//                withdrawalResult
//                        .getDisposition();
//
//        BigDecimal endingAssets =
//                calculateEndingAssets(
//                        beginningAssets,
//                        investmentGrowth,
//                        withdrawalDisposition);
//
//        ProjectionYear projectionYear =
//                new ProjectionYear(
//                        yearOffset,
//                        calendarYear,
//                        beginningAssets,
//                        investmentGrowth,
//                        guaranteedIncome,
//                        annualExpenses,
//                        withdrawalResult.getCashFlowNeed(),
//                        portfolioWithdrawal,
//                        requiredMinimumDistribution,
//                        withdrawalResult.getExcessRmd(),
//                        endingAssets);
//
//        /*
//         * Temporary bridge remains in place.
//         *
//         * We have not yet integrated account-level
//         * RMD allocation into the year calculation.
//         */
//        ProjectedPortfolio endingPortfolio =
//                createNextPortfolio(
//                        projectedPortfolio,
//                        endingAssets);
//
//        return new ProjectionYearCalculation(
//                projectionYear,
//                endingPortfolio);
//    }

    private ProjectionYearCalculation calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            ProjectedPortfolio projectedPortfolio,
            RmdBalanceSnapshot priorYearEndSnapshot) {

        BigDecimal beginningAssets =
                projectedPortfolio.getTotalBalance();


        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        BigDecimal investmentGrowth =
                calculateInvestmentGrowth(
                        beginningAssets,
                        assumptions,
                        yearOffset,
                        projectionStartDate);

        LocalDate projectionDate =
                yearOffset == 0
                        ? projectionStartDate
                        : LocalDate.of(
                        calendarYear,
                        1,
                        1);

        Household household =
                plan.getHousehold();

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

        /*
         * Calculate the complete household RMD
         * result rather than immediately reducing
         * it to a single dollar amount.
         */
        HouseholdRmdResult householdRmdResult =
                calculateRequiredMinimumDistribution(
                        plan,
                        calendarYear,
                        priorYearEndSnapshot);

        BigDecimal requiredMinimumDistribution =
                householdRmdResult
                        .getTotalRmd()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        WithdrawalResult withdrawalResult =
                withdrawalCalculator
                        .calculateWithdrawal(
                                guaranteedIncome,
                                annualExpenses,
                                requiredMinimumDistribution);

        BigDecimal portfolioWithdrawal =
                withdrawalResult
                        .getTotalWithdrawal()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        /*
         * Account-level projection state.
         *
         * First allocate the already-calculated
         * investment growth across the projected
         * accounts.
         */
//        ProjectedPortfolio portfolioAfterGrowth =
//                projectedPortfolio.withGrowth(
//                        investmentGrowth);

        /*
         * Remove RMDs from the actual accounts
         * responsible for those distributions.
         */
//        ProjectedPortfolio portfolioAfterRmd =
//                withdrawalAllocator.applyHouseholdRmds(
//                        portfolioAfterGrowth,
//                        householdRmdResult);

        /*
         * Any RMD amount not required for modeled
         * spending remains a household asset.
         *
         * Until we have an explicit taxable-account
         * allocation policy, retain it as
         * unallocated projected cash.
         */
//        ProjectedPortfolio portfolioAfterExcessRmd =
//                portfolioAfterRmd.withAdditionalCash(
//                        withdrawalResult.getExcessRmd());

        ProjectedPortfolio portfolioAfterGrowth =
                projectedPortfolio.withGrowth(
                        investmentGrowth);

        ProjectedPortfolio portfolioAfterRmd =
                withdrawalAllocator.applyHouseholdRmds(
                        portfolioAfterGrowth,
                        householdRmdResult);

        /*
         * The RMD may satisfy some or all of the
         * household's cash-flow need.
         *
         * Withdraw only the remaining amount that
         * still must come from the portfolio.
         */
        ProjectedPortfolio portfolioAfterAdditionalWithdrawal =
                withdrawalAllocator.applyAdditionalWithdrawal(
                        portfolioAfterRmd,
                        withdrawalResult
                                .getAdditionalWithdrawalRequired());

        /*
         * If the RMD exceeded the household's
         * spending need, the excess remains an
         * investable household asset.
         */
        ProjectedPortfolio endingPortfolio =
                portfolioAfterAdditionalWithdrawal
                        .withAdditionalCash(
                                withdrawalResult.getExcessRmd());

        /*
         * Aggregate ending-assets calculation.
         *
         * We continue using this calculation until
         * ordinary non-RMD portfolio withdrawals
         * are allocated at the account level.
         */
        BigDecimal endingAssets =
                beginningAssets
                        .add(investmentGrowth)
                        .subtract(portfolioWithdrawal)
                        .add(
                                withdrawalResult
                                        .getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        ProjectionYear projectionYear =
                new ProjectionYear(
                        yearOffset,
                        calendarYear,
                        beginningAssets,
                        investmentGrowth,
                        guaranteedIncome,
                        annualExpenses,
                        withdrawalResult.getCashFlowNeed(),
                        portfolioWithdrawal,
                        requiredMinimumDistribution,
                        withdrawalResult.getExcessRmd(),
                        endingAssets);

        /*
         * TEMPORARY BRIDGE
         *
         * Do not use portfolioAfterExcessRmd as the
         * ending portfolio yet.
         *
         * We still need account-level allocation of
         * ordinary cash-flow withdrawals before the
         * account-level portfolio can completely
         * replace this proportional bridge.
         */
//        ProjectedPortfolio endingPortfolio =
//                createNextPortfolio(
//                        projectedPortfolio,
//                        endingAssets);

        return new ProjectionYearCalculation(
                projectionYear,
                endingPortfolio);
    }

    private HouseholdRmdResult calculateRequiredMinimumDistribution(
            RetirementPlan plan,
            int calendarYear,
            RmdBalanceSnapshot priorYearEndSnapshot) {

        /*
         * The first projection year does not have
         * a modeled prior December 31 balance.
         */
        if (priorYearEndSnapshot == null) {
            return HouseholdRmdResult.zero();
        }

        return householdRmdCalculator.calculate(
                plan,
                priorYearEndSnapshot,
                calendarYear,
                governmentRules);
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

    private WithdrawalResult calculatePortfolioWithdrawal(
            BigDecimal guaranteedIncome,
            BigDecimal annualExpenses,
            BigDecimal requiredMinimumDistribution) {

        return withdrawalCalculator
                .calculateWithdrawal(
                        guaranteedIncome,
                        annualExpenses,
                        requiredMinimumDistribution);
    }

    private BigDecimal calculateEndingAssets(
            BigDecimal beginningAssets,
            BigDecimal investmentGrowth,
            WithdrawalDisposition withdrawalDisposition) {

        /*
         * Only money actually spent leaves
         * household investable wealth.
         *
         * An excess RMD may leave a tax-deferred
         * account, but when reinvested in a taxable
         * account it remains part of household
         * investable assets.
         */
        return beginningAssets
                .add(investmentGrowth)
                .subtract(
                        withdrawalDisposition
                                .getSpent())
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }




}