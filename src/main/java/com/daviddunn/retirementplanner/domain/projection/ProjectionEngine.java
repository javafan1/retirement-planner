package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.estate.AfterTaxEstateCalculator;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;

import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculation;
import com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculator;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdCalculator;
import com.daviddunn.retirementplanner.domain.rmd.HouseholdRmdResult;
import com.daviddunn.retirementplanner.domain.rmd.RmdBalanceSnapshot;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculation;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdCalculator;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public class ProjectionEngine {

    private final WithdrawalCalculator withdrawalCalculator;
    private final HouseholdRmdCalculator householdRmdCalculator;
    private final OpeningRmdCalculator openingRmdCalculator;
    private final GovernmentRules governmentRules;
    private final ProjectedWithdrawalAllocator withdrawalAllocator;

    private final TaxFundingCalculator taxFundingCalculator;
    private final GovernmentRuleProjectionService
            governmentRuleProjectionService;

    private final MedicarePremiumCalculator
            medicarePremiumCalculator;

    private final CompoundGrowthService compoundGrowthService;


    private final SocialSecurityProjectionIncomeProvider
            socialSecurityProjectionIncomeProvider;


    private final ScheduledRothConversionPolicy
            scheduledRothConversionPolicy;

    private final ProjectedPortfolioRothConverter
            projectedPortfolioRothConverter ;



    private final RothConversionBracketFillCalculator
            rothConversionBracketFillCalculator;



    private final RothConversionTargetBracketResolver
            rothConversionTargetBracketResolver;

    private final AfterTaxEstateCalculator afterTaxEstateCalculator =
            new AfterTaxEstateCalculator();


    public ProjectionEngine() {

        this.withdrawalCalculator =
                new WithdrawalCalculator();

        this.householdRmdCalculator =
                new HouseholdRmdCalculator();

        this.openingRmdCalculator =
                new OpeningRmdCalculator();

        this.withdrawalAllocator =
                new ProjectedWithdrawalAllocator();

        this.governmentRuleProjectionService =
                new GovernmentRuleProjectionService();

        this.taxFundingCalculator =
                new TaxFundingCalculator();

        this.medicarePremiumCalculator =
                new MedicarePremiumCalculator();

        this.compoundGrowthService = new CompoundGrowthService();


        this.socialSecurityProjectionIncomeProvider =
                new SocialSecurityProjectionIncomeProvider();



        this.scheduledRothConversionPolicy =
                new ScheduledRothConversionPolicy();

        this.projectedPortfolioRothConverter =
                new ProjectedPortfolioRothConverter();

//        this.federalTaxBracketCalculator =
//                new FederalTaxBracketCalculator();
//
//        this.rothConversionBracketFillStrategy =
//                new RothConversionBracketFillStrategy();

        this.rothConversionBracketFillCalculator =
                new RothConversionBracketFillCalculator();



        this.rothConversionTargetBracketResolver =
                new RothConversionTargetBracketResolver();


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

        return project(plan, ProjectionEvaluationContext.empty());
    }

    public Projection project(
            RetirementPlan plan,
            ProjectionEvaluationContext evaluationContext) {

        java.util.Objects.requireNonNull(plan, "Retirement plan is required.");
        java.util.Objects.requireNonNull(
                evaluationContext,
                "Projection evaluation context is required.");

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

        Map<Integer, HouseholdSocialSecurityResult> socialSecurityByYear =
                socialSecurityProjectionIncomeProvider.calculate(
                        plan,
                        startYear,
                        startYear + projectionLength - 1,
                        evaluationContext);

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
                            withdrawalStrategy,
                            socialSecurityByYear.getOrDefault(
                                    calendarYear,
                                    HouseholdSocialSecurityResult.zero()));

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
            WithdrawalStrategy withdrawalStrategy,
            HouseholdSocialSecurityResult socialSecurityResult) {

        return calculateProjectionYear(
                plan,
                yearOffset,
                calendarYear,
                projectedPortfolio,
                priorYearEndSnapshot,
                withdrawalStrategy,
                socialSecurityResult,
                null);
    }

    private ProjectionYearCalculation calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            ProjectedPortfolio projectedPortfolio,
            RmdBalanceSnapshot priorYearEndSnapshot,
            WithdrawalStrategy withdrawalStrategy,
            HouseholdSocialSecurityResult socialSecurityResult,
            MedicarePremiumCalculation authoritativeMedicarePremium) {

        BigDecimal beginningAssets =
                projectedPortfolio.getTotalBalance();


        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        LocalDate projectionStartDate =
                assumptions.getProjectionStartDate();

        GovernmentRules projectedGovernmentRules =
                governmentRuleProjectionService.project(
                        governmentRules,
                        assumptions,
                        calendarYear);

        BigDecimal investmentGrowth =
                calculateInvestmentGrowth(
                        beginningAssets,
                        assumptions,
                        yearOffset,
                        projectionStartDate);

        BigDecimal beginningRetainedRmdAssets =
                projectedPortfolio.getUnallocatedCash();

        BigDecimal retainedRmdAssetGrowth =
                calculateRetainedRmdAssetGrowth(
                        investmentGrowth,
                        beginningRetainedRmdAssets,
                        beginningAssets);

        BigDecimal accountAssetGrowth =
                investmentGrowth.subtract(
                        retainedRmdAssetGrowth);

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
                        projectionDate,
                        assumptions,
                        socialSecurityResult);

        BigDecimal annualExpenses =
                calculateProjectedExpenses(
                        household,
                        assumptions,
                        yearOffset,
                        projectionDate);

        BigDecimal cashFlowExpenses =
                annualExpenses.add(
                        authoritativeMedicarePremium == null
                                ? BigDecimal.ZERO
                                : authoritativeMedicarePremium
                                        .totalAnnualMedicarePremium());



        /*
         * Calculate the complete household RMD
         * result rather than immediately reducing
         * it to a single dollar amount.
         */
        OpeningRmdCalculation openingRmdCalculation = yearOffset == 0
                ? openingRmdCalculator.calculate(
                        plan,
                        calendarYear,
                        governmentRules)
                : null;

        HouseholdRmdResult annualHouseholdRmdResult =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getAnnualRequirement()
                        : calculateRequiredMinimumDistribution(
                                plan,
                                calendarYear,
                                priorYearEndSnapshot);

        HouseholdRmdResult projectedPeriodRmdResult =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getRemainingRequirement()
                        : annualHouseholdRmdResult;

        BigDecimal rmdDistributedBeforeProjection =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getDistributedBeforeProjection()
                        : BigDecimal.ZERO;

        BigDecimal requiredMinimumDistribution =
                annualHouseholdRmdResult
                        .getTotalRmd()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        WithdrawalResult withdrawalResult =
                withdrawalCalculator
                        .calculateWithdrawal(
                                guaranteedIncome,
                                cashFlowExpenses,
                                projectedPeriodRmdResult
                                        .getTotalRmd());

        BigDecimal portfolioWithdrawal =
                withdrawalResult
                        .getTotalWithdrawal()
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);


//        BigDecimal availableTraditionalBalance =
//                plan
//                        .getAccountPortfolio()
//                        .getEligibleTraditionalBalance(
//                                AccountOwnership.PRIMARY);

        ProjectedPortfolio portfolioAfterGrowth =
                projectedPortfolio.withGrowth(
                        accountAssetGrowth,
                        retainedRmdAssetGrowth);

        ProjectedWithdrawalAllocation rmdAllocation =
                withdrawalAllocator.allocateHouseholdRmds(
                        portfolioAfterGrowth,
                        projectedPeriodRmdResult);

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

        RothConversionRequest rothConversionRequest =
                plan.getRothConversionRequest();

        boolean householdSubjectToRmd =
                annualHouseholdRmdResult
                        .getTotalRmd()
                        .signum() > 0;

        BigDecimal rothConversion =
                BigDecimal.ZERO;

        if (rothConversionRequest != null &&
                scheduledRothConversionPolicy
                        .shouldExecuteConvert(
                                rothConversionRequest,
                                calendarYear,
                                householdSubjectToRmd)) {

            RothConversionStrategy strategy =
                    rothConversionRequest.getStrategy();

            if (strategy ==
                    RothConversionStrategy.FIXED_AMOUNT) {

                rothConversion =
                        rothConversionRequest
                                .getAnnualAmount();

            } else {

                FilingStatus projectionFilingStatus =
                        getProjectionFilingStatus(
                                assumptions,
                                projectionDate);

                BigDecimal targetTaxableIncome;

                if (strategy ==
                        RothConversionStrategy
                                .CUSTOM_TAXABLE_INCOME_TARGET) {

                    targetTaxableIncome =
                            rothConversionRequest
                                    .getCustomTargetTaxableIncome();

                } else {

                    FederalTaxRules federalTaxRules =
                            projectedGovernmentRules
                                    .getFederalTaxRules(
                                            projectionFilingStatus);

                    FederalTaxRules publishedFederalTaxRules =
                            governmentRules
                                    .getFederalTaxRules(
                                            projectionFilingStatus);

                    FederalTaxBracket targetBracket =
                            this.rothConversionTargetBracketResolver
                                    .resolve(
                                            strategy,
                                            publishedFederalTaxRules,
                                            federalTaxRules);

                    targetTaxableIncome =
                            targetBracket.getUpperBound();
                }

                /*
                 * Calculate the Roth conversion required
                 * to reach the selected taxable-income target after
                 * accounting for the tax-funding withdrawal.
                 */
                rothConversion =
                        rothConversionBracketFillCalculator
                                .calculateConversion(
                                        household,
                                        projectionDate,
                                        portfolioAfterAdditionalWithdrawal,
                                        totalWithdrawalBreakdown,
                                        withdrawalStrategy,
                                        projectionFilingStatus,
                                        projectedGovernmentRules,
                                        targetTaxableIncome,
                                        BigDecimal.ZERO,
                                        assumptions
                                                .getSocialSecurityColaRate(),
                                        assumptions
                                                .getDeathScenarioAssumptions(),
                                        rmdDistributedBeforeProjection,
                                        socialSecurityResult);
            }
        }

        /*
         * Strategies determine a household target. Limit it to the amount
         * that can actually move from an eligible source into a same-owner
         * Roth destination before it enters household tax calculations.
         */
        BigDecimal requestedRothConversion = rothConversion;

        rothConversion = rothConversion.min(
                projectedPortfolioRothConverter
                        .getMaximumConvertibleAmount(
                                portfolioAfterAdditionalWithdrawal));

        ProjectedRothConversionResult rothConversionResult =
                projectedPortfolioRothConverter.convertHousehold(
                        portfolioAfterAdditionalWithdrawal,
                        rothConversion);

        rothConversion = rothConversionResult.getTotalConversion();

        ProjectedPortfolio portfolioAfterConversion =
                rothConversionResult.getPortfolio();

        TaxFundingResult taxFundingResult =
                taxFundingCalculator.calculate(
                        household,
                        projectionDate,
                        portfolioAfterConversion,
                        totalWithdrawalBreakdown,
                        withdrawalStrategy,
                        getProjectionFilingStatus(
                                assumptions,
                                projectionDate),
                        projectedGovernmentRules,
                        rothConversion,
                        BigDecimal.ZERO,
                        assumptions.getSocialSecurityColaRate(),
                        assumptions.getDeathScenarioAssumptions(),
                        rmdDistributedBeforeProjection,
                        socialSecurityResult);

        BigDecimal taxFundingWithdrawal =
                taxFundingResult.getAdditionalWithdrawal();

        BigDecimal totalPortfolioWithdrawal =
                portfolioWithdrawal.add(
                        taxFundingWithdrawal);

        FederalTaxCalculation federalTaxCalculation =
                taxFundingResult.getFederalTaxCalculation();

        MichiganTaxCalculation michiganTaxCalculation =
                taxFundingResult.getMichiganTaxCalculation();

        int coveredIndividuals =
                calculateCoveredMedicareParticipants(
                        household,
                        projectionDate);


        FilingStatus projectionFilingStatus =
                getProjectionFilingStatus(
                        assumptions,
                        projectionDate);

        MedicarePremiumCalculation medicarePremiumCalculation =
                authoritativeMedicarePremium == null
                        ? medicarePremiumCalculator.calculate(
                                federalTaxCalculation,
                                projectionFilingStatus,
                                projectedGovernmentRules,
                                coveredIndividuals)
                        : authoritativeMedicarePremium;

        if (authoritativeMedicarePremium == null
                && medicarePremiumCalculation
                        .totalAnnualMedicarePremium()
                        .signum() > 0) {
            return calculateProjectionYear(
                    plan,
                    yearOffset,
                    calendarYear,
                    projectedPortfolio,
                    priorYearEndSnapshot,
                    withdrawalStrategy,
                    socialSecurityResult,
                    medicarePremiumCalculation);
        }

        ProjectedPortfolio portfolioAfterTaxWithdrawal =
                withdrawalAllocator.applyAdditionalWithdrawal(
                        portfolioAfterConversion,
                        taxFundingWithdrawal,
                        withdrawalStrategy);

        /*
         * If the RMD exceeded the household's
         * spending need, the excess remains an
         * investable household asset.
         */
//        ProjectedPortfolio endingPortfolio =
//                portfolioAfterTaxWithdrawal
//                        .withAdditionalCash(
//                                withdrawalResult.getExcessRmd());

        ProjectedPortfolio endingPortfolio =
                portfolioAfterTaxWithdrawal
                        .withAdditionalCash(
                                withdrawalResult
                                        .getExcessRmd());

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

        LocalDate projectionYearEnd =
                LocalDate.of(
                        calendarYear,
                        12,
                        31);

        int primaryPersonAge =
                primaryPerson.getAge(projectionYearEnd);

//        AfterTaxEstateCalculator afterTaxEstateCalculator =
//                new AfterTaxEstateCalculator();

        BigDecimal heirTaxRate =
                plan.getPlanningAssumptions()
                        .getTaxAssumptions()
                        .getEstimatedHeirTaxRateOnTaxDeferredAssets();

        BigDecimal estimatedHeirTax =
                afterTaxEstateCalculator.calculateEstimatedTax(
                        endingAccountSnapshots,
                        heirTaxRate);

        BigDecimal afterTaxEstateValue =
                afterTaxEstateCalculator.calculateAfterTaxEstateValue(
                        endingAssets,
                        estimatedHeirTax);


        ProjectionYear projectionYear =
                new ProjectionYear(
                        yearOffset,
                        calendarYear,
                        beginningAssets,
                        investmentGrowth,
                        guaranteedIncome,
                        socialSecurityResult,
                        annualExpenses,
                        withdrawalResult.getCashFlowNeed(),
                        totalPortfolioWithdrawal,
                        requiredMinimumDistribution,
                        rmdDistributedBeforeProjection,
                        projectedPeriodRmdResult.getTotalRmd(),
                        withdrawalResult.getExcessRmd(),
                        beginningRetainedRmdAssets,
                        retainedRmdAssetGrowth,
                        endingPortfolio.getUnallocatedCash(),
                        endingAssets,
                        endingAccountSnapshots,
                        federalTaxCalculation,
                        michiganTaxCalculation,
                        medicarePremiumCalculation,
                        taxFundingWithdrawal,
                        requestedRothConversion,
                        rothConversion,
                        rothConversionResult.getConversion(
                                AccountOwnership.PRIMARY),
                        rothConversionResult.getConversion(
                                AccountOwnership.SPOUSE),
                        estimatedHeirTax,
                        afterTaxEstateValue,
                        primaryPersonAge);

        return new ProjectionYearCalculation(
                projectionYear,
                endingPortfolio);
    }

    private static com.daviddunn.retirementplanner.domain.rules.FilingStatus getFilingStatus(PlanningAssumptions assumptions) {
        return assumptions
                .getTaxAssumptions()
                .getFilingStatus();
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

    private BigDecimal calculateRetainedRmdAssetGrowth(
            BigDecimal totalInvestmentGrowth,
            BigDecimal beginningRetainedRmdAssets,
            BigDecimal beginningInvestableAssets) {

        if (beginningRetainedRmdAssets.signum() == 0
                || beginningInvestableAssets.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return totalInvestmentGrowth
                .multiply(beginningRetainedRmdAssets)
                .divide(
                        beginningInvestableAssets,
                        12,
                        RoundingMode.HALF_UP)
                .setScale(
                        2,
                        RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTotalIncome(
            Household household,
            LocalDate projectionDate,
            PlanningAssumptions assumptions,
            HouseholdSocialSecurityResult socialSecurityResult) {

        BigDecimal total =
                BigDecimal.ZERO;

        total = total.add(
                calculateIncome(
                        household.getPrimaryPerson(),
                        projectionDate,
                        assumptions));

        total = total.add(
                calculateIncome(
                        household.getSpouse(),
                        projectionDate,
                        assumptions));

        total = total.add(
                socialSecurityResult.householdBenefit());

        total = total.add(
                calculateSurvivorPensionIncome(
                        household,
                        projectionDate,
                        assumptions));

        return total;
    }

    private BigDecimal calculateSurvivorPensionIncome(
            Household household,
            LocalDate projectionDate,
            PlanningAssumptions assumptions) {

        DeathScenario deathScenario =
                assumptions
                        .getDeathScenarioAssumptions()
                        .getDeathScenario();

        if (deathScenario == DeathScenario.BOTH_SURVIVE) {
            return BigDecimal.ZERO;
        }

        Person deceasedPerson;

        if (deathScenario == DeathScenario.PRIMARY_DIES) {

            deceasedPerson =
                    household.getPrimaryPerson();

        } else {

            deceasedPerson =
                    household.getSpouse();
        }

        /*
         * Survivor benefits do not begin until the
         * death scenario is active.
         */
        if (!assumptions
                .getDeathScenarioAssumptions()
                .isDeathScenarioActive(
                        projectionDate.getYear())) {

            return BigDecimal.ZERO;
        }

        BigDecimal total =
                BigDecimal.ZERO;

        /*
         * Find all pensions belonging to the deceased
         * person that provide survivor benefits.
         */
        for (IncomeSource income :
                deceasedPerson.getIncomeSources()) {

            if (income instanceof Pension pension) {

                total = total.add(
                        pension.getAnnualSurvivorIncome(
                                projectionDate));
            }
        }

        return total;
    }

    private BigDecimal calculateIncome(
            Person person,
            LocalDate projectionDate,
            PlanningAssumptions assumptions) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            if (income instanceof SocialSecurityIncome) {
                continue;
            }

            if (!assumptions
                    .getDeathScenarioAssumptions()
                    .isIncomeActive(
                            income.getOwnership(),
                            projectionDate.getYear())) {

                continue;
            }

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
            LocalDate projectionDate) {

        BigDecimal totalExpenses =
                BigDecimal.ZERO;

        for (Expense expense :
                household.getExpenses()) {

            if (!expense.isActiveDuringYear(
                    projectionDate.getYear(),
                    assumptions.getProjectionStartDate())) {

                continue;
            }

            BigDecimal projectedExpense =
                    calculateProjectedExpense(
                            expense,
                            assumptions,
                            yearOffset);

            /*
             * Only the first projection year may
             * represent a partial calendar year.
             *
             * For Version 1, all active recurring
             * expenses are prorated equally.
             */
            if (yearOffset == 0
                    && expense.getExpenseType()
                    == ExpenseType.RECURRING) {

                int activeMonths =
                        13 -
                                projectionDate
                                        .getMonthValue();

                projectedExpense =
                        projectedExpense
                                .multiply(
                                        BigDecimal.valueOf(
                                                activeMonths))
                                .divide(
                                        BigDecimal.valueOf(12),
                                        2,
                                        RoundingMode.HALF_UP);
            }

            /*
             * Apply the post-death expense adjustment
             * only to recurring expenses.
             *
             * One-time expenses are intentionally
             * excluded from this adjustment.
             */
            if (expense.getExpenseType()
                    == ExpenseType.RECURRING
                    && assumptions
                    .getDeathScenarioAssumptions()
                    .isDeathScenarioActive(
                            projectionDate.getYear())) {

                projectedExpense =
                        projectedExpense.multiply(
                                assumptions
                                        .getDeathScenarioAssumptions()
                                        .getPostDeathExpenseFactor());
            }

            totalExpenses =
                    totalExpenses.add(
                            projectedExpense);
        }

        return totalExpenses.setScale(
                2,
                RoundingMode.HALF_UP);
    }



    private BigDecimal getExpenseGrowthRate(
            Expense expense,
            PlanningAssumptions assumptions) {

        return expense.isHealthcareExpense()
                ? assumptions.getHealthcareInflationRate()
                : assumptions.getGeneralInflationRate();
    }

    private BigDecimal calculateProjectedExpense(
            Expense expense,
            PlanningAssumptions assumptions,
            int yearOffset) {



        BigDecimal growthRate =
                getExpenseGrowthRate(
                        expense,
                        assumptions);

        return compoundGrowthService.project(
                expense.getAnnualAmount(),
                growthRate,
                yearOffset);
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
//
//    private int calculateCoveredMedicareParticipants(
//            Household household,
//            LocalDate projectionDate) {
//
//        int participants = 0;
//
//        if (household.getPrimaryPerson()
//                .getAge(projectionDate) >= 65) {
//
//            participants++;
//        }
//
//        if (household.getSpouse()
//                .getAge(projectionDate) >= 65) {
//
//            participants++;
//        }
//
//        return participants;
//    }

    private int calculateCoveredMedicareParticipants(
            Household household,
            LocalDate projectionDate) {

        int participants = 0;

        Person primary = household.getPrimaryPerson();

        if (primary.getBirthDate() != null &&
                primary.getAge(projectionDate) >= 65) {
            participants++;
        }

        Person spouse = household.getSpouse();

        if (spouse.getBirthDate() != null &&
                spouse.getAge(projectionDate) >= 65) {
            participants++;
        }

        return participants;
    }


    private static FilingStatus getProjectionFilingStatus(
            PlanningAssumptions assumptions,
            LocalDate projectionDate) {

        DeathScenarioAssumptions deathScenario =
                assumptions
                        .getDeathScenarioAssumptions();

        /*
         * Before the death scenario becomes active,
         * use the filing status configured by the user.
         */
        if (!deathScenario.isDeathScenarioActive(
                projectionDate.getYear())) {

            return getFilingStatus(assumptions);
        }

        /*
         * The death year retains the configured
         * filing status.
         */
        if (projectionDate.getYear()
                == deathScenario.getDeathYear()) {

            return getFilingStatus(assumptions);
        }

        /*
         * Beginning the year after death, the
         * surviving spouse files as Single.
         */
        return FilingStatus.SINGLE;
    }


}
