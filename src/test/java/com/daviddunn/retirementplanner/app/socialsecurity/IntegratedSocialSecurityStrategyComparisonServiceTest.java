package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgress;
import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class IntegratedSocialSecurityStrategyComparisonServiceTest {

    private final IntegratedSocialSecurityStrategyComparisonService service =
            new IntegratedSocialSecurityStrategyComparisonService();

    @Test
    void quickComparisonReportsBaselineAndActualUniqueCandidateWork() {
        RetirementPlan plan = plan();
        var first = strategy(plan, 62, 62, 60);
        var second = strategy(plan, 70, 70, 67);
        List<AnalysisProgress> updates = new ArrayList<>();

        var result = service.compare(plan, List.of(first, first, second), updates::add);

        assertEquals(2, result.entries().size());
        assertTrue(updates.stream().anyMatch(update -> update.phase()
                == AnalysisPhase.CURRENT_PLAN_BASELINE
                && update.completedWork() == 1 && update.totalWork() == 1));
        List<AnalysisProgress> candidates = updates.stream()
                .filter(update -> update.phase() == AnalysisPhase.QUICK_COMPARISON_CANDIDATES)
                .toList();
        assertEquals(0, candidates.getFirst().completedWork());
        assertEquals(2, candidates.getLast().completedWork());
        assertEquals(2, candidates.getLast().totalWork());
    }

    @Test
    void evaluatesThreeCandidatesInCallerOrderAndRetainsCompleteProjections()
            throws Exception {
        RetirementPlan plan = plan();
        String before = json(plan);
        SocialSecurityHouseholdClaimingStrategy first = strategy(plan, 62, 62, 60);
        SocialSecurityHouseholdClaimingStrategy second = strategy(plan, 67, 62, 67);
        SocialSecurityHouseholdClaimingStrategy third = strategy(plan, 70, 70, 64);

        IntegratedSocialSecurityStrategyComparisonResult result =
                service.compare(plan, List.of(first, second, third));

        assertEquals(3, result.suppliedCandidateCount());
        assertEquals(3, result.uniqueCandidateCount());
        assertEquals(List.of(first, second, third), result.entries().stream()
                .map(IntegratedSocialSecurityStrategyComparisonEntry::strategy).toList());
        assertEquals(List.of(1, 2, 3), result.entries().stream()
                .map(IntegratedSocialSecurityStrategyComparisonEntry::callerOrder).toList());
        assertTrue(result.entries().stream().allMatch(
                IntegratedSocialSecurityStrategyComparisonEntry::successful));
        assertTrue(result.entries().stream().allMatch(entry ->
                entry.integratedResult().orElseThrow().projection().size() == 12));
        assertEquals(before, json(plan));

        List<BigDecimal> socialSecurity = result.entries().stream()
                .map(entry -> entry.integratedResult().orElseThrow().metrics()
                        .lifetimeHouseholdSocialSecurity()).distinct().toList();
        assertEquals(3, socialSecurity.size());
    }

    @Test
    void exactDuplicatesAreEvaluatedOnceAndFirstCallerPositionIsRetained() {
        RetirementPlan plan = plan();
        SocialSecurityHouseholdClaimingStrategy first = strategy(plan, 62, 62, 60);
        SocialSecurityHouseholdClaimingStrategy second = strategy(plan, 67, 62, 67);
        SocialSecurityHouseholdClaimingStrategy third = strategy(plan, 70, 70, 64);

        IntegratedSocialSecurityStrategyComparisonResult result = service.compare(
                plan, List.of(first, second, first, third, second));

        assertEquals(5, result.suppliedCandidateCount());
        assertEquals(3, result.uniqueCandidateCount());
        assertEquals(List.of(1, 2, 4), result.entries().stream()
                .map(IntegratedSocialSecurityStrategyComparisonEntry::callerOrder).toList());
    }

    @Test
    void everyDifferenceIsCandidateMinusSingleCurrentPlanBaseline() {
        RetirementPlan plan = plan();
        IntegratedSocialSecurityStrategyComparisonResult result = service.compare(
                plan, List.of(strategy(plan, 62, 62, 60),
                        strategy(plan, 70, 70, 67)));
        ProjectionMetrics baseline = result.currentPlanBaseline().metrics();

        for (IntegratedSocialSecurityStrategyComparisonEntry entry : result.entries()) {
            ProjectionMetrics candidate = entry.integratedResult().orElseThrow().metrics();
            ProjectionMetrics difference = entry.differencesFromCurrentPlan().orElseThrow();
            assertMoney(candidate.totalInvestmentGrowth().subtract(
                    baseline.totalInvestmentGrowth()), difference.totalInvestmentGrowth());
            assertMoney(candidate.totalIncome().subtract(baseline.totalIncome()),
                    difference.totalIncome());
            assertMoney(candidate.totalTaxes().subtract(baseline.totalTaxes()),
                    difference.totalTaxes());
            assertMoney(candidate.peakAnnualTax().subtract(baseline.peakAnnualTax()),
                    difference.peakAnnualTax());
            assertMoney(candidate.endingInvestableAssets().subtract(
                    baseline.endingInvestableAssets()), difference.endingInvestableAssets());
            assertMoney(candidate.endingNetWorth().subtract(baseline.endingNetWorth()),
                    difference.endingNetWorth());
            assertMoney(candidate.afterTaxEstate().subtract(baseline.afterTaxEstate()),
                    difference.afterTaxEstate());
            assertMoney(candidate.lifetimePortfolioWithdrawals().subtract(
                    baseline.lifetimePortfolioWithdrawals()),
                    difference.lifetimePortfolioWithdrawals());
            assertMoney(candidate.lifetimeRothConversions().subtract(
                    baseline.lifetimeRothConversions()), difference.lifetimeRothConversions());
            assertMoney(candidate.lifetimeRequiredMinimumDistributions().subtract(
                    baseline.lifetimeRequiredMinimumDistributions()),
                    difference.lifetimeRequiredMinimumDistributions());
            assertMoney(candidate.lifetimeMedicarePremiums().subtract(
                    baseline.lifetimeMedicarePremiums()),
                    difference.lifetimeMedicarePremiums());
            assertMoney(candidate.lifetimeHouseholdSocialSecurity().subtract(
                    baseline.lifetimeHouseholdSocialSecurity()),
                    difference.lifetimeHouseholdSocialSecurity());
        }
    }

    @Test
    void invalidCandidateProducesStructuredFailureAndLaterCandidateStillSucceeds() {
        RetirementPlan plan = plan();
        SocialSecurityHouseholdClaimingStrategy first = strategy(plan, 62, 62, 60);
        SocialSecurityHouseholdClaimingStrategy validTemplate = strategy(plan, 67, 62, 64);
        SocialSecurityHouseholdClaimingStrategy invalid =
                new SocialSecurityHouseholdClaimingStrategy(
                        67, 62, validTemplate.primaryRetirementClaimDate().plusDays(1),
                        validTemplate.spouseRetirementClaimDate(),
                        validTemplate.primarySurvivorElection(),
                        validTemplate.spouseSurvivorElection());
        SocialSecurityHouseholdClaimingStrategy third = strategy(plan, 70, 70, 67);

        IntegratedSocialSecurityStrategyComparisonResult result =
                service.compare(plan, List.of(first, invalid, third));

        assertTrue(result.entries().get(0).successful());
        assertFalse(result.entries().get(1).successful());
        IntegratedSocialSecurityStrategyEvaluationFailure failure =
                result.entries().get(1).failure().orElseThrow();
        assertEquals(IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION,
                failure.category());
        assertTrue(failure.message().contains("does not match age"));
        assertTrue(result.entries().get(2).successful());
    }

    @Test
    void comparisonIsRepeatableIsolatedAndBaselineRemainsUnchanged() throws Exception {
        RetirementPlan plan = plan();
        String before = json(plan);
        IntegratedSocialSecurityStrategyEvaluator evaluator =
                new IntegratedSocialSecurityStrategyEvaluator();
        IntegratedSocialSecurityStrategyResult baselineBefore =
                evaluator.evaluateCurrentStrategy(plan);
        List<SocialSecurityHouseholdClaimingStrategy> strategies = List.of(
                strategy(plan, 62, 62, 60),
                strategy(plan, 67, 62, 67),
                strategy(plan, 70, 70, 64));

        IntegratedSocialSecurityStrategyComparisonResult first =
                service.compare(plan, strategies);
        IntegratedSocialSecurityStrategyComparisonResult second =
                service.compare(plan, strategies);
        IntegratedSocialSecurityStrategyResult baselineAfter =
                evaluator.evaluateCurrentStrategy(plan);

        assertEquals(first.currentPlanBaseline().metrics(),
                second.currentPlanBaseline().metrics());
        assertEquals(first.entries().stream().map(entry -> entry.integratedResult()
                        .orElseThrow().metrics()).toList(),
                second.entries().stream().map(entry -> entry.integratedResult()
                        .orElseThrow().metrics()).toList());
        assertEquals(first.entries().stream().map(entry -> entry.differencesFromCurrentPlan()
                        .orElseThrow()).toList(),
                second.entries().stream().map(entry -> entry.differencesFromCurrentPlan()
                        .orElseThrow()).toList());
        assertEquals(baselineBefore.metrics(), baselineAfter.metrics());
        assertEquals(before, json(plan));
    }

    @Test
    void baselineFailureAbortsComparison() {
        RetirementPlan plan = plan();
        SocialSecurityIncome spouseSource = plan.getHousehold().getSpouse()
                .getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast).findFirst().orElseThrow();
        plan.getHousehold().getSpouse().removeIncomeSource(spouseSource);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.compare(plan, List.of()));
        assertTrue(failure.getMessage().contains("advanced path is unavailable"));
    }

    @Test
    void retirementAndExactSurvivorDifferencesBothFlowThroughComparison() {
        RetirementPlan plan = plan();
        SocialSecurityHouseholdClaimingStrategy survivorEarly =
                strategy(plan, 67, 62, 60);
        SocialSecurityHouseholdClaimingStrategy survivorLate =
                strategy(plan, 67, 62, 67);
        SocialSecurityHouseholdClaimingStrategy retirementDifferent =
                strategy(plan, 62, 62, 60);

        IntegratedSocialSecurityStrategyComparisonResult result = service.compare(
                plan, List.of(survivorEarly, survivorLate, retirementDifferent));
        ProjectionMetrics early = result.entries().get(0).integratedResult()
                .orElseThrow().metrics();
        ProjectionMetrics survivor = result.entries().get(1).integratedResult()
                .orElseThrow().metrics();
        ProjectionMetrics retirement = result.entries().get(2).integratedResult()
                .orElseThrow().metrics();

        assertNotEquals(early.lifetimeHouseholdSocialSecurity(),
                survivor.lifetimeHouseholdSocialSecurity());
        assertNotEquals(early.lifetimeHouseholdSocialSecurity(),
                retirement.lifetimeHouseholdSocialSecurity());
        assertNotEquals(early.totalTaxes(), survivor.totalTaxes());
        assertNotEquals(early.lifetimePortfolioWithdrawals(),
                survivor.lifetimePortfolioWithdrawals());
        assertTrue(early.lifetimeMedicarePremiums().signum() > 0);
    }

    @Test
    void analyzerExpectedValuesRemainDistinctTraceabilityMetadata() {
        RetirementPlan plan = plan();
        SocialSecurityHouseholdClaimingStrategy strategy = strategy(plan, 67, 62, 60);
        SocialSecurityMortalityWeightedStrategyValue expected =
                new SocialSecurityMortalityWeightedStrategyValue(
                        new BigDecimal("300"), new BigDecimal("250"),
                        new BigDecimal("225"), new BigDecimal("200"),
                        new BigDecimal("100"), 4);

        IntegratedSocialSecurityStrategyComparisonResult result =
                service.compareAnalyzerCandidates(plan, List.of(
                        new SocialSecuritySurvivorClaimingOptimizationCell(strategy, expected)));

        IntegratedSocialSecurityStrategyComparisonEntry entry = result.entries().getFirst();
        assertSame(expected, entry.socialSecurityOnlyExpectedValue().orElseThrow());
        assertNotEquals(expected.expectedPresentValue(),
                entry.integratedResult().orElseThrow().metrics().afterTaxEstate());
    }

    private RetirementPlan plan() {
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
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2028, 62),
                12, LocalDate.of(2026, 7, 1));
        return new RetirementPlan(household, portfolio, assumptions);
    }

    private SocialSecurityHouseholdClaimingStrategy strategy(
            RetirementPlan plan,
            int primaryAge,
            int spouseAge,
            int spouseSurvivorAge) {
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
                survivor(primary, 60), survivor(spouse, spouseSurvivorAge));
    }

    private SocialSecuritySurvivorClaimingCandidate survivor(Person person, int age) {
        return new SocialSecuritySurvivorClaimingCandidate(
                person.getBirthDate().plusYears(age), age, 0, "Age " + age);
    }

    private SocialSecurityIncome source(
            AccountOwnership owner,
            LocalDate start,
            String benefit,
            int age) {
        return new SocialSecurityIncome("Social Security", owner, start, null,
                new BigDecimal(benefit), age, BigDecimal.ZERO, 2025);
    }

    private String json(RetirementPlan plan) throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(plan);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
