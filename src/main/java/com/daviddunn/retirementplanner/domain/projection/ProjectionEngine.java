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
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculator;

import com.daviddunn.retirementplanner.domain.tax.TaxIncomeCalculator;

import com.daviddunn.retirementplanner.domain.tax.TaxFundingCalculator;
import com.daviddunn.retirementplanner.domain.tax.TaxFundingResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class ProjectionEngine {

    private final WithdrawalCalculator withdrawalCalculator;
    private final HouseholdRmdCalculator householdRmdCalculator;
    private final GovernmentRules governmentRules;
    private final ProjectedWithdrawalAllocator withdrawalAllocator;

    private final TaxFundingCalculator taxFundingCalculator;


    public ProjectionEngine() {

        this.withdrawalCalculator =
                new WithdrawalCalculator();

        this.householdRmdCalculator =
                new HouseholdRmdCalculator();

        this.withdrawalAllocator =
                new ProjectedWithdrawalAllocator();



        this.taxFundingCalculator =
                new TaxFundingCalculator();


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

        WithdrawalStrategy withdrawalStrategy =
                WithdrawalStrategyFactory.create(
                        plan
                                .getPlanningAssumptions()
                                .getWithdrawalAssumptions()
                                .getWithdrawalStrategyType());

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
                            priorYearEndSnapshot,
                            withdrawalStrategy);

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

    private ProjectionYearCalculation calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            ProjectedPortfolio projectedPortfolio,
            RmdBalanceSnapshot priorYearEndSnapshot,
            WithdrawalStrategy withdrawalStrategy) {

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
                        12,
                        31);

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


        ProjectedPortfolio portfolioAfterGrowth =
                projectedPortfolio.withGrowth(
                        investmentGrowth);

        ProjectedWithdrawalAllocation rmdAllocation =
                withdrawalAllocator.allocateHouseholdRmds(
                        portfolioAfterGrowth,
                        householdRmdResult);

        ProjectedPortfolio portfolioAfterRmd =
                rmdAllocation.getPortfolio();

        WithdrawalBreakdown rmdWithdrawalBreakdown =
                rmdAllocation.getWithdrawalBreakdown();

        /*
         * The RMD may satisfy some or all of the
         * household's cash-flow need.
         *
         * Withdraw only the remaining amount that
         * still must come from the portfolio.
         */
        ProjectedWithdrawalAllocation withdrawalAllocation =
                withdrawalAllocator.allocateAdditionalWithdrawal(
                        portfolioAfterRmd,
                        withdrawalResult
                                .getAdditionalWithdrawalRequired(),
                        withdrawalStrategy);

        ProjectedPortfolio portfolioAfterAdditionalWithdrawal =
                withdrawalAllocation.getPortfolio();

        WithdrawalBreakdown withdrawalBreakdown =
                withdrawalAllocation.getWithdrawalBreakdown();

        WithdrawalBreakdown totalWithdrawalBreakdown =
                rmdWithdrawalBreakdown.plus(
                        withdrawalBreakdown);


        TaxFundingResult taxFundingResult =
                taxFundingCalculator.calculate(
                        household,
                        projectionDate,
                        portfolioAfterAdditionalWithdrawal,
                        totalWithdrawalBreakdown,
                        withdrawalStrategy,
                        assumptions
                                .getTaxAssumptions()
                                .getFilingStatus(),
                        governmentRules);

        BigDecimal taxFundingWithdrawal =
                taxFundingResult.getAdditionalWithdrawal();

        BigDecimal totalPortfolioWithdrawal =
                portfolioWithdrawal.add(
                        taxFundingWithdrawal);

        FederalTaxCalculation federalTaxCalculation =
                taxFundingResult.getFederalTaxCalculation();

        ProjectedPortfolio portfolioAfterTaxWithdrawal =
                withdrawalAllocator.applyAdditionalWithdrawal(
                        portfolioAfterAdditionalWithdrawal,
                        taxFundingWithdrawal,
                        withdrawalStrategy);

        /*
         * If the RMD exceeded the household's
         * spending need, the excess remains an
         * investable household asset.
         */
        ProjectedPortfolio endingPortfolio =
                portfolioAfterTaxWithdrawal
                        .withAdditionalCash(
                                withdrawalResult.getExcessRmd());

        List<ProjectedAccountSnapshot> endingAccountSnapshots =
                endingPortfolio
                        .getAccountBalances()
                        .stream()
                        .map(projected ->
                                new ProjectedAccountSnapshot(
                                        projected.getAccount(),
                                        projected.getBalance()))
                        .toList();
        /*
         * Aggregate ending-assets value reported by
         * ProjectionYear.
         *
         * The account-level endingPortfolio is carried
         * forward independently into the next year.
         */
        BigDecimal endingAssets =
                beginningAssets
                        .add(investmentGrowth)
                        .subtract(totalPortfolioWithdrawal)
                        .add(
                                withdrawalResult
                                        .getExcessRmd())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        Person primaryPerson =
                household.getPrimaryPerson();

        int primaryPersonAge =
                primaryPerson.getAge(projectionDate);

        ProjectionYear projectionYear =
                new ProjectionYear(
                        yearOffset,
                        calendarYear,
                        beginningAssets,
                        investmentGrowth,
                        guaranteedIncome,
                        annualExpenses,
                        withdrawalResult.getCashFlowNeed(),
                        totalPortfolioWithdrawal,
                        requiredMinimumDistribution,
                        withdrawalResult.getExcessRmd(),
                        endingAssets,
                        endingAccountSnapshots,
                        federalTaxCalculation,
                        taxFundingWithdrawal,
                        primaryPersonAge);



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
                            person,
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