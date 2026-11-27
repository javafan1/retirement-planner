package com.daviddunn.retirementplanner.domain.projection;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.TaxCharacter;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.estate.AfterTaxEstateCalculator;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxBracket;
import com.daviddunn.retirementplanner.domain.rules.FederalTaxRules;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult;

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
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdMembership;
import com.daviddunn.retirementplanner.domain.retirementownership.Spousal401kRollover;
import com.daviddunn.retirementplanner.domain.rmd.SpousalIraRmdUnavailable;
import static com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution.Purpose.TAX_FUNDING;
import com.daviddunn.retirementplanner.domain.tax.*;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasRecipientDiagnostic;
import com.daviddunn.retirementplanner.domain.tax.state.ProductionStateTaxResult;
import com.daviddunn.retirementplanner.domain.tax.state.TaxHouseholdContext;
import com.daviddunn.retirementplanner.domain.withdrawal.*;
import com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository;


import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.income.HouseholdPensionIncomeCalculator.PensionIncomeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;


public class ProjectionEngine {

    /** Shared admission checks, including runs ending at opening without annual execution. */
    public static void validateSinglePersonProjection(RetirementPlan plan, ProjectionEvaluationContext context) {
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireDeterministicSupported(plan);
        if (plan.getHousehold().hasSpouse()) return;
        if (context.survivorClaimingAge().isPresent()
                || context.socialSecurityStrategy().map(strategy -> strategy.hasSpouse()).orElse(false)
                || context.householdLifetimeScenario().flatMap(HouseholdLifetimeScenario::spouseDeathYear).isPresent()) {
            throw new IllegalArgumentException("A single-person projection cannot contain spouse or survivor assumptions.");
        }
        java.util.Objects.requireNonNull(plan.getHousehold().getPrimaryPerson().getBirthDate(), "Primary birth date is required.");
        for (var account : plan.getAccountPortfolio().getAccounts()) {
            if (account.getOwnership() == AccountOwnership.JOINT) {
                throw new IllegalArgumentException("Joint accounts require a spouse; resolve ownership explicitly before projection.");
            }
        }
        for (var income : plan.getHousehold().getPrimaryPerson().getIncomeSources()) {
            if (income.getOwnership() != AccountOwnership.PRIMARY) {
                throw new IllegalArgumentException("Single-person income must be primary-owned.");
            }
            if (income instanceof Pension pension && pension.getSurvivorMonthlyBenefit() != null
                    && pension.getSurvivorMonthlyBenefit().signum() > 0) {
                throw new IllegalArgumentException("A survivor pension election requires a spouse.");
            }
        }
    }

    private final HouseholdPensionIncomeCalculator householdPensionIncomeCalculator =
            new HouseholdPensionIncomeCalculator();
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


    public static final String ALGORITHM_VERSION = "v1";

    private final ArkansasProjectionValidation arkansasValidation;
    private final boolean arkansasProduction;
    private final ThreadLocal<ArkansasRunObserver> arkansasObserver = new ThreadLocal<>();
    private final ThreadLocal<ArkansasRunControl> arkansasRunControl = new ThreadLocal<>();

    private record ArkansasRunControl(
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.IntConsumer completedYears,
            Optional<Consumer<ArkansasLowIncomeDiagnostic>> lowIncomeDiagnostic, boolean deterministic) { }

    /** Control applies only to an already gated engine. This method cannot grant the capability. */
    public final ProjectionExecutionResult projectArkansasWithOutcome(RetirementPlan frozenPlan,
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.IntConsumer completedYears) {
        return projectArkansasWithOutcome(frozenPlan, cancellation, completedYears, Optional.empty());
    }

    /** Opt-in sink for one deterministic run. Only an escaping exact low-income refusal is reported. */
    public final ProjectionExecutionResult projectArkansasWithOutcome(RetirementPlan frozenPlan,
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.IntConsumer completedYears,
            Optional<Consumer<ArkansasLowIncomeDiagnostic>> lowIncomeDiagnostic) {
        return withArkansasRunControl(cancellation, completedYears, lowIncomeDiagnostic, true, () -> projectWithOutcome(frozenPlan));
    }

    /** Runs an Arkansas analysis on its existing worker with cancellation inside annual proofs. */
    public final <T> T runArkansasAnalysis(
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.Supplier<T> work) {
        return withArkansasRunControl(cancellation, ignored -> { }, Optional.empty(), false, work);
    }

    private <T> T withArkansasRunControl(
            com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken cancellation,
            java.util.function.IntConsumer completedYears,
            Optional<Consumer<ArkansasLowIncomeDiagnostic>> lowIncomeDiagnostic,
            boolean deterministic,
            java.util.function.Supplier<T> work) {
        if (arkansasValidation == null || arkansasRunControl.get() != null) {
            throw new IllegalStateException("Arkansas projection requires an isolated gated engine and one calculation scope.");
        }
        var control = new ArkansasRunControl(java.util.Objects.requireNonNull(cancellation),
                java.util.Objects.requireNonNull(completedYears), java.util.Objects.requireNonNull(lowIncomeDiagnostic), deterministic);
        arkansasRunControl.set(control);
        try {
            cancellation.throwIfCancellationRequested();
            var result = work.get();
            cancellation.throwIfCancellationRequested();
            return result;
        } finally {
            arkansasRunControl.remove();
        }
    }

    @FunctionalInterface
    public interface ArkansasRunObserver {
        void accept(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionExecutionResult result);
    }

    /** Collects completed and funded-prefix facts on the calling thread of a controlled analysis. */
    public <T> T observeArkansasRuns(
            ArkansasRunObserver observer,
            java.util.function.Supplier<T> analysis) {
        if (arkansasValidation == null || arkansasObserver.get() != null) {
            throw new IllegalStateException("Arkansas analysis requires an isolated gated engine and one observation scope.");
        }
        arkansasObserver.set(java.util.Objects.requireNonNull(observer));
        try {
            return analysis.get();
        } finally {
            arkansasObserver.remove();
        }
    }

    public ProjectionEngine() {
        this(new TaxFundingCalculator());
    }

    public static boolean selectsArkansas(RetirementPlan plan) {
        return plan != null && plan.getPlanningAssumptions() != null
                && plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                == com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AR;
    }

    /** Production evidence comes only from the frozen plan and executed annual ledgers. */
    public static ProjectionEngine forArkansasProduction() {
        return new ProjectionEngine(new TaxFundingCalculator(),
                new ArkansasProjectionValidation((year, pensions) -> ArkansasProjectionValidation.AnnualEvidence.EMPTY), true);
    }

    /** Supplies the same annual funding boundary for deterministic and structured evaluations. */
    public ProjectionEngine(TaxFundingCalculator taxFundingCalculator) {
        this(taxFundingCalculator, null);
    }

    ProjectionEngine(TaxFundingCalculator taxFundingCalculator, ArkansasProjectionValidation arkansasValidation) {
        this(taxFundingCalculator, arkansasValidation, false);
    }

    private ProjectionEngine(TaxFundingCalculator taxFundingCalculator, ArkansasProjectionValidation arkansasValidation,
            boolean arkansasProduction) {
        this.arkansasValidation = arkansasValidation;
        this.arkansasProduction = arkansasProduction;

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
                java.util.Objects.requireNonNull(taxFundingCalculator);

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
        return project(plan, evaluationContext, ProjectionEconomicPath.constant(
                plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn()));
    }

    /** Same financial engine with caller-supplied, immutable annual investment returns. */
    public Projection project(
            RetirementPlan plan,
            ProjectionEvaluationContext evaluationContext,
            ProjectionEconomicPath economicPath) {

        return ((ProjectionExecutionResult.Completed) executeInternal(
                plan, evaluationContext, economicPath, false)).projection();
    }

    public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) {
        return projectWithOutcome(plan, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.constant(
                plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn()));
    }

    public static boolean supportsVersionedRothScheduling(RetirementPlan plan) {
        if (plan == null || plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction()
                != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.FL
                || com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.resolved(plan)
                        != com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.BASIC) return false;
        var assumptions = plan.getPlanningAssumptions();
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), assumptions.getDeathScenarioAssumptions(),
                Optional.empty());
        int finalYear = assumptions.getProjectionStartDate().getYear() + assumptions.getProjectionLengthYears() - 1;
        return deaths.firstDeathYear().filter(year -> year.getValue() <= finalYear).isEmpty()
                || requestsAppliedSpousalIraRmd(plan);
    }

    /** Explicit normal workflow. Contextual and analyzer entry points retain their versioned-request refusal. */
    public ProjectionExecutionResult projectNormalWithRothScheduling(RetirementPlan plan) {
        if (!RothConversionRequest.usesVersionedExecution(plan.getRothConversionRequest())
                || !supportsVersionedRothScheduling(plan) || arkansasValidation != null || arkansasProduction) {
            throw new RothConversionRequest.SchedulingUnavailable();
        }
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireDeterministicSupported(plan);
        boolean ownerEmployer = requestsSpousalPostRbd401kOwner(plan);
        boolean beneficiaryEmployer = requestsSpousalPostRbd401k(plan);
        boolean deathYearIra = requestsSpousalIraDeathYear(plan);
        SpousalIraRmdPolicy policy = null;
        if (requestsAppliedSpousalIraRmd(plan)) {
            var treatment = ownerEmployer ? SpousalIraRmdPolicy.Treatment.OWNER_ROLLOVER
                    : beneficiaryEmployer ? SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K
                    : deathYearIra ? deathYearTreatment(plan) : successorOrdinaryTreatment(plan);
            policy = new SpousalIraRmdPolicy(plan, governmentRules.getRmdRules(), treatment, true,
                    ownerEmployer || beneficiaryEmployer || deathYearIra);
        }
        return runProjection(plan, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.constant(
                plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn()), true, policy, true);
    }

    /** Only the top-level deterministic workflow dispatches to this financial policy. */
    public static boolean requestsSpousalIraRmd(RetirementPlan plan) {
        return SpousalIraRmdPolicy.requested(plan);
    }

    public static boolean requestsSpousal401kRollover(RetirementPlan plan) {
        return SpousalIraRmdPolicy.rolloverRequested(plan);
    }

    public static boolean requestsSpousalBeneficiaryIraRmd(RetirementPlan plan) {
        return SpousalIraRmdPolicy.beneficiaryRequested(plan);
    }

    public static boolean requestsSpousalBeneficiary401kRmd(RetirementPlan plan) {
        return SpousalIraRmdPolicy.beneficiary401kRequested(plan);
    }

    public static boolean requestsAppliedSpousalIraRmd(RetirementPlan plan) {
        return requestsSpousalIraRmd(plan) || requestsSpousal401kRollover(plan) || requestsSpousalBeneficiaryIraRmd(plan)
                || requestsSpousalBeneficiary401kRmd(plan);
    }

    /** A detached ordinary deterministic input, without analyzer or economic path overrides. */
    public ProjectionExecutionResult projectSpousalIraRmd(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, SpousalIraRmdPolicy.Treatment.OWNER_IRA);
    }

    public ProjectionExecutionResult projectSpousal401kRollover(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, SpousalIraRmdPolicy.Treatment.OWNER_ROLLOVER);
    }

    public ProjectionExecutionResult projectSpousalBeneficiaryIraRmd(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, SpousalIraRmdPolicy.Treatment.BENEFICIARY_IRA);
    }

    public ProjectionExecutionResult projectSpousalBeneficiary401kRmd(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K);
    }

    public static boolean requestsSpousalIraDeathYear(RetirementPlan plan) {
        if (!requestsSpousalIraRmd(plan) && !requestsSpousalBeneficiaryIraRmd(plan)) return false;
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(), Optional.empty());
        return plan.getHousehold().peopleByOwner().entrySet().stream().anyMatch(entry -> {
            var birth = entry.getValue().getBirthDate();
            return birth != null && birth.getYear() >= 1960
                    && deaths.deathDate(entry.getKey()).filter(date -> date.getYear() >= birth.getYear() + 75).isPresent();
        });
    }

    public static boolean requestsSpousalPostRbd401k(RetirementPlan plan) {
        return requestsSpousalBeneficiary401kRmd(plan) && modeledEmployerPostRbd(plan);
    }

    public static boolean requestsSpousalPostRbd401kOwner(RetirementPlan plan) {
        return requestsSpousal401kRollover(plan) && modeledEmployerPostRbd(plan);
    }

    private static boolean modeledEmployerPostRbd(RetirementPlan plan) {
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(), Optional.empty());
        return plan.getHousehold().peopleByOwner().entrySet().stream().anyMatch(entry -> {
            var birth = entry.getValue().getBirthDate();
            return birth != null && birth.getYear() >= 1960
                    && deaths.deathDate(entry.getKey())
                            .filter(date -> date.isAfter(LocalDate.of(birth.getYear() + 76, 4, 1))).isPresent();
        });
    }

    public ProjectionExecutionResult projectSpousalPostRbd401kOwner(RetirementPlan plan) {
        return projectSpousalIraRmd(plan, SpousalIraRmdPolicy.Treatment.OWNER_ROLLOVER, true, true);
    }

    public record PostRbd401kOwnerHistory(
            java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership> owner,
            java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership> beforeRollover) { }

    public PostRbd401kOwnerHistory spousalPostRbd401kOwnerHistory(RetirementPlan plan,
            Map<IncomeSourceAttribution.AccountReference, Spousal401kRollover> rollovers) {
        var policy = new SpousalIraRmdPolicy(plan, governmentRules.getRmdRules(),
                SpousalIraRmdPolicy.Treatment.OWNER_ROLLOVER, true, true);
        var verified = Map.copyOf(rollovers);
        return new PostRbd401kOwnerHistory(year -> policy.ownership(year, verified), policy::rolloverPaymentOwnership);
    }

    public ProjectionExecutionResult projectSpousalPostRbd401k(RetirementPlan plan) {
        return projectSpousalIraRmd(plan, SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K, true, true);
    }

    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousalPostRbd401kOwnership(RetirementPlan frozenPlan) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(),
                SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K, true, true);
        return policy::ownership;
    }

    public static boolean requiresSpousalOpeningHistory(RetirementPlan plan,
            com.daviddunn.retirementplanner.domain.financial.Account account) {
        if (!(account.getType() == AccountType.TRADITIONAL_IRA && requestsSpousalIraDeathYear(plan)
                || account.getType() == AccountType.TRADITIONAL_401K && (requestsSpousalPostRbd401k(plan) || requestsSpousalPostRbd401kOwner(plan)))) return false;
        var assumptions = plan.getPlanningAssumptions();
        var deaths = EffectiveHouseholdDeathView.resolve(plan.getHousehold(), assumptions.getDeathScenarioAssumptions(),
                Optional.empty());
        var person = plan.getHousehold().peopleByOwner().get(account.getOwnership());
        return person != null && deaths.deathDate(account.getOwnership()).isPresent()
                && assumptions.getProjectionStartDate().getYear() > person.getBirthDate().getYear() + 75;
    }

    public ProjectionExecutionResult projectSpousalIraDeathYear(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, deathYearTreatment(frozenPlan), true, true);
    }

    private static SpousalIraRmdPolicy.Treatment deathYearTreatment(RetirementPlan plan) {
        return requestsSpousalBeneficiaryIraRmd(plan) ? SpousalIraRmdPolicy.Treatment.BENEFICIARY_IRA
                : SpousalIraRmdPolicy.Treatment.OWNER_IRA;
    }

    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousalIraDeathYearOwnership(RetirementPlan frozenPlan) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(),
                deathYearTreatment(frozenPlan), true, true);
        return policy::ownership;
    }

    public ProjectionExecutionResult projectSpousalSuccessorOrdinary(RetirementPlan frozenPlan) {
        return projectSpousalIraRmd(frozenPlan, successorOrdinaryTreatment(frozenPlan), true);
    }

    private static SpousalIraRmdPolicy.Treatment successorOrdinaryTreatment(RetirementPlan frozenPlan) {
        return requestsSpousalBeneficiary401kRmd(frozenPlan) ? SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K
                : requestsSpousalBeneficiaryIraRmd(frozenPlan) ? SpousalIraRmdPolicy.Treatment.BENEFICIARY_IRA
                : requestsSpousal401kRollover(frozenPlan) ? SpousalIraRmdPolicy.Treatment.OWNER_ROLLOVER
                : SpousalIraRmdPolicy.Treatment.OWNER_IRA;
    }

    private ProjectionExecutionResult projectSpousalIraRmd(RetirementPlan frozenPlan, SpousalIraRmdPolicy.Treatment treatment) {
        return projectSpousalIraRmd(frozenPlan, treatment, false);
    }

    private ProjectionExecutionResult projectSpousalIraRmd(RetirementPlan frozenPlan,
            SpousalIraRmdPolicy.Treatment treatment, boolean ordinaryEnabled) {
        return projectSpousalIraRmd(frozenPlan, treatment, ordinaryEnabled, false);
    }

    private ProjectionExecutionResult projectSpousalIraRmd(RetirementPlan frozenPlan,
            SpousalIraRmdPolicy.Treatment treatment, boolean ordinaryEnabled, boolean deathYearPolicy) {
        if (arkansasValidation != null) throw new IllegalStateException("Spousal IRA RMD treatment requires Florida Basic.");
        com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitAdmission.requireDeterministicSupported(frozenPlan);
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(), treatment, ordinaryEnabled, deathYearPolicy);
        return runProjection(frozenPlan, ProjectionEvaluationContext.empty(), ProjectionEconomicPath.constant(
                frozenPlan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn()), true, policy);
    }

    /** Rebinds authorized rows to the original accounts of a detached, validated historical graph. */
    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousalIraRmdOwnership(RetirementPlan frozenPlan) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules());
        return policy::ownership;
    }

    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousal401kRolloverOwnership(RetirementPlan frozenPlan,
                Map<IncomeSourceAttribution.AccountReference,
                        com.daviddunn.retirementplanner.domain.retirementownership.Spousal401kRollover> rollovers) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(), true);
        var verifiedRollovers = Map.copyOf(rollovers);
        return year -> policy.ownership(year, verifiedRollovers);
    }

    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousalBeneficiaryIraOwnership(RetirementPlan frozenPlan) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(), SpousalIraRmdPolicy.Treatment.BENEFICIARY_IRA);
        return policy::ownership;
    }

    public java.util.function.IntFunction<com.daviddunn.retirementplanner.domain.retirementownership.AnnualRetirementOwnership>
            spousalBeneficiary401kOwnership(RetirementPlan frozenPlan) {
        var policy = new SpousalIraRmdPolicy(frozenPlan, governmentRules.getRmdRules(), SpousalIraRmdPolicy.Treatment.BENEFICIARY_401K);
        return policy::ownership;
    }

    /** Known funding constraints become outcomes; unrelated errors still throw. */
    public ProjectionExecutionResult projectWithOutcome(
            RetirementPlan plan,
            ProjectionEvaluationContext context,
            ProjectionEconomicPath economicPath) {
        return executeInternal(plan, context, economicPath, true);
    }

    private ProjectionExecutionResult executeInternal(
            RetirementPlan plan,
            ProjectionEvaluationContext evaluationContext,
            ProjectionEconomicPath economicPath,
            boolean structured) {
        RothConversionRequest.requireExecutable(plan.getRothConversionRequest());
        if (arkansasProduction && (!selectsArkansas(plan) || arkansasRunControl.get() == null)) {
            throw new IllegalStateException("Arkansas production requires Arkansas inputs and a controlled calculation scope.");
        }
        if (selectsArkansas(plan) && !arkansasProduction) {
            throw new ArkansasProjectionUnavailableException(plan.getPlanningAssumptions().getProjectionStartDate().getYear(),
                    ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX,
                    "This action requires the controlled Arkansas projection route.",
                    com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.of(
                            com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.Kind.POLICY), java.util.List.of());
        }
        ProjectionExecutionResult execution;
        try {
            if (arkansasProduction) {
                boolean analysisContext = arkansasObserver.get() != null
                        || !ProjectionEvaluationContext.empty().equals(evaluationContext);
                if (analysisContext && ConditionalBenefitAdmission.hasArkansasConditionalBenefits(plan)
                        || ConditionalBenefitAdmission.usesArkansasRegularTaxOnly(plan)
                            && (analysisContext || !arkansasRunControl.get().deterministic())) {
                    ConditionalBenefitAdmission.requireAnalyzerSupported(plan);
                }
            }
            execution = runProjection(plan, evaluationContext, economicPath, structured, null);
        } catch (com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException failure) {
            if (arkansasValidation == null) throw failure;
            throw arkansasNiitUnavailable(plan.getPlanningAssumptions().getProjectionStartDate().getYear(), failure);
        }
        if (arkansasValidation != null) {
            var observer = arkansasObserver.get();
            if (observer != null) observer.accept(plan, evaluationContext, execution);
        }
        return execution;
    }

    private ProjectionExecutionResult runProjection(
            RetirementPlan plan, ProjectionEvaluationContext evaluationContext,
            ProjectionEconomicPath economicPath, boolean structured, SpousalIraRmdPolicy spousalIra) {
        return runProjection(plan, evaluationContext, economicPath, structured, spousalIra, false);
    }

    private ProjectionExecutionResult runProjection(RetirementPlan plan, ProjectionEvaluationContext evaluationContext,
            ProjectionEconomicPath economicPath, boolean structured, SpousalIraRmdPolicy spousalIra, boolean versionedRoth) {
        java.util.Objects.requireNonNull(plan, "Retirement plan is required.");
        if (!versionedRoth) RothConversionRequest.requireExecutable(plan.getRothConversionRequest());
        plan.validateHouseholdReferences();
        validateSinglePersonProjection(plan, evaluationContext);
        java.util.Objects.requireNonNull(economicPath, "Economic path is required.");
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

        int endingYear = evaluationContext.resolveEndingYear(startYear,
                Math.addExact(startYear, assumptions.getProjectionLengthYears() - 1));
        int projectionLength = Math.addExact(Math.subtractExact(endingYear, startYear), 1);
        economicPath.requireCoverage(startYear, endingYear);
        evaluationContext.inflationPath().ifPresent(path -> path.requireCoverage(startYear, endingYear));
        evaluationContext.expensePath().ifPresent(path -> path.validateBinding(
                plan.getHousehold().getExpenses(), startYear, endingYear));

        EffectiveHouseholdDeathView deathView = EffectiveHouseholdDeathView.resolve(
                plan.getHousehold(), assumptions.getDeathScenarioAssumptions(), evaluationContext.householdLifetimeScenario());

        Map<Integer, HouseholdSocialSecurityResult> socialSecurityByYear =
                socialSecurityProjectionIncomeProvider.calculate(
                        plan,
                        startYear,
                        endingYear,
                        evaluationContext,
                        deathView);

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

        boolean lifetimeRun = evaluationContext.householdLifetimeScenario().isPresent();
        var benefitDefinitions = plan.getConditionalBenefits();
        var financialMode = plan.getFinancialCalculationMode();
        boolean annuityExecution = com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.modelsNiit(plan);
        boolean annuityOpeningCash = annuityExecution && com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityAdmission.hasOpeningNonqualifiedCash(plan.getAccountPortfolio());
        var benefitPrimaryBirth = plan.getHousehold().getPrimaryPerson().getBirthDate();
        var benefitSpouseBirth = plan.getHousehold().spouse().map(Person::getBirthDate);
        var benefitOpening = plan.getPlanningAssumptions().getProjectionStartDate();
        boolean householdRmdHasOccurred = false;
        boolean versionedRmdHasOccurred = false;
        BigDecimal generalExpenseMultiplier = BigDecimal.ONE;
        var controlledRun = arkansasValidation == null ? null : arkansasRunControl.get();
        Map<IncomeSourceAttribution.AccountReference,
                com.daviddunn.retirementplanner.domain.retirementownership.Spousal401kRollover> rollovers = Map.of();
        for (int yearOffset = 0;
             yearOffset < projectionLength;
             yearOffset++) {
            if (controlledRun != null) controlledRun.cancellation().throwIfCancellationRequested();
            checkProtectedProjectionCancellation(spousalIra, versionedRoth);

            int calendarYear =
                    startYear + yearOffset;

            if (yearOffset > 0 && evaluationContext.inflationPath().isPresent()) {
                generalExpenseMultiplier = generalExpenseMultiplier.multiply(BigDecimal.ONE.add(
                        evaluationContext.inflationPath().orElseThrow().inflationForYear(calendarYear)));
            }

            ProjectionYearCalculation calculation;
            var conditionalBenefits = benefitDefinitions.isEmpty()
                    ? com.daviddunn.retirementplanner.domain.benefit.AnnualBenefitEvaluation.EMPTY
                    : new com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitEvaluator().evaluate(benefitDefinitions,
                            new com.daviddunn.retirementplanner.domain.benefit.BenefitEvaluationContext(benefitPrimaryBirth,benefitSpouseBirth,deathView,benefitOpening,calendarYear));
            try {
                var annualReturn = economicPath.investmentReturnForYear(calendarYear);
                var mandatory = spousalIra == null ? null
                        : spousalIra.mandatoryBeforeRollover(calendarYear, projectedPortfolio, priorYearEndSnapshot);
                OpeningEmployerPayment openingPayment = null;
                if (mandatory != null) {
                    var obligations = mandatory.beneficiaryEmployerObligations();
                    var payment = withdrawalAllocator.allocateHouseholdRmds(projectedPortfolio,
                            new HouseholdRmdResult(HouseholdRmdResult.zero().ownerResults(), null, obligations), mandatory);
                    openingPayment = new OpeningEmployerPayment(payment, obligations);
                }
                var afterOpeningPayment = openingPayment == null ? projectedPortfolio : openingPayment.allocation().getPortfolio();
                if (spousalIra != null && rollovers.isEmpty()) {
                    rollovers = spousalIra.rolloversAtOpening(calendarYear, afterOpeningPayment, priorYearEndSnapshot, mandatory);
                }
                var membership = spousalIra == null ? null
                        : spousalIra.membership(calendarYear, afterOpeningPayment,
                                priorYearEndSnapshot == null && spousalIra.inSurvivorPeriod(calendarYear)
                                        ? RmdBalanceSnapshot.fromOpeningRmdData(plan.getAccountPortfolio(), calendarYear)
                                        : priorYearEndSnapshot, rollovers);
                if (annuityExecution) com.daviddunn.retirementplanner.domain.benefit.NonqualifiedAnnuityAdmission.requireGrowthSupported(
                        annuityOpeningCash, projectedPortfolio.getRetainedNonQualifiedAssets(), annualReturn);
                calculation = calculateProjectionYear(
                            plan,
                            yearOffset,
                            calendarYear,
                            projectedPortfolio,
                            priorYearEndSnapshot,
                            withdrawalStrategy,
                            socialSecurityByYear.getOrDefault(
                                    calendarYear,
                                    HouseholdSocialSecurityResult.zero()),
                            deathView,
                            lifetimeRun,
                            versionedRoth ? versionedRmdHasOccurred : householdRmdHasOccurred,
                            annualReturn,
                            evaluationContext.inflationPath().isPresent() ? generalExpenseMultiplier : null,
                            evaluationContext.expensePath().orElse(null), conditionalBenefits, financialMode,
                            spousalIra, membership, openingPayment, null, 0);
            } catch (RuntimeException exception) {
                if (arkansasValidation != null && exception instanceof com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException) {
                    throw arkansasNiitUnavailable(calendarYear, exception);
                }
                if (!structured || !(exception.getCause() instanceof FundingConstraint constraint)) {
                    throw exception;
                }
                var household = plan.getHousehold();
                var date = LocalDate.of(calendarYear, 12, 31);
                var failure = new FundingFailure(
                        calendarYear, yearOffset,
                        reportingAge(household.getPrimaryPerson(), date),
                        household.spouse().flatMap(person -> reportingAge(person, date)),
                        constraint.stage, constraint.owner, constraint.required, constraint.available,
                        constraint.required.subtract(constraint.available));
                return new ProjectionExecutionResult.InsufficientFunds(projection.getYears(), failure);
            }

            ProjectionYear projectionYear =
                    calculation.getProjectionYear();

            householdRmdHasOccurred |= projectionYear.getRequiredMinimumDistribution().signum() > 0;
            versionedRmdHasOccurred |= calculation.positiveMandatoryRmd();
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
            checkProtectedProjectionCancellation(spousalIra, versionedRoth);
            if (controlledRun != null) {
                controlledRun.cancellation().throwIfCancellationRequested();
                controlledRun.completedYears().accept(projection.getYearsProjected());
            }
        }

        return new ProjectionExecutionResult.Completed(projection.getYears());
    }

    private static void checkProtectedProjectionCancellation(SpousalIraRmdPolicy policy, boolean versionedRoth) {
        if ((policy != null || versionedRoth) && Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Projection canceled.");
        }
    }

    private static ArkansasProjectionUnavailableException arkansasNiitUnavailable(int year, RuntimeException failure) {
        return new ArkansasProjectionUnavailableException(year,
                ArkansasProjectionUnavailableException.Reason.SOURCE_OR_TAX, failure.getMessage(),
                com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.of(
                        com.daviddunn.retirementplanner.domain.tax.state.ArkansasTaxIssue.Kind.NIIT), java.util.List.of());
    }

    private static Optional<Integer> reportingAge(Person person, LocalDate date) {
        if (person == null || person.getBirthDate() == null) {
            return Optional.empty();
        }
        return Optional.of(person.getAge(date));
    }

    private static void reconcileTaxFundingDebits(int year, List<IncomeSourceAttribution> expected,
            List<IncomeSourceAttribution> executed) {
        if (expected.size() == executed.size()) {
            int index = 0;
            for (; index < expected.size(); index++) {
                var receipt = expected.get(index);
                var debit = executed.get(index);
                if (!receipt.account().equals(debit.account()) || receipt.grossAmount().compareTo(debit.grossAmount()) != 0) {
                    break;
                }
            }
            if (index == expected.size()) return;
        }
        throw new SpousalIraRmdUnavailable(year, SpousalIraRmdUnavailable.Reason.INCONSISTENT_WITHDRAWAL_DEBIT,
                "Tax funding receipts differ from executed source debits.");
    }

    private record OpeningEmployerPayment(ProjectedWithdrawalAllocation allocation,
            List<com.daviddunn.retirementplanner.domain.rmd.Beneficiary401kRmd> obligations) {
        BigDecimal amount() { return allocation.getWithdrawalBreakdown().getTotalWithdrawal(); }
    }

    private ProjectionYearCalculation calculateProjectionYear(
            RetirementPlan plan,
            int yearOffset,
            int calendarYear,
            ProjectedPortfolio projectedPortfolio,
            RmdBalanceSnapshot priorYearEndSnapshot,
            WithdrawalStrategy withdrawalStrategy,
            HouseholdSocialSecurityResult socialSecurityResult,
            EffectiveHouseholdDeathView deathView,
            boolean lifetimeRun,
            boolean householdRmdHasOccurred,
            BigDecimal annualInvestmentReturn,
            BigDecimal generalExpenseMultiplier,
            ProjectionExpensePath expensePath,
            com.daviddunn.retirementplanner.domain.benefit.AnnualBenefitEvaluation conditionalBenefits,
            com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode financialMode,
            SpousalIraRmdPolicy spousalIra,
            SpousalIraRmdMembership membership,
            OpeningEmployerPayment openingPayment,
            MedicarePremiumCalculation authoritativeMedicarePremium,
            int medicareRecalculations) {

        BigDecimal beginningAssets =
                projectedPortfolio.getTotalBalance();
        var investableAtOpening = openingPayment == null ? projectedPortfolio : openingPayment.allocation().getPortfolio();
        var openingMandatoryAmount = openingPayment == null ? BigDecimal.ZERO : openingPayment.amount();
        var growthBasis = investableAtOpening.getTotalBalance();

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
                        growthBasis,
                        annualInvestmentReturn,
                        yearOffset,
                        projectionStartDate);

        BigDecimal beginningRetainedNonQualifiedAssets =
                projectedPortfolio.getRetainedNonQualifiedAssets();

        BigDecimal retainedNonQualifiedAssetGrowth =
                calculateRetainedNonQualifiedAssetGrowth(
                        investmentGrowth,
                        beginningRetainedNonQualifiedAssets,
                        growthBasis);

        BigDecimal accountAssetGrowth =
                investmentGrowth.subtract(
                        retainedNonQualifiedAssetGrowth);

        LocalDate projectionDate =
                yearOffset == 0
                        ? projectionStartDate
                        : LocalDate.of(
                        calendarYear,
                        12,
                        31);

        Household household =
                plan.getHousehold();

        boolean survivorIraPeriod = spousalIra != null && spousalIra.inSurvivorPeriod(calendarYear);
        Set<AccountOwnership> eligibleOwners = household.peopleByOwner().keySet().stream()
                .filter(owner -> !lifetimeRun && arkansasValidation == null && !survivorIraPeriod
                        || deathView.isAlive(owner, calendarYear))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Optional<PensionIncomeResult> pensionResult = Optional.of(householdPensionIncomeCalculator
                .calculateWithSources(household, projectionDate, deathView));
        Optional<BigDecimal> authoritativePensionIncome = pensionResult.map(PensionIncomeResult::total);

        BigDecimal guaranteedIncome =
                calculateTotalIncome(
                        household,
                        projectionDate,
                        assumptions,
                        socialSecurityResult,
                        deathView, authoritativePensionIncome);

        var conditionalSources = new java.util.ArrayList<>(conditionalBenefits.receipts().stream().map(IncomeSourceAttribution::conditional).toList());
        if (financialMode != null) conditionalSources.add(IncomeSourceAttribution.calculationPolicy(financialMode, calendarYear));
        if (!conditionalBenefits.receipts().isEmpty()) guaranteedIncome = guaranteedIncome.add(conditionalBenefits.gross());

        BigDecimal annualExpenses =
                calculateProjectedExpenses(
                        household,
                        assumptions,
                        yearOffset,
                        projectionDate,
                        deathView,
                        generalExpenseMultiplier,
                        expensePath);

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
                        governmentRules, eligibleOwners)
                : null;

        HouseholdRmdResult annualHouseholdRmdResult =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getAnnualRequirement()
                        : calculateRequiredMinimumDistribution(
                                plan,
                                calendarYear,
                                priorYearEndSnapshot, eligibleOwners, membership);

        HouseholdRmdResult projectedPeriodRmdResult =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getRemainingRequirement()
                        : annualHouseholdRmdResult;

        if (membership != null) {
            var livingAnnual = annualHouseholdRmdResult;
            annualHouseholdRmdResult = membership.addDeathYearRequirement(livingAnnual, projectedGovernmentRules);
            projectedPeriodRmdResult = projectedPeriodRmdResult == livingAnnual ? annualHouseholdRmdResult
                    : membership.addDeathYearRequirement(projectedPeriodRmdResult, projectedGovernmentRules);
        }

        BigDecimal rmdDistributedBeforeProjection =
                openingRmdCalculation != null
                        ? openingRmdCalculation.getDistributedBeforeProjection()
                        : BigDecimal.ZERO;

        var projectedRmdCash = projectedPeriodRmdResult.getTotalRmd().add(openingMandatoryAmount);
        BigDecimal requiredMinimumDistribution =
                annualHouseholdRmdResult
                        .getTotalRmd().add(openingMandatoryAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        WithdrawalResult withdrawalResult =
                withdrawalCalculator
                        .calculateWithdrawal(
                                guaranteedIncome,
                                cashFlowExpenses,
                                projectedRmdCash);

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
                investableAtOpening.withGrowth(
                        accountAssetGrowth,
                        retainedNonQualifiedAssetGrowth);

        ProjectedWithdrawalAllocation rmdAllocation =
                withdrawalAllocator.allocateHouseholdRmds(
                        portfolioAfterGrowth,
                        projectedPeriodRmdResult, membership);

        ProjectedPortfolio portfolioAfterRmd =
                rmdAllocation.getPortfolio();

        WithdrawalBreakdown rmdWithdrawalBreakdown =
                openingPayment == null ? rmdAllocation.getWithdrawalBreakdown()
                        : openingPayment.allocation().getWithdrawalBreakdown().plus(rmdAllocation.getWithdrawalBreakdown());

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
        java.util.function.UnaryOperator<IncomeSourceAttribution> attributeWithdrawal = survivorIraPeriod
                ? source -> spousalIra.attributeDiscretionarySource(calendarYear, source, membership)
                : java.util.function.UnaryOperator.identity();
        if (survivorIraPeriod) {
            withdrawalBreakdown = withdrawalBreakdown.withIncomeSources(
                    withdrawalBreakdown.getIncomeSources().stream().map(attributeWithdrawal).toList());
        }

        WithdrawalBreakdown totalWithdrawalBreakdown =
                rmdWithdrawalBreakdown.plus(
                        withdrawalBreakdown);

        BigDecimal spendingWithdrawal =
                withdrawalResult.getAdditionalWithdrawalRequired();

        BigDecimal availableHouseholdCashForTaxes =
                guaranteedIncome
                        .add(projectedRmdCash)
                        .subtract(cashFlowExpenses)
                        .max(BigDecimal.ZERO);

        RothConversionRequest rothConversionRequest =
                plan.getRothConversionRequest();

        boolean householdSubjectToRmd =
                annualHouseholdRmdResult.getTotalRmd().add(openingMandatoryAmount).signum() > 0;

        TaxHouseholdContext taxContext = TaxHouseholdContext.capture(
                household, projectionDate, assumptions.getDeathScenarioAssumptions(), pensionResult);
        if (lifetimeRun) {
            taxContext = taxContext.withActiveOwners(eligibleOwners);
        }
        List<IncomeSourceAttribution> preConversionSources = new java.util.ArrayList<>();
        if (openingRmdCalculation != null) {
            preConversionSources.addAll(openingRmdCalculation.getIncomeSources());
        }
        pensionResult.ifPresent(result -> preConversionSources.addAll(result.sources()));
        preConversionSources.addAll(conditionalSources);

        BigDecimal rothConversion = BigDecimal.ZERO;
        BigDecimal arkansasTarget = null;

        boolean versionedRoth = RothConversionRequest.usesVersionedExecution(rothConversionRequest);
        var conversionOwners = versionedRoth ? eligibleOwners.stream()
                .filter(owner -> deathView.isAlive(owner, calendarYear)).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : eligibleOwners;
        if (rothConversionRequest != null && (versionedRoth
                ? scheduledRothConversionPolicy.shouldExecuteVersioned(rothConversionRequest, calendarYear,
                        householdSubjectToRmd, householdRmdHasOccurred)
                : scheduledRothConversionPolicy
                        .shouldExecuteConvert(
                                rothConversionRequest,
                                calendarYear,
                                householdSubjectToRmd || (lifetimeRun && householdRmdHasOccurred)))) {

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
                                projectionDate,
                                deathView);

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
                if (arkansasValidation != null) {
                    arkansasTarget = targetTaxableIncome;
                } else {
                    rothConversion = rothConversionBracketFillCalculator
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
                                        socialSecurityResult,
                                        availableHouseholdCashForTaxes,
                                        authoritativePensionIncome, preConversionSources, taxContext, conversionOwners,
                                        assumptions.getStateTaxAssumptions(), survivorIraPeriod);
                }
            }
        }

        /*
         * Strategies determine a household target. Limit it to the amount
         * that can actually move from an eligible source into a same-owner
         * Roth destination before it enters household tax calculations.
         */
        BigDecimal requestedRothConversion = rothConversion;
        ProjectedRothConversionResult rothConversionResult;
        TaxFundingResult taxFundingResult = null;
        ArkansasProjectionValidation.Execution arkansas = null;
        FilingStatus projectionFilingStatus = getProjectionFilingStatus(assumptions, projectionDate, deathView);
        if (arkansasValidation != null) {
            var opening = yearOffset == 0 ? projectionStartDate : LocalDate.of(calendarYear, 1, 1);
            var sources = new java.util.ArrayList<IncomeSourceAttribution>(conditionalSources);
            if (openingRmdCalculation != null) sources.addAll(openingRmdCalculation.getIncomeSources());
            var premium = authoritativeMedicarePremium == null ? BigDecimal.ZERO
                    : authoritativeMedicarePremium.totalAnnualMedicarePremium();
            var control = arkansasRunControl.get();
            boolean captureLowIncome = control != null && control.lowIncomeDiagnostic().isPresent();
            try (var recipientTrace = ArkansasRecipientDiagnostic.isEnabled()
                    ? ArkansasRecipientDiagnostic.annual(
                            deathView.firstDeathYear(), calendarYear, financialMode,
                            assumptions.getStateTaxAssumptions().arkansasLowIncomePolicy(),
                            authoritativeMedicarePremium == null
                                    ? ArkansasRecipientDiagnostic.Pass.INITIAL : ArkansasRecipientDiagnostic.Pass.MEDICARE_AWARE,
                            arkansasTarget == null
                                    ? ArkansasRecipientDiagnostic.RothPath.FIXED_OR_NONE : ArkansasRecipientDiagnostic.RothPath.TARGETED)
                    : null) {
                arkansas = arkansasValidation.execute(plan, taxContext, opening, deathView, projectionFilingStatus,
                        projectedGovernmentRules, socialSecurityResult, totalWithdrawalBreakdown,
                        rmdDistributedBeforeProjection, sources, new com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.Cash(
                                guaranteedIncome, projectedPeriodRmdResult.getTotalRmd(), spendingWithdrawal, annualExpenses, premium),
                        portfolioAfterAdditionalWithdrawal, withdrawalStrategy, rothConversion, arkansasTarget,
                        control == null ? com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none()
                                : control.cancellation(), captureLowIncome);
            } catch (ArkansasProjectionUnavailableException failure) {
                if (captureLowIncome) {
                    reportArkansasLowIncome(control, failure, projectionFilingStatus, deathView, financialMode,
                            arkansasTarget != null, authoritativeMedicarePremium != null, conditionalSources);
                }
                throw failure;
            }
            requestedRothConversion = arkansas.conversion().requested();
            rothConversionResult = arkansas.conversion().conversion();
            rothConversion = rothConversionResult.getTotalConversion();
        } else {
            rothConversion = rothConversion.min(
                    projectedPortfolioRothConverter
                            .getMaximumConvertibleAmount(
                                    portfolioAfterAdditionalWithdrawal, conversionOwners, calendarYear));

            rothConversionResult =
                    projectedPortfolioRothConverter.convertHousehold(
                            portfolioAfterAdditionalWithdrawal,
                            rothConversion, conversionOwners, calendarYear);

            rothConversion = rothConversionResult.getTotalConversion();

            var portfolioAfterConversion = rothConversionResult.getPortfolio();

            List<IncomeSourceAttribution> supplementalIncomeSources = new java.util.ArrayList<>(
                    rothConversionResult.getIncomeSources(calendarYear));
            if (openingRmdCalculation != null) {
                supplementalIncomeSources.addAll(openingRmdCalculation.getIncomeSources());
            }
            pensionResult.ifPresent(result -> supplementalIncomeSources.addAll(result.sources()));
            supplementalIncomeSources.addAll(conditionalSources);

            taxFundingResult =
                    taxFundingCalculator.calculate(
                            household,
                            projectionDate,
                            portfolioAfterConversion,
                            totalWithdrawalBreakdown,
                            withdrawalStrategy,
                            getProjectionFilingStatus(
                                    assumptions,
                                    projectionDate,
                                    deathView),
                            projectedGovernmentRules,
                            rothConversion,
                            BigDecimal.ZERO,
                            assumptions.getSocialSecurityColaRate(),
                            assumptions.getDeathScenarioAssumptions(),
                            rmdDistributedBeforeProjection,
                            socialSecurityResult,
                            availableHouseholdCashForTaxes,
                            authoritativePensionIncome, supplementalIncomeSources, taxContext, assumptions.getStateTaxAssumptions(),
                            survivorIraPeriod ? attributeWithdrawal : null);
        }

        BigDecimal taxFundingWithdrawal = arkansas == null ? taxFundingResult.getAdditionalWithdrawal()
                : arkansas.funding().spendable();
        BigDecimal totalPortfolioWithdrawal = (arkansas == null ? portfolioWithdrawal
                : totalWithdrawalBreakdown.getTotalWithdrawal()).add(taxFundingWithdrawal);
        FederalTaxCalculation federalTaxCalculation = arkansas == null ? taxFundingResult.getFederalTaxCalculation()
                : arkansas.funding().annualTaxFacts().federal();
        ProductionStateTaxResult stateTaxCalculation = arkansas == null ? taxFundingResult.getStateTaxCalculation() : null;

        int coveredIndividuals =
                calculateCoveredMedicareParticipants(
                        household,
                        projectionDate,
                        deathView);


        boolean successorOrdinaryPaid = survivorIraPeriod && spousalIra.ordinaryEnabled
                && (openingPayment != null || taxFundingResult.getIncomeSources().stream().anyMatch(source -> source.successorOrdinary().isPresent()
                        || source.successorRmd().map(row -> switch (row.treatment()) {
                            case MODELED_SPOUSAL_DEATH_YEAR_IRA_V1, MODELED_SPOUSAL_DEATH_YEAR_401K_V1 -> true;
                            default -> false;
                        }).orElse(false)));
        MedicarePremiumCalculation medicarePremiumCalculation =
                authoritativeMedicarePremium == null || successorOrdinaryPaid
                        ? medicarePremiumCalculator.calculate(
                                federalTaxCalculation,
                                projectionFilingStatus,
                                projectedGovernmentRules,
                                coveredIndividuals)
                        : authoritativeMedicarePremium;

        BigDecimal fundedPremium = authoritativeMedicarePremium == null ? BigDecimal.ZERO
                : authoritativeMedicarePremium.totalAnnualMedicarePremium();
        if (medicarePremiumCalculation.totalAnnualMedicarePremium().compareTo(fundedPremium) != 0) {
            if (medicareRecalculations >= projectedGovernmentRules.getIrmaaRules().getBrackets().size()) {
                throw new SpousalIraRmdUnavailable(calendarYear, SpousalIraRmdUnavailable.Reason.MEDICARE_FUNDING_NONCONVERGENCE,
                        "The successor withdrawal and Medicare premium did not settle in the same IRMAA tier.");
            }
            checkProtectedProjectionCancellation(spousalIra, versionedRoth);
            return calculateProjectionYear(
                    plan,
                    yearOffset,
                    calendarYear,
                    projectedPortfolio,
                    priorYearEndSnapshot,
                    withdrawalStrategy,
                    socialSecurityResult,
                    deathView,
                    lifetimeRun,
                    householdRmdHasOccurred,
                    annualInvestmentReturn,
                    generalExpenseMultiplier,
                    expensePath,
                    conditionalBenefits,
                    financialMode,
                    spousalIra,
                    membership,
                    openingPayment,
                    medicarePremiumCalculation,
                    medicareRecalculations + 1);
        }

        HouseholdCashSettlement cashSettlement;
        ProjectedPortfolio endingPortfolio;
        if (arkansas != null) {
            var settlement = com.daviddunn.retirementplanner.domain.tax.ArkansasAnnualFunding.settle(arkansas.funding());
            cashSettlement = settlement.cash();
            endingPortfolio = settlement.portfolio();
        } else {
            cashSettlement =
                    HouseholdCashSettlement.calculate(
                            guaranteedIncome,
                            projectedRmdCash,
                            spendingWithdrawal,
                            taxFundingWithdrawal,
                            annualExpenses,
                            medicarePremiumCalculation
                                    .totalAnnualMedicarePremium(),
                            taxFundingResult.getTotalIncomeTax());

            ProjectedPortfolio portfolioAfterTaxWithdrawal;
            if (survivorIraPeriod && spousalIra.ordinaryEnabled) {
                var actual = withdrawalAllocator.allocateTaxFundingWithdrawal(
                        rothConversionResult.getPortfolio(), taxFundingWithdrawal, withdrawalStrategy);
                var expected = taxFundingResult.getIncomeSources().stream()
                        .filter(source -> source.purpose() == TAX_FUNDING).toList();
                reconcileTaxFundingDebits(calendarYear, expected, actual.getWithdrawalBreakdown().getIncomeSources());
                portfolioAfterTaxWithdrawal = actual.getPortfolio();
            } else {
                portfolioAfterTaxWithdrawal = withdrawalAllocator.applyAdditionalWithdrawal(
                        rothConversionResult.getPortfolio(), taxFundingWithdrawal, withdrawalStrategy);
            }

            endingPortfolio =
                    portfolioAfterTaxWithdrawal
                            .withAdditionalRetainedNonQualifiedAssets(
                                    cashSettlement.retainedHouseholdSurplus());
        }

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
                                cashSettlement.retainedHouseholdSurplus())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);
        if (arkansas != null) endingAssets = endingPortfolio.getTotalBalance();
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
                        projectedRmdCash,
                        withdrawalResult.getExcessRmd(),
                        beginningRetainedNonQualifiedAssets,
                        retainedNonQualifiedAssetGrowth,
                        endingPortfolio.getRetainedNonQualifiedAssets(),
                        cashSettlement,
                        endingAssets,
                        endingAccountSnapshots,
                        federalTaxCalculation,
                        stateTaxCalculation,
                        medicarePremiumCalculation,
                        taxFundingWithdrawal,
                        requestedRothConversion,
                        rothConversion,
                        rothConversionResult.getConversion(
                                AccountOwnership.PRIMARY),
                        household.hasSpouse() ? rothConversionResult.getConversion(AccountOwnership.SPOUSE) : null,
                        estimatedHeirTax,
                        afterTaxEstateValue,
                        primaryPersonAge, arkansas == null ? taxFundingResult.getIncomeSources() : arkansas.funding().incomeSources(),
                        arkansas == null ? null : arkansas.disclosure(),
                        membership == null ? List.of() : membership.openingRollovers(),
                        projectedPeriodRmdResult.beneficiaryIraRmd(), openingPayment == null
                                ? projectedPeriodRmdResult.employerBeneficiaryRmds() : openingPayment.obligations());

        return new ProjectionYearCalculation(
                projectionYear,
                endingPortfolio, householdSubjectToRmd);
    }

    private static com.daviddunn.retirementplanner.domain.rules.FilingStatus getFilingStatus(PlanningAssumptions assumptions) {
        return assumptions
                .getTaxAssumptions()
                .getFilingStatus();
    }

    private HouseholdRmdResult calculateRequiredMinimumDistribution(
            RetirementPlan plan,
            int calendarYear,
            RmdBalanceSnapshot priorYearEndSnapshot, Set<AccountOwnership> eligibleOwners,
            SpousalIraRmdMembership membership) {

        /*
         * The first projection year does not have
         * a modeled prior December 31 balance.
         */
        if (priorYearEndSnapshot == null) {
            return new HouseholdRmdResult(plan.getHousehold().peopleByOwner().keySet().stream()
                    .collect(java.util.stream.Collectors.toMap(owner -> owner, owner -> com.daviddunn.retirementplanner.domain.rmd.OwnerRmdResult.zero())));
        }

        return householdRmdCalculator.calculate(
                plan,
                priorYearEndSnapshot,
                calendarYear,
                governmentRules, eligibleOwners, membership);
    }

    private BigDecimal calculateInvestmentGrowth(
            BigDecimal beginningAssets,
            BigDecimal annualInvestmentReturn,
            int yearOffset,
            LocalDate projectionStartDate) {

        BigDecimal investmentGrowth =
                beginningAssets.multiply(
                        annualInvestmentReturn);

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

    private BigDecimal calculateRetainedNonQualifiedAssetGrowth(
            BigDecimal totalInvestmentGrowth,
            BigDecimal beginningRetainedNonQualifiedAssets,
            BigDecimal beginningInvestableAssets) {

        if (beginningRetainedNonQualifiedAssets.signum() == 0
                || beginningInvestableAssets.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return totalInvestmentGrowth
                .multiply(beginningRetainedNonQualifiedAssets)
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
            HouseholdSocialSecurityResult socialSecurityResult,
            EffectiveHouseholdDeathView deathView, Optional<BigDecimal> authoritativePensionIncome) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (Person person : household.members()) {
            total = total.add(calculateIncome(person, projectionDate, deathView, authoritativePensionIncome.isPresent()));
        }
        total = total.add(
                socialSecurityResult.householdBenefit());

        total = total.add(authoritativePensionIncome.orElseGet(() ->
                calculateSurvivorPensionIncome(
                        household,
                        projectionDate,
                        deathView)));

        return total;
    }

    private BigDecimal calculateSurvivorPensionIncome(
            Household household,
            LocalDate projectionDate,
            EffectiveHouseholdDeathView deathView) {

        if (!household.hasSpouse()) return BigDecimal.ZERO;
        int year = projectionDate.getYear();
        boolean primaryAlive = deathView.isAlive(AccountOwnership.PRIMARY, year);
        boolean spouseAlive = deathView.isAlive(AccountOwnership.SPOUSE, year);
        if (primaryAlive == spouseAlive) {
            return BigDecimal.ZERO;
        }
        Person deceasedPerson = primaryAlive
                ? household.getSpouse() : household.getPrimaryPerson();
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
            EffectiveHouseholdDeathView deathView, boolean pensionAlreadyCalculated) {

        BigDecimal total =
                BigDecimal.ZERO;

        for (IncomeSource income :
                person.getIncomeSources()) {

            if (income instanceof SocialSecurityIncome || (pensionAlreadyCalculated && income instanceof Pension)) {
                continue;
            }

            if (!deathView.isAlive(
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
            LocalDate projectionDate,
            EffectiveHouseholdDeathView deathView,
            BigDecimal generalExpenseMultiplier,
            ProjectionExpensePath expensePath) {

        BigDecimal totalExpenses =
                BigDecimal.ZERO;

        var expenses = household.getExpenses();
        for (int expenseIndex = 0; expenseIndex < expenses.size(); expenseIndex++) {
            Expense expense = expenses.get(expenseIndex);

            if (expense.isContractualInsurancePremium()) {
                totalExpenses = totalExpenses.add(com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.annual(
                        expense, assumptions.getProjectionStartDate(), projectionDate.getYear(), deathView));
                continue;
            }

            if (!expense.isActiveDuringYear(
                    projectionDate.getYear(),
                    assumptions.getProjectionStartDate())) {

                continue;
            }

            if (expense.getExpenseType() == ExpenseType.RECURRING
                    && deathView.isHouseholdDeceased(projectionDate.getYear())) {
                continue;
            }

            BigDecimal projectedExpense =
                    calculateProjectedExpense(
                            expense,
                            assumptions,
                            yearOffset,
                            generalExpenseMultiplier);

            if (expensePath != null) {
                var occurrence = expensePath.bindingForExpense(expenseIndex);
                if (occurrence.isPresent()) {
                    BigDecimal multiplier = expensePath.multiplier(occurrence.orElseThrow(), projectionDate.getYear());
                    if (projectedExpense.signum() != 0) {
                        projectedExpense = projectedExpense.multiply(multiplier);
                    }
                }
            }

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
                    && deathView.hasAnyDeathOccurred(
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
            int yearOffset,
            BigDecimal generalExpenseMultiplier) {

        if (expense.getGrowthCategory() == com.daviddunn.retirementplanner.domain.financial.GrowthCategory.NONE) {
            return expense.getAnnualAmount();
        }
        if (generalExpenseMultiplier != null && !expense.isHealthcareExpense()) {
            return expense.getAnnualAmount().multiply(generalExpenseMultiplier);
        }

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
    private int calculateCoveredMedicareParticipants(
            Household household,
            LocalDate projectionDate,
            EffectiveHouseholdDeathView deathView) {

        int participants = 0;
        for (var entry : household.peopleByOwner().entrySet()) {
            Person person = entry.getValue();
            if (person.getBirthDate() != null && deathView.isAlive(entry.getKey(), projectionDate.getYear())
                    && person.getAge(projectionDate) >= 65) {
                participants++;
            }
        }
        return participants;
    }


    private static FilingStatus getProjectionFilingStatus(
            PlanningAssumptions assumptions,
            LocalDate projectionDate,
            EffectiveHouseholdDeathView deathView) {

        int year = projectionDate.getYear();
        // The surviving-filer transition is a couple rule, not a change to a single person's configured status.
        if (deathView.state(AccountOwnership.SPOUSE, year) == EffectiveHouseholdDeathView.PersonState.ABSENT) {
            return getFilingStatus(assumptions);
        }
        if (!deathView.hasAnyDeathOccurred(year)
                || deathView.firstDeathYear().orElseThrow().getValue() == year) {
            return getFilingStatus(assumptions);
        }
        return FilingStatus.SINGLE;
    }

    private static void reportArkansasLowIncome(ArkansasRunControl control, ArkansasProjectionUnavailableException failure,
            FilingStatus filing, EffectiveHouseholdDeathView deaths,
            FinancialCalculationMode mode, boolean targetRequested,
            boolean medicareAware, List<IncomeSourceAttribution> sources) {
        var evidence = failure.issue().lowIncomeEvidence().orElse(null);
        if (evidence == null || evidence.exactFundingProbe().isEmpty() || control.cancellation().isCancellationRequested()) {
            return;
        }
        int year = failure.year();
        var firstDeath = deaths.firstDeathYear();
        var deathYear = ArkansasLowIncomeDiagnostic.DeathYear.NO_MODELED_DEATH;
        if (firstDeath.isPresent()) {
            int first = firstDeath.orElseThrow().getValue();
            deathYear = year < first ? ArkansasLowIncomeDiagnostic.DeathYear.BEFORE_FIRST_DEATH
                    : year == first ? ArkansasLowIncomeDiagnostic.DeathYear.FIRST_DEATH_YEAR
                    : ArkansasLowIncomeDiagnostic.DeathYear.LATER_SURVIVOR_YEAR;
        }
        var path = failure.reason() == ArkansasProjectionUnavailableException.Reason.TARGET_PROOF
                ? ArkansasLowIncomeDiagnostic.RothPath.TARGET
                : targetRequested ? ArkansasLowIncomeDiagnostic.RothPath.TARGET_FALLBACK_FIXED
                : ArkansasLowIncomeDiagnostic.RothPath.FIXED;
        boolean lifePaid = sources.stream().flatMap(source -> source.conditionalBenefit().stream())
                .anyMatch(receipt -> receipt.taxCharacter() == TaxCharacter.QUALIFIED_LIFE_INSURANCE_DEATH_PRINCIPAL_EXCLUDED);
        var summary = new ArkansasLowIncomeDiagnostic(year, filing, deathYear,
                mode == null ? FinancialCalculationMode.BASIC : mode, path,
                medicareAware ? ArkansasLowIncomeDiagnostic.Pass.MEDICARE_AWARE : ArkansasLowIncomeDiagnostic.Pass.INITIAL,
                lifePaid, evidence.section301Gross(), evidence.section501Net(), evidence.exactFundingProbe().orElseThrow(),
                failure.reason());
        try {
            control.lowIncomeDiagnostic().orElseThrow().accept(summary);
        } catch (RuntimeException unavailable) {
            System.getLogger(ProjectionEngine.class.getName()).log(System.Logger.Level.WARNING,
                    "Unable to record the optional Arkansas low-income diagnostic.");
        }
    }

}
