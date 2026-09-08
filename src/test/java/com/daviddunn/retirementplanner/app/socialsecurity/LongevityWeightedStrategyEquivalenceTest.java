package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rmd.OpeningRmdAccountData;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;

class LongevityWeightedStrategyEquivalenceTest {
    private final LongevityWeightedIntegratedStrategyComparisonService service =
            new LongevityWeightedIntegratedStrategyComparisonService();

    @Test
    void duplicatesAndInactiveSurvivorElectionsPreserveEveryIdentityRankDeltaAndSelectedDetail() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var differentElection = survivorAges(plan, first, 67, 67);
        var candidates = List.of(first, differentElection, first, strategy(plan, 70, 70));
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios(plan),
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.of(first),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of(1, 2, 3)),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = assertReference(request);
        assertEquals(List.of(1, 1, 1, 4), result.equivalencePlan().orElseThrow().representativeOrders());
        assertEquals(2, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertEquals(3, result.work().stageFourEvaluations()); // includes separate baseline
        assertEquals(2, result.evaluationsAvoided());
        assertEquals(6, result.work().projectionEngineRuns());
        assertEquals(8, result.retainedDetailedScenarioOutcomeCount());
        assertEquals(0, result.detailReevaluations());
        for (int index = 0; index < candidates.size(); index++) {
            assertSame(candidates.get(index), result.orderedEntries().get(index).strategy());
            assertEquals(index + 1, result.orderedEntries().get(index).inputOrder());
        }
        assertEquals(List.of(1, 2, 3), result.rankedSuccessfulEntries().stream()
                .filter(entry -> entry.aggregate().equals(result.orderedEntries().getFirst().aggregate()))
                .map(LongevityWeightedIntegratedStrategyComparisonEntry::inputOrder).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> result.equivalencePlan().orElseThrow().groups().getFirst().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"older", "young-primary-first", "young-spouse-first", "simultaneous", "partial", "bracket-fill"})
    void completeReferenceEqualityAcrossFinancialAndMortalityFixtures(String fixture) throws Exception {
        var plan = fixture.startsWith("young") || fixture.equals("partial") ? youngPlan() : Stage4TestPlans.plan();
        if (fixture.equals("partial")) {
            var a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                    a.getWithdrawalAssumptions(), a.getDeathScenarioAssumptions(), 5, LocalDate.of(2032, 7, 1)));
        }
        if (fixture.equals("bracket-fill")) {
            plan.setRothConversionRequest(new RothConversionRequest(true, 2030, BigDecimal.ZERO,
                    RothConversionStopRule.NEVER, RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                    RothConversionFrequency.ANNUAL));
        }
        var mortality = fixture.equals("young-primary-first") || fixture.equals("partial")
                ? mortality(plan, 63, 72) : fixture.equals("young-spouse-first")
                ? mortality(plan, 74, 61) : fixture.equals("simultaneous")
                ? mortality(plan, 74, 72) : scenarios(plan);
        var first = strategy(plan, 67, 67);
        var candidates = List.of(first, survivorAges(plan, first, 60, 60), survivorAges(plan, first, 67, 67),
                strategy(plan, 62, 70), first);
        var json = Stage4TestPlans.json(plan);
        var deterministic = Stage4TestPlans.json(new ProjectionEngine().project(plan));
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, mortality,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.of(first),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of(2, 3)),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = assertReference(request);
        assertEquals(0, result.failedStrategyCount());
        assertEquals(json, Stage4TestPlans.json(plan));
        assertEquals(deterministic, Stage4TestPlans.json(new ProjectionEngine().project(plan)));
        assertTrue(result.evaluationsAvoided() >= 1);
        if (fixture.startsWith("young") || fixture.equals("partial")) {
            assertNotEquals(result.equivalencePlan().orElseThrow().representativeOrders().get(1),
                    result.equivalencePlan().orElseThrow().representativeOrders().get(2));
        }
    }

    @Test
    void earlyDeathAndOwnVersusSurvivorSelectionsPreventGrouping() {
        var plan = youngPlan();
        var first = strategy(plan, 67, 62);
        var early = survivorAges(plan, first, 60, 60);
        var late = survivorAges(plan, first, 67, 67);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(early, late),
                mortality(plan, 63, 72), LocalDate.of(2030, 1, 1), BigDecimal.ZERO);
        var result = assertReference(request);
        assertEquals(2, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        var lifetime = new HouseholdLifetimeScenarioMapper().map(request.longevityScenarios().scenarios().getFirst());
        var provider = new SocialSecurityProjectionIncomeProvider();
        var a = provider.calculate(plan, 2030, 2044, ProjectionEvaluationContext.withSocialSecurityStrategy(early, lifetime));
        var b = provider.calculate(plan, 2030, 2044, ProjectionEvaluationContext.withSocialSecurityStrategy(late, lifetime));
        assertEquals(SocialSecurityBenefitSelection.SURVIVOR, a.get(2035).spouseSelection());
        assertEquals(SocialSecurityBenefitSelection.OWN, b.get(2035).spouseSelection());
        assertNotEquals(a, b);
    }

    @Test
    void extendedHorizonCannotHideLaterClaimingDifferences() {
        var plan = youngPlan(); // configured end 2034, elections 2037/2039
        var first = strategy(plan, 67, 67);
        var later = strategy(plan, 70, 70);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(first, later),
                mortality(plan, 80, 80), LocalDate.of(2030, 1, 1), BigDecimal.ZERO);
        var result = assertReference(request);
        assertEquals(2, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertNotEquals(result.orderedEntries().get(0).aggregate(), result.orderedEntries().get(1).aggregate());
    }

    @Test
    void individualValidationFailureIsNeverHiddenByAnEquivalentValidCandidate() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var invalid = new SocialSecurityHouseholdClaimingStrategy(62, 67, first.primaryRetirementClaimDate(),
                first.spouseRetirementClaimDate(), first.primarySurvivorElection(), first.spouseSurvivorElection());
        var result = assertReference(request(plan, List.of(first, invalid, first, invalid)));
        assertEquals(List.of(1, 2, 1, 4), result.equivalencePlan().orElseThrow().representativeOrders());
        assertEquals(2, result.failedStrategyCount());
        assertEquals(1, result.work().stageFourEvaluations());
        assertEquals(1, result.evaluationsAvoided());
    }

    @Test
    void provenGroupFinancialFailuresAreEvaluatedIndividuallyWithOriginalIdentity() {
        var plan = Stage4TestPlans.plan();
        plan.getAccountPortfolio().getAccounts().getFirst().setOpeningRmdAccountData(
                new OpeningRmdAccountData(2030, new BigDecimal("500000"), BigDecimal.ONE));
        var first = strategy(plan, 67, 67);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(first, survivorAges(plan, first, 67, 67), first), mortality(plan, 70, 72),
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO);
        var result = assertReference(request);
        assertEquals(1, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertEquals(3, result.work().stageFourEvaluations());
        assertEquals(3, result.failedStrategyCount());
        assertEquals(0, result.evaluationsAvoided());
        assertTrue(result.orderedEntries().stream().allMatch(entry -> entry.aggregate().isEmpty()));
    }

    @Test
    void invalidSurvivorPolicyRemainsIndividualEvenWhenDeathWouldMakeItInapplicable() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var invalid = new SocialSecurityHouseholdClaimingStrategy(first.primaryRetirementAge(), first.spouseRetirementAge(),
                first.primaryRetirementClaimDate(), first.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(59),
                        60, 0, "Invalid date"), first.spouseSurvivorElection());
        var result = assertReference(request(plan, List.of(first, invalid, invalid)));
        assertEquals(2, result.failedStrategyCount());
        assertEquals(3, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
    }

    @Test
    void detailForOnlyNonRepresentativeDoesNotLeakRepresentativeDetail() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(first, survivorAges(plan, first, 67, 67), first), scenarios(plan),
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO, Optional.empty(),
                new LongevityWeightedDetailRetentionPolicy(false, Set.of(2)),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = assertReference(request);
        assertEquals(1, result.work().stageFourEvaluations());
        assertEquals(2, result.retainedDetailedScenarioOutcomeCount());
        assertTrue(result.orderedEntries().getFirst().scenarioDetails().isEmpty());
        assertTrue(result.orderedEntries().getLast().scenarioDetails().isEmpty());
        assertSame(request.candidates().get(1), result.orderedEntries().get(1).strategy());
    }

    @ParameterizedTest
    @ValueSource(strings = {"planning", "boundary", "inside-stage4"})
    void cancellationNeverPublishesPartialGroups(String location) {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var stop = new AtomicBoolean();
        var inFinancialPhase = new AtomicBoolean();
        var checks = new AtomicInteger();
        var progress = new ArrayList<AnalysisProgress>();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(first, first), scenarios(plan),
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO, Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), p -> {
                    progress.add(p);
                    if (p.phase() == AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE && p.completedWork() == 1
                            && location.equals("planning")) stop.set(true);
                    if (p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON) {
                        inFinancialPhase.set(true);
                        if (p.completedWork() == 1 && location.equals("boundary")) stop.set(true);
                    }
                }, () -> stop.get() || (location.equals("inside-stage4") && inFinancialPhase.get()
                        && checks.incrementAndGet() >= 7));
        assertThrows(AnalysisCancelledException.class, () -> service.compare(request));
        if (location.equals("inside-stage4")) {
            assertEquals(List.of(0), progress.stream().filter(p -> p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON)
                    .map(AnalysisProgress::completedWork).toList());
        }
    }

    @Test
    void progressCountsEveryOriginalOccurrenceAfterSeparateProofPhase() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var progress = new ArrayList<AnalysisProgress>();
        service.compare(new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(first, first, first),
                scenarios(plan), LocalDate.of(2030, 1, 1), BigDecimal.ZERO, Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), progress::add, AnalysisCancellationToken.none()));
        assertEquals(List.of(0, 1, 2, 3), progress.stream()
                .filter(p -> p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON)
                .map(AnalysisProgress::completedWork).toList());
        assertEquals(List.of(0, 1, 2), progress.stream()
                .filter(p -> p.phase() == AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE)
                .map(AnalysisProgress::completedWork).toList());
    }

    @Test
    void emptyInputStillMatchesExactReference() {
        var result = assertReference(request(Stage4TestPlans.plan(), List.of()));
        assertEquals(0, result.work().stageFourEvaluations());
        assertEquals(0, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
    }

    @Test
    void equalHouseholdTotalsWithDifferentOwnerComponentsAreNotGrouped() {
        var original = Stage4TestPlans.plan();
        var primary = new Person("Symmetric", "Primary", LocalDate.of(1970, 6, 15));
        var spouse = new Person("Symmetric", "Spouse", primary.getBirthDate());
        for (var owner : List.of(AccountOwnership.PRIMARY, AccountOwnership.SPOUSE)) {
            var person = owner == AccountOwnership.PRIMARY ? primary : spouse;
            person.addIncomeSource(new SocialSecurityIncome("SS", owner, person.getBirthDate().plusYears(67),
                    null, new BigDecimal("1000"), 67, BigDecimal.ZERO, 2030));
        }
        var plan = new RetirementPlan(new Household(primary, spouse), original.getAccountPortfolio(), original.getPlanningAssumptions());
        var a = strategy(plan, 62, 70);
        var b = strategy(plan, 70, 62);
        var mortality = mortality(plan, 80, 80);
        var lifetime = new HouseholdLifetimeScenarioMapper().map(mortality.scenarios().getFirst());
        var provider = new SocialSecurityProjectionIncomeProvider();
        var first = provider.calculate(plan, 2030, 2049, ProjectionEvaluationContext.withSocialSecurityStrategy(a, lifetime));
        var second = provider.calculate(plan, 2030, 2049, ProjectionEvaluationContext.withSocialSecurityStrategy(b, lifetime));
        for (int year : first.keySet()) {
            assertEquals(first.get(year).householdBenefit(), second.get(year).householdBenefit());
        }
        assertNotEquals(first.get(2033).primarySelection(), second.get(2033).primarySelection());
        var result = assertReference(new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(a, b),
                mortality, LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(2, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
    }

    @Test
    void weightedComparisonDoesNotChangeDeterministicRanks() {
        var plan = Stage4TestPlans.plan();
        var universe = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        var grid = new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(62, 67, 70), List.of(62, 67, 70),
                universe.primarySurvivorCandidates().subList(0, 2), universe.spouseSurvivorCandidates().subList(0, 2),
                IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 0);
        var calculator = new IntegratedSocialSecurityCompleteStrategySearchCalculator();
        var before = calculator.calculate(grid);
        var candidates = before.entries().stream().map(IntegratedSocialSecurityCompleteStrategySearchEntry::strategy).toList();
        var weighted = service.compare(request(plan, candidates));
        var after = calculator.calculate(grid);
        assertEquals(before.entries(), after.entries());
        assertEquals(before.rankedSuccessfulEntries(), after.rankedSuccessfulEntries());
        assertEquals(before.currentPlanRank(), after.currentPlanRank());
        assertEquals(IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, after.rankingMeasure());
        assertEquals(LongevityWeightedComparisonObjective.EXPECTED_PV_AFTER_TAX_ESTATE, weighted.objective());
    }

    @Test
    void proofAndRepresentativeRunsUseFrozenInputsDespiteLiveEdits() {
        var plan = Stage4TestPlans.plan();
        var first = strategy(plan, 67, 67);
        var candidates = List.of(first, survivorAges(plan, first, 67, 67));
        var exact = service.compareExact(request(plan, candidates));
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios(plan),
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), p -> {
                    if (p.phase() == AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE && p.completedWork() == 1) {
                        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(BigDecimal.ONE);
                    }
                }, AnalysisCancellationToken.none());
        assertEquals(exact.orderedEntries(), service.compare(request).orderedEntries());
    }
    @Test
    void zeroProbabilityEarlyDeathIsSkippedWithoutRenormalization() {
        var plan = youngPlan();
        var first = strategy(plan, 67, 67);
        var mortality = PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(63, new BigDecimal("0.0")),
                        new SocialSecurityMortalityProbability(80, new BigDecimal("1.0"))),
                List.of(new SocialSecurityMortalityProbability(80, BigDecimal.ONE)));
        var result = assertReference(new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(survivorAges(plan, first, 60, 60), survivorAges(plan, first, 67, 67)), mortality,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(1, result.equivalencePlan().orElseThrow().equivalenceGroupCount());
        assertEquals(1, result.work().projectionEngineRuns());
        var aggregate = result.orderedEntries().getFirst().aggregate().orElseThrow();
        assertEquals(new BigDecimal("1.0"), aggregate.totalEvaluatedProbability());
        assertEquals(2, aggregate.originalScenarioCount());
    }
    private LongevityWeightedIntegratedStrategyComparisonResult assertReference(
            LongevityWeightedIntegratedStrategyComparisonRequest request) {
        var exact = service.compareExact(request);
        var optimized = service.compare(request);
        assertEquals(exact.orderedEntries(), optimized.orderedEntries());
        assertEquals(exact.rankedSuccessfulEntries(), optimized.rankedSuccessfulEntries());
        assertEquals(exact.baseline(), optimized.baseline());
        assertEquals(exact.metadata(), optimized.metadata());
        assertEquals(exact.status(), optimized.status());
        assertEquals(exact.failures(), optimized.failures());
        return optimized;
    }

    static SocialSecurityHouseholdClaimingStrategy survivorAges(RetirementPlan plan,
            SocialSecurityHouseholdClaimingStrategy own, int primaryAge, int spouseAge) {
        return new SocialSecurityHouseholdClaimingStrategy(own.primaryRetirementAge(), own.spouseRetirementAge(),
                own.primaryRetirementClaimDate(), own.spouseRetirementClaimDate(),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getPrimaryPerson().getBirthDate().plusYears(primaryAge),
                        primaryAge, 0, "Primary explicit " + primaryAge),
                new SocialSecuritySurvivorClaimingCandidate(plan.getHousehold().getSpouse().getBirthDate().plusYears(spouseAge),
                        spouseAge, 0, "Spouse explicit " + spouseAge));
    }

    static HouseholdLongevityScenarios mortality(RetirementPlan plan, int primaryAge, int spouseAge) {
        return PreparedLongevityTestSupport.create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1),
                List.of(new SocialSecurityMortalityProbability(primaryAge, BigDecimal.ONE)),
                List.of(new SocialSecurityMortalityProbability(spouseAge, BigDecimal.ONE)));
    }

    static RetirementPlan youngPlan() {
        var original = Stage4TestPlans.plan();
        var primary = new Person("Young", "Primary", LocalDate.of(1970, 2, 28));
        var spouse = new Person("Young", "Spouse", LocalDate.of(1972, 6, 15));
        primary.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.PRIMARY,
                primary.getBirthDate().plusYears(67), null, new BigDecimal("3000"), 67, BigDecimal.ZERO, 2030));
        spouse.addIncomeSource(new SocialSecurityIncome("SS", AccountOwnership.SPOUSE,
                spouse.getBirthDate().plusYears(67), null, new BigDecimal("1000"), 67, BigDecimal.ZERO, 2030));
        primary.addIncomeSource(new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2030, 1, 1), null,
                new BigDecimal("1000"), new BigDecimal("0.02"), new BigDecimal("500")));
        var household = new Household(primary, spouse);
        household.addExpense(new Expense("Living", new BigDecimal("40000")));
        return new RetirementPlan(household, original.getAccountPortfolio(), original.getPlanningAssumptions());
    }
}
