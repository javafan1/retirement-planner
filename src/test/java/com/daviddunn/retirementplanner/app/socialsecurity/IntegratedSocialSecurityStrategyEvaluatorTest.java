package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.RothIRA;
import com.daviddunn.retirementplanner.domain.financial.TraditionalIRA;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.domain.tax.TaxIncome;
import com.daviddunn.retirementplanner.domain.tax.TaxIncomeCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class IntegratedSocialSecurityStrategyEvaluatorTest {

    private final IntegratedSocialSecurityStrategyEvaluator evaluator =
            new IntegratedSocialSecurityStrategyEvaluator();

    @Test
    void untouchedDeepCopyProjectsEquivalentlyAndDoesNotShareNestedState() {
        RetirementPlan original = plan();
        RetirementPlan copy = new RetirementPlanScenarioCopyService().copy(original);

        assertNotSame(original, copy);
        assertNotSame(original.getHousehold(), copy.getHousehold());
        assertNotSame(original.getHousehold().getPrimaryPerson(),
                copy.getHousehold().getPrimaryPerson());
        assertNotSame(original.getAccountPortfolio(), copy.getAccountPortfolio());
        assertNotSame(original.getAccountPortfolio().getAccounts().getFirst(),
                copy.getAccountPortfolio().getAccounts().getFirst());

        ProjectionMetricsCalculator calculator = new ProjectionMetricsCalculator();
        assertEquals(calculator.calculate(original, new ProjectionEngine().project(original)),
                calculator.calculate(copy, new ProjectionEngine().project(copy)));

        SocialSecurityIncome copiedSource = primarySource(copy);
        copy.getHousehold().getPrimaryPerson().replaceIncomeSource(copiedSource,
                source(AccountOwnership.PRIMARY, LocalDate.of(2025, 6, 4), "3000", 62));
        assertEquals(LocalDate.of(2030, 6, 4), primarySource(original).getStartDate());
    }

    @Test
    void candidateEvaluationIsReadOnlyRepeatableAndCannotContaminateOtherRuns()
            throws Exception {
        RetirementPlan plan = plan();
        String before = json(plan);
        SocialSecurityHouseholdClaimingStrategy early = strategy(plan, 62, 62, 60, 60);
        SocialSecurityHouseholdClaimingStrategy late = strategy(plan, 70, 70, 67, 67);

        IntegratedSocialSecurityStrategyResult baselineBefore =
                evaluator.evaluateCurrentStrategy(plan);
        IntegratedSocialSecurityStrategyResult earlyFirst = evaluator.evaluate(plan, early);
        IntegratedSocialSecurityStrategyResult lateResult = evaluator.evaluate(plan, late);
        IntegratedSocialSecurityStrategyResult earlyAgain = evaluator.evaluate(plan, early);
        IntegratedSocialSecurityStrategyResult baselineAfter =
                evaluator.evaluateCurrentStrategy(plan);

        assertEquals(before, json(plan));
        assertEquals(earlyFirst.metrics(), earlyAgain.metrics());
        assertEquals(baselineBefore.metrics(), baselineAfter.metrics());
        assertNotEquals(earlyFirst.metrics().lifetimeHouseholdSocialSecurity(),
                lateResult.metrics().lifetimeHouseholdSocialSecurity());
        assertNotEquals(earlyFirst.metrics().lifetimePortfolioWithdrawals(),
                lateResult.metrics().lifetimePortfolioWithdrawals());
        assertTrue(earlyFirst.advancedSocialSecurityPathUsed());
        assertTrue(earlyFirst.warnings().isEmpty());
    }

    @Test
    void candidateFlowsThroughTaxesPortfolioAndAuthoritativeResultMetrics() {
        RetirementPlan plan = plan();
        IntegratedSocialSecurityStrategyResult early = evaluator.evaluate(
                plan, strategy(plan, 62, 62, 60, 60));
        IntegratedSocialSecurityStrategyResult late = evaluator.evaluate(
                plan, strategy(plan, 70, 70, 67, 67));

        assertNotEquals(early.metrics().totalTaxes(), late.metrics().totalTaxes());
        assertNotEquals(early.metrics().endingInvestableAssets(),
                late.metrics().endingInvestableAssets());
        assertEquals(early.projection().getLastYear().getAfterTaxEstateValue(),
                early.metrics().afterTaxEstate());
        assertEquals(new ProjectionMetricsCalculator().calculate(planForMetrics(early),
                early.projection()), early.metrics());
    }

    @Test
    void rejectsAClaimDateThatDoesNotMatchTheCandidateAge() {
        RetirementPlan plan = plan();
        SocialSecurityHouseholdClaimingStrategy valid = strategy(plan, 62, 62, 60, 60);
        SocialSecurityHouseholdClaimingStrategy invalid =
                new SocialSecurityHouseholdClaimingStrategy(
                        valid.primaryRetirementAge(), valid.spouseRetirementAge(),
                        valid.primaryRetirementClaimDate().plusDays(1),
                        valid.spouseRetirementClaimDate(),
                        valid.primarySurvivorElection(), valid.spouseSurvivorElection());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(plan, invalid));
        assertTrue(error.getMessage().contains("does not match age"));
    }

    @Test
    void rejectsCompatibilityFallbackHouseholdInsteadOfClaimingFullEvaluation() {
        RetirementPlan plan = plan();
        plan.getHousehold().getSpouse().removeIncomeSource(spouseSource(plan));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> evaluator.evaluate(plan, strategy(plan, 62, 62, 60, 60)));
        assertTrue(error.getMessage().contains("advanced path is unavailable"));
    }

    @Test
    void emptyProjectionMetricsUseExistingZeroSemantics() {
        ProjectionMetrics metrics = new ProjectionMetricsCalculator()
                .calculate(plan(), new Projection());
        assertEquals(BigDecimal.ZERO, metrics.totalIncome());
        assertEquals(BigDecimal.ZERO, metrics.endingNetWorth());
        assertEquals(BigDecimal.ZERO, metrics.afterTaxEstate());
    }

    @Test
    void exactSpouseSurvivorOverrideChangesIntegratedResultsAndIsIsolated()
            throws Exception {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2028, 62));
        String before = json(plan);
        SocialSecurityHouseholdClaimingStrategy early = strategy(plan, 67, 62, 60, 60);
        SocialSecurityHouseholdClaimingStrategy later = strategy(plan, 67, 62, 60, 67);

        IntegratedSocialSecurityStrategyResult baselineBefore =
                evaluator.evaluateCurrentStrategy(plan);
        IntegratedSocialSecurityStrategyResult firstEarly = evaluator.evaluate(plan, early);
        IntegratedSocialSecurityStrategyResult late = evaluator.evaluate(plan, later);
        IntegratedSocialSecurityStrategyResult secondEarly = evaluator.evaluate(plan, early);
        IntegratedSocialSecurityStrategyResult baselineAfter =
                evaluator.evaluateCurrentStrategy(plan);

        assertTrue(firstEarly.projection().getYears().stream()
                .filter(year -> year.getCalendarYear() >= 2028)
                .anyMatch(year -> year.getSocialSecurityResult()
                        .spouseSurvivorCandidate().signum() > 0));
        assertTrue(firstEarly.projection().getYears().stream()
                .filter(year -> year.getCalendarYear() < 2028)
                .allMatch(year -> year.getSocialSecurityResult()
                        .spouseSurvivorCandidate().signum() == 0));
        assertTrue(late.projection().getYears().stream()
                .filter(year -> year.getCalendarYear() < 2032)
                .allMatch(year -> year.getSocialSecurityResult()
                        .spouseSurvivorCandidate().signum() == 0));
        assertNotEquals(firstEarly.metrics().lifetimeHouseholdSocialSecurity(),
                late.metrics().lifetimeHouseholdSocialSecurity());
        assertNotEquals(firstEarly.metrics().totalTaxes(), late.metrics().totalTaxes());
        assertNotEquals(firstEarly.metrics().lifetimePortfolioWithdrawals(),
                late.metrics().lifetimePortfolioWithdrawals());
        assertEquals(firstEarly.metrics(), secondEarly.metrics());
        assertEquals(baselineBefore.metrics(), baselineAfter.metrics());
        assertEquals(before, json(plan));
        assertEquals(62, plan.getPlanningAssumptions().getDeathScenarioAssumptions()
                .getSurvivorClaimingAge());
    }

    @Test
    void exactPrimarySurvivorOverrideChangesOnlyRelevantDeathDirection() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.SPOUSE_DIES, 2028, 62));
        IntegratedSocialSecurityStrategyResult early = evaluator.evaluate(
                plan, strategy(plan, 67, 62, 60, 60));
        IntegratedSocialSecurityStrategyResult later = evaluator.evaluate(
                plan, strategy(plan, 67, 62, 67, 60));

        assertTrue(early.projection().getYears().stream()
                .filter(year -> year.getCalendarYear() >= 2028)
                .anyMatch(year -> year.getSocialSecurityResult()
                        .primarySurvivorCandidate().signum() > 0));
        assertNotEquals(early.metrics().lifetimeHouseholdSocialSecurity(),
                later.metrics().lifetimeHouseholdSocialSecurity());
    }

    @Test
    void integratedAnnualSocialSecurityReconcilesExactlyToDirectAdvancedResult() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2028, 62));
        SocialSecurityHouseholdClaimingStrategy strategy =
                strategy(plan, 70, 62, 60, 67);
        IntegratedSocialSecurityStrategyResult integrated = evaluator.evaluate(plan, strategy);
        SocialSecurityLifetimeResult direct = new SocialSecurityStrategyCalculator()
                .calculate(directRequest(plan, strategy));

        for (SocialSecurityAnnualResult expected : direct.annualResults()) {
            ProjectionYear actual = integrated.projection().getYears().stream()
                    .filter(year -> year.getCalendarYear() == expected.calendarYear())
                    .findFirst().orElseThrow();
            assertMoney(expected.primaryOwnBenefits(),
                    actual.getSocialSecurityResult().primaryOwnBenefit());
            assertMoney(expected.spouseOwnBenefits(),
                    actual.getSocialSecurityResult().spouseOwnBenefit());
            assertMoney(expected.primarySpousalExcessBenefits(),
                    actual.getSocialSecurityResult().primarySpousalExcessBenefit());
            assertMoney(expected.spouseSpousalExcessBenefits(),
                    actual.getSocialSecurityResult().spouseSpousalExcessBenefit());
            assertMoney(expected.primarySelectedBenefits(),
                    actual.getSocialSecurityResult().primarySelectedBenefit());
            assertMoney(expected.spouseSelectedBenefits(),
                    actual.getSocialSecurityResult().spouseSelectedBenefit());
            assertMoney(expected.householdBenefits(),
                    actual.getSocialSecurityResult().householdBenefit());
        }
    }

    @Test
    void emptyContextPreservesOrdinaryProjectionBehavior() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2028, 62));
        Projection ordinary = new ProjectionEngine().project(plan);
        Projection explicitEmpty = new ProjectionEngine().project(
                plan, ProjectionEvaluationContext.empty());

        assertEquals(new ProjectionMetricsCalculator().calculate(plan, ordinary),
                new ProjectionMetricsCalculator().calculate(plan, explicitEmpty));
        for (int index = 0; index < ordinary.size(); index++) {
            assertEquals(ordinary.getYearAt(index).getSocialSecurityResult(),
                    explicitEmpty.getYearAt(index).getSocialSecurityResult());
        }
    }

    @Test
    void earlyWorkerRibLimAndDelayedWorkerDrcCasesReconcileToAdvancedEngine() {
        RetirementPlan earlyDeathPlan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2032, 62));
        assertDirectReconciliation(earlyDeathPlan,
                strategy(earlyDeathPlan, 62, 62, 60, 67));

        RetirementPlan delayedDeathPlan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2035, 62));
        assertDirectReconciliation(delayedDeathPlan,
                strategy(delayedDeathPlan, 70, 62, 60, 67));
    }

    @Test
    void overriddenSocialSecurityFeedsGuaranteedIncomeExactlyOnce() {
        RetirementPlan plan = plan(new DeathScenarioAssumptions(
                DeathScenario.PRIMARY_DIES, 2028, 62));
        IntegratedSocialSecurityStrategyResult result = evaluator.evaluate(
                plan, strategy(plan, 67, 62, 60, 67));

        for (ProjectionYear year : result.projection().getYears()) {
            if (year.getCalendarYear() >= 2028) {
                continue;
            }
            BigDecimal pension = new BigDecimal("60000");
            assertMoney(pension.add(year.getSocialSecurityResult().householdBenefit()),
                    year.getGuaranteedIncome());
        }

        ProjectionYear representative = result.projection().getYears().stream()
                .filter(year -> year.getCalendarYear() == 2029)
                .findFirst().orElseThrow();
        TaxIncome taxIncome = new TaxIncomeCalculator().calculate(
                plan.getHousehold(), LocalDate.of(2029, 12, 31),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                representative.getSocialSecurityResult());
        assertMoney(representative.getSocialSecurityResult().householdBenefit(),
                taxIncome.getSocialSecurityIncome());
    }

    @Test
    void overriddenAuthoritativeSocialSecurityControlsRothBracketFill() {
        RetirementPlan plan = plan();
        plan.getAccountPortfolio().addAccount(new TraditionalIRA(
                "Traditional IRA", AccountOwnership.PRIMARY,
                new BigDecimal("1000000")));
        plan.getAccountPortfolio().addAccount(new RothIRA(
                "Roth IRA", AccountOwnership.PRIMARY, BigDecimal.ZERO));
        plan.setRothConversionRequest(new RothConversionRequest(
                true, 2026, BigDecimal.ZERO, RothConversionStopRule.NEVER,
                RothConversionStrategy.FILL_12_PERCENT_BRACKET,
                RothConversionFrequency.ANNUAL, null));

        ProjectionYear early = evaluator.evaluate(
                        plan, strategy(plan, 62, 62, 60, 60))
                .projection().getYears().stream()
                .filter(year -> year.getCalendarYear() == 2027)
                .findFirst().orElseThrow();
        ProjectionYear late = evaluator.evaluate(
                        plan, strategy(plan, 70, 62, 60, 60))
                .projection().getYears().stream()
                .filter(year -> year.getCalendarYear() == 2027)
                .findFirst().orElseThrow();

        assertTrue(early.getSocialSecurityResult().householdBenefit().compareTo(
                late.getSocialSecurityResult().householdBenefit()) > 0);
        assertTrue(early.getRothConversion().compareTo(late.getRothConversion()) < 0);
        assertMoney(early.getFederalTaxableIncome(), late.getFederalTaxableIncome());
        assertMoney(early.getSocialSecurityResult().householdBenefit(),
                new TaxIncomeCalculator().calculate(
                        plan.getHousehold(), LocalDate.of(2027, 12, 31),
                        BigDecimal.ZERO, BigDecimal.ZERO, early.getRothConversion(),
                        BigDecimal.ZERO,
                        plan.getPlanningAssumptions().getSocialSecurityColaRate(),
                        plan.getPlanningAssumptions().getDeathScenarioAssumptions(),
                        early.getSocialSecurityResult()).getSocialSecurityIncome());
    }

    private RetirementPlan planForMetrics(IntegratedSocialSecurityStrategyResult result) {
        RetirementPlan scenario = new RetirementPlanScenarioCopyService().copy(plan());
        apply(scenario.getHousehold().getPrimaryPerson(), AccountOwnership.PRIMARY,
                result.evaluatedStrategy().primaryRetirementClaimDate(),
                result.evaluatedStrategy().primaryRetirementAge());
        apply(scenario.getHousehold().getSpouse(), AccountOwnership.SPOUSE,
                result.evaluatedStrategy().spouseRetirementClaimDate(),
                result.evaluatedStrategy().spouseRetirementAge());
        return scenario;
    }

    private void apply(Person person, AccountOwnership owner, LocalDate date, int age) {
        SocialSecurityIncome old = (SocialSecurityIncome) person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
        person.replaceIncomeSource(old, source(owner, date,
                old.getFullRetirementMonthlyBenefit().toPlainString(), age));
    }

    private RetirementPlan plan() {
        return plan(new DeathScenarioAssumptions(
                DeathScenario.BOTH_SURVIVE, null, 62));
    }

    private RetirementPlan plan(DeathScenarioAssumptions death) {
        Person primary = new Person("Primary", "Planner", LocalDate.of(1963, 6, 4));
        Person spouse = new Person("Spouse", "Planner", LocalDate.of(1965, 2, 28));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY,
                LocalDate.of(2026, 1, 1), null, new BigDecimal("5000"),
                BigDecimal.ZERO));
        primary.addIncomeSource(source(AccountOwnership.PRIMARY,
                LocalDate.of(2030, 6, 4), "3000", 67));
        spouse.addIncomeSource(source(AccountOwnership.SPOUSE,
                LocalDate.of(2027, 2, 28), "900", 62));
        Household household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("80000")));
        AccountPortfolio portfolio = new AccountPortfolio();
        portfolio.addAccount(new BrokerageAccount("Brokerage", AccountOwnership.PRIMARY,
                new BigDecimal("750000")));
        PlanningAssumptions assumptions = new PlanningAssumptions(
                new EconomicAssumptions(new BigDecimal("0.03"), new BigDecimal("0.02"),
                        BigDecimal.ZERO, new BigDecimal("0.02")),
                new TaxAssumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new WithdrawalAssumptions(WithdrawalStrategyType.TAXABLE_FIRST),
                death,
                12, LocalDate.of(2026, 7, 1));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    private SocialSecurityHouseholdClaimingStrategy strategy(
            RetirementPlan plan, int primaryAge, int spouseAge,
            int primarySurvivorAge, int spouseSurvivorAge) {
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        return new SocialSecurityHouseholdClaimingStrategy(
                primaryAge, spouseAge,
                com.daviddunn.retirementplanner.domain.income
                        .SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                                primary.getBirthDate(), primaryAge),
                com.daviddunn.retirementplanner.domain.income
                        .SocialSecurityRetirementDateCalculator.calculateRetirementClaimDate(
                                spouse.getBirthDate(), spouseAge),
                survivor(primary, primarySurvivorAge),
                survivor(spouse, spouseSurvivorAge));
    }

    private SocialSecuritySurvivorClaimingCandidate survivor(Person person, int age) {
        return new SocialSecuritySurvivorClaimingCandidate(
                person.getBirthDate().plusYears(age), age, 0, "Age " + age);
    }

    private SocialSecurityIncome source(
            AccountOwnership owner, LocalDate start, String benefit, int age) {
        return new SocialSecurityIncome("Social Security", owner, start, null,
                new BigDecimal(benefit), age, BigDecimal.ZERO, 2025);
    }

    private SocialSecurityIncome primarySource(RetirementPlan plan) {
        return plan.getHousehold().getPrimaryPerson().getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow();
    }

    private SocialSecurityIncome spouseSource(RetirementPlan plan) {
        return plan.getHousehold().getSpouse().getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow();
    }

    private String json(RetirementPlan plan) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return mapper.writeValueAsString(plan);
    }

    private SocialSecurityStrategyRequest directRequest(
            RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        DeathScenarioAssumptions death = plan.getPlanningAssumptions()
                .getDeathScenarioAssumptions();
        LocalDate primaryDeath = death.getDeathScenario() == DeathScenario.PRIMARY_DIES
                ? LocalDate.of(death.getDeathYear(), 1, 1) : null;
        LocalDate spouseDeath = death.getDeathScenario() == DeathScenario.SPOUSE_DIES
                ? LocalDate.of(death.getDeathYear(), 1, 1) : null;
        return new SocialSecurityStrategyRequest(
                LocalDate.of(2026, 1, 1), LocalDate.of(2037, 12, 31),
                new SocialSecurityClaimingElection(AccountOwnership.PRIMARY,
                        primary.getBirthDate(), primarySource(plan)
                        .getFullRetirementMonthlyBenefit(), 2025,
                        strategy.primaryRetirementClaimDate()),
                new SocialSecurityClaimingElection(AccountOwnership.SPOUSE,
                        spouse.getBirthDate(), spouseSource(plan)
                        .getFullRetirementMonthlyBenefit(), 2025,
                        strategy.spouseRetirementClaimDate()),
                spouseDeath == null ? null
                        : strategy.primarySurvivorElection().claimDate(),
                primaryDeath == null ? null
                        : strategy.spouseSurvivorElection().claimDate(),
                primaryDeath, spouseDeath,
                plan.getPlanningAssumptions().getSocialSecurityColaRate());
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private void assertDirectReconciliation(
            RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy strategy) {
        IntegratedSocialSecurityStrategyResult integrated = evaluator.evaluate(plan, strategy);
        SocialSecurityLifetimeResult direct = new SocialSecurityStrategyCalculator()
                .calculate(directRequest(plan, strategy));
        for (SocialSecurityAnnualResult annual : direct.annualResults()) {
            ProjectionYear year = integrated.projection().getYears().stream()
                    .filter(value -> value.getCalendarYear() == annual.calendarYear())
                    .findFirst().orElseThrow();
            assertMoney(annual.householdBenefits(),
                    year.getSocialSecurityResult().householdBenefit());
            assertMoney(annual.primarySelectedBenefits(),
                    year.getSocialSecurityResult().primarySelectedBenefit());
            assertMoney(annual.spouseSelectedBenefits(),
                    year.getSocialSecurityResult().spouseSelectedBenefit());
        }
    }
}
