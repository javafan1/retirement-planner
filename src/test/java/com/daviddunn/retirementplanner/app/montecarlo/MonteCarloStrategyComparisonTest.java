package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetricsCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityExecutionTest.JSON;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPairedOutcome.Status.*;

class MonteCarloStrategyComparisonTest {
    static MonteCarloStrategyComparisonRequest fixed(RetirementPlan a, RetirementPlan b, int count, int last) {
        return request(a, b, new MonteCarloStrategyComparisonRequest.Fixed(
                MonteCarloSettings.forPlan(a, count, 417, BigDecimal.ZERO),
                a.getPlanningAssumptions().getProjectionStartDate(), last, HouseholdLifetimeScenario.bothSurvive()));
    }

    static MonteCarloStrategyComparisonRequest request(RetirementPlan a, RetirementPlan b,
            MonteCarloStrategyComparisonRequest.Assumptions assumptions) {
        return new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("A", a),
                new MonteCarloStrategyCandidate("B", b), assumptions);
    }

    static MonteCarloStrategyComparisonRequest longevity(RetirementPlan a, RetirementPlan b, int count) {
        return request(a, b, new MonteCarloStrategyComparisonRequest.Longevity(
                MonteCarloWorldGeneratorTest.request(a, MonteCarloSettings.forPlan(a, count, 417,
                        new BigDecimal("0.12")).withInflation(MonteCarloInflationTest.inflation("0.0175")))));
    }

    static MonteCarloStrategyComparisonResult run(MonteCarloStrategyComparisonRequest request) {
        return new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request);
    }

    static MonteCarloStrategyComparisonResult run(MonteCarloStrategyComparisonRequest request,
            MonteCarloComparisonWorld world) {
        return new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request, i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
    }

    static void oracle(RetirementPlan plan, ProjectionEvaluationContext context,
                       MonteCarloComparisonWorld world, MonteCarloStrategyOutcome outcome, int last) {
        var execution = new ProjectionEngine().projectWithOutcome(plan, context, world.economicPath());
        var annual = new TreeMap<Integer, BigDecimal>();
        execution.completedYears().forEach(y -> annual.put(y.getCalendarYear(), y.getEndingInvestableAssets()));
        assertEquals(annual, outcome.annualInvestableAssets());
        if (execution instanceof ProjectionExecutionResult.InsufficientFunds failure) {
            assertEquals(failure.fundingFailure(), outcome.fundingFailure().orElseThrow());
            assertTrue(outcome.terminal().isEmpty());
        } else {
            var metrics = new ProjectionMetricsCalculator().calculate(plan,
                    ((ProjectionExecutionResult.Completed) execution).projection());
            assertEquals(new MonteCarloMortalityAnalysisResult.TerminalOutcome(LocalDate.of(last, 12, 31),
                    metrics.endingInvestableAssets(), metrics.endingNetWorth(), metrics.afterTaxEstate(),
                    metrics.totalTaxes()), outcome.terminal().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void identicalStrategiesRepeatExactlyWithZeroDeltas(boolean mortality) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var request = mortality ? longevity(plan, plan, 8) : fixed(plan, plan, 8, 2056);
        var result = run(request);
        assertEquals(result, run(request));
        assertEquals(8, result.requestedPairedCount());
        for (var pair : result.outcomes()) {
            assertEquals(pair.outcomeA(), pair.outcomeB());
            if (pair.status() == BOTH_COMPLETED) {
                var delta = pair.deltas().orElseThrow();
                for (var value : List.of(delta.investableAssets(), delta.netWorth(), delta.afterTaxEstate(), delta.lifetimeTaxes())) {
                    assertEquals(0, value.signum());
                }
            } else {
                assertEquals(BOTH_FAILED, pair.status());
                assertTrue(pair.deltas().isEmpty());
            }
        }
        assertTrue(result.outcomes().stream().anyMatch(p -> p.status() == BOTH_COMPLETED));
        assertThrows(UnsupportedOperationException.class, () -> result.outcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.outcomes().getFirst().outcomeA().annualInvestableAssets().clear());
    }

    @Test
    void knownExpenseDifferenceAndUnequalNominalHorizonsUseExactRequestHorizon() {
        var a = MonteCarloFundingTest.plan("10000", "100", 1);
        var b = MonteCarloFundingTest.plan("10000", "150", 20);
        var request = fixed(a, b, 1, 2029);
        var world = request.worldSource().apply(0);
        var pair = run(request).outcomes().getFirst();
        assertEquals(BOTH_COMPLETED, pair.status());
        assertEquals(0, new BigDecimal("150").compareTo(pair.deltas().orElseThrow().investableAssets()));
        assertEquals(3, pair.outcomeA().annualInvestableAssets().size());
        assertEquals(3, pair.outcomeB().annualInvestableAssets().size());
        oracle(a, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeA(), 2029);
        oracle(b, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeB(), 2029);
    }

    @Test
    void partialFirstYearStillUsesEngineProration() {
        var a = MonteCarloFundingTest.plan("10000", "100", 1);
        var b = MonteCarloFundingTest.plan("10000", "200", 3);
        for (var plan : List.of(a, b)) {
            MonteCarloMortalityExecutionTest.configure(plan, LocalDate.of(2027, 7, 1), 5, 67);
        }
        var request = fixed(a, b, 1, 2028);
        var pair = run(request).outcomes().getFirst();
        oracle(a, ProjectionEvaluationContext.empty().withExactEndingYear(2028), request.worldSource().apply(0), pair.outcomeA(), 2028);
        oracle(b, ProjectionEvaluationContext.empty().withExactEndingYear(2028), request.worldSource().apply(0), pair.outcomeB(), 2028);
        assertTrue(pair.deltas().orElseThrow().investableAssets().compareTo(new BigDecimal("200")) < 0);
    }

    @Test
    void fundingAsymmetryAndBothFailuresRetainIndependentActualFailures() {
        var a = MonteCarloFundingTest.plan("1000", "100", 4);
        var b = MonteCarloFundingTest.plan("250", "100", 4);
        var request = fixed(a, b, 1, 2030);
        var pair = run(request).outcomes().getFirst();
        assertEquals(A_COMPLETED_B_FAILED, pair.status());
        assertTrue(pair.outcomeB().terminal().isEmpty());
        assertTrue(pair.deltas().isEmpty());
        oracle(b, ProjectionEvaluationContext.empty(), request.worldSource().apply(0), pair.outcomeB(), 2030);
        var reverse = run(fixed(b, a, 1, 2030)).outcomes().getFirst();
        assertEquals(A_FAILED_B_COMPLETED, reverse.status());
        assertEquals(pair.outcomeA(), reverse.outcomeB());
        assertEquals(pair.outcomeB(), reverse.outcomeA());
        var c = MonteCarloFundingTest.plan("150", "100", 4);
        var failed = run(fixed(b, c, 1, 2030)).outcomes().getFirst();
        assertEquals(BOTH_FAILED, failed.status());
        assertEquals(2029, failed.outcomeA().fundingFailure().orElseThrow().calendarYear());
        assertEquals(2028, failed.outcomeB().fundingFailure().orElseThrow().calendarYear());
        assertTrue(failed.deltas().isEmpty());
        assertTrue(failed.outcomeA().terminal().isEmpty());
        assertTrue(failed.outcomeB().terminal().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentSourcesRemainUnchangedAndReversingOrderPreservesEachOutcome(boolean mortality) {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        b.getHousehold().addExpense(new Expense("Alternative expense", new BigDecimal("900")));
        var beforeA = JSON.valueToTree(a);
        var beforeB = JSON.valueToTree(b);
        var request = mortality ? longevity(a, b, 5) : fixed(a, b, 5, 2056);
        var observed = new IdentityHashMap<RetirementPlan, com.fasterxml.jackson.databind.JsonNode>();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                assertNotSame(a, p);
                assertNotSame(b, p);
                observed.putIfAbsent(p, JSON.valueToTree(p));
                var result = super.projectWithOutcome(p, c, path);
                assertEquals(observed.get(p), JSON.valueToTree(p));
                return result;
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request);
        assertEquals(2, observed.size(), "Exactly two working plans reused across every world");
        var reverse = run(new MonteCarloStrategyComparisonRequest(request.strategyB(), request.strategyA(), request.assumptions()));
        for (int i = 0; i < result.outcomes().size(); i++) {
            assertEquals(result.outcomes().get(i).outcomeA(), reverse.outcomes().get(i).outcomeB());
            assertEquals(result.outcomes().get(i).outcomeB(), reverse.outcomes().get(i).outcomeA());
        }
        assertEquals(beforeA, JSON.valueToTree(a));
        assertEquals(beforeB, JSON.valueToTree(b));
        a.getHousehold().addExpense(new Expense("Later edit", new BigDecimal("999999999")));
        b.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1900, 1, 1));
        assertEquals(result, run(request), "Session capture must survive subsequent original-plan edits");
    }

    @Test
    void authoritativeSocialSecurityElectionChangesFinancialOutcomesOnly() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        var person = b.getHousehold().getPrimaryPerson();
        var old = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
        person.removeIncomeSource(old);
        person.addIncomeSource(new SocialSecurityIncome("Alex SS", AccountOwnership.PRIMARY,
                person.getBirthDate().plusYears(67), null, new BigDecimal("3000"), 67, BigDecimal.ZERO, 2027));
        var request = fixed(a, b, 1, 2040);
        var world = request.worldSource().apply(0);
        var pair = run(request, world).outcomes().getFirst();
        oracle(a, ProjectionEvaluationContext.empty().withExactEndingYear(2040), world, pair.outcomeA(), 2040);
        oracle(b, ProjectionEvaluationContext.empty().withExactEndingYear(2040), world, pair.outcomeB(), 2040);
        assertNotEquals(0, pair.deltas().orElseThrow().investableAssets().signum());
    }

    @Test
    void longevityUsesSameDeathsAndHorizonButEachCandidatesSurvivorElection() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        MonteCarloMortalityExecutionTest.configure(b, LocalDate.of(2027, 1, 1), 2, 70);
        var request = longevity(a, b, 1);
        var base = MonteCarloMortalityExecutionTest.world(0, 2030, 2040, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var world = new MonteCarloComparisonWorld(0, base.economicPath(), base.lifetimeScenario(),
                new MonteCarloInflationGenerator().generate(2027, 2039, request.assumptions().settings(), 0));
        var pair = run(request, world).outcomes().getFirst();
        var context = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario()).withExactEndingYear(2039)
                .withInflationPath(world.inflationPath().orElseThrow());
        oracle(a, context.withSurvivorClaimingAge(67), world, pair.outcomeA(), 2039);
        oracle(b, context.withSurvivorClaimingAge(70), world, pair.outcomeB(), 2039);
        assertEquals(LocalDate.of(2039, 12, 31), pair.outcomeA().terminal().orElseThrow().balanceDate());
        assertEquals(pair.outcomeA().terminal().orElseThrow().balanceDate(), pair.outcomeB().terminal().orElseThrow().balanceDate());
    }

    @Test
    void openingSecondDeathUsesExistingOpeningEstateWithNoProjectionCalls() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var request = longevity(a, a, 1);
        var world = MonteCarloMortalityExecutionTest.world(0, 2027, 2027, ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                fail("Opening death must not invoke engine");
                return null;
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request,
                i -> MonteCarloComparisonWorld.from(world), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var existing = MonteCarloMortalityExecutionTest.run(a, world).outcomes().getFirst();
        assertEquals(existing.terminal(), result.outcomes().getFirst().outcomeA().terminal());
        assertEquals(result.outcomes().getFirst().outcomeA(), result.outcomes().getFirst().outcomeB());
        assertTrue(result.outcomes().getFirst().outcomeA().annualInvestableAssets().isEmpty());
    }

    @Test
    void existingFixedAndLongevityResultsAgreeWithPairedOutcomes() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 5, 417, new BigDecimal("0.12"))
                .withInflation(MonteCarloInflationTest.inflation("0.0175"));
        var fixed = request(plan, plan, new MonteCarloStrategyComparisonRequest.Fixed(settings,
                LocalDate.of(2027, 1, 1), 2056, HouseholdLifetimeScenario.bothSurvive()));
        var previous = new MonteCarloAnalyzer().analyze(plan, settings);
        var current = run(fixed);
        for (int i = 0; i < 5; i++) {
            var pair = current.outcomes().get(i);
            var world = fixed.worldSource().apply(i);
            oracle(plan, ProjectionEvaluationContext.empty().withInflationPath(world.inflationPath().orElseThrow()),
                    world, pair.outcomeA(), 2056);
            assertEquals(previous.outcomes().get(i).fundingFailure(), pair.outcomeA().fundingFailure());
        }
        var longevity = longevity(plan, plan, 5);
        var old = new MonteCarloAnalyzer().analyzeMortality(plan,
                ((MonteCarloStrategyComparisonRequest.Longevity) longevity.assumptions()).mortality());
        var paired = run(longevity);
        for (int i = 0; i < 5; i++) {
            var expected = old.outcomes().get(i);
            var actual = paired.outcomes().get(i).outcomeA();
            assertEquals(expected.annualInvestableAssets(), actual.annualInvestableAssets());
            assertEquals(expected.terminal(), actual.terminal());
            assertEquals(expected.fundingFailure(), actual.fundingFailure());
        }
    }

    @Test
    void fixedConfiguredDeathRetainsTheExistingEnginePathAndSurvivorBehavior() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(),
                a.getWithdrawalAssumptions(), new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2030, 67),
                14, a.getProjectionStartDate()));
        var lifetime = new HouseholdLifetimeScenario(Optional.of(java.time.Year.of(2030)), Optional.empty());
        var request = request(plan, plan, new MonteCarloStrategyComparisonRequest.Fixed(
                MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO), a.getProjectionStartDate(), 2040, lifetime));
        var world = request.worldSource().apply(0);
        var pair = run(request).outcomes().getFirst();
        assertEquals(lifetime, pair.lifetimeScenario());
        oracle(plan, ProjectionEvaluationContext.empty(), world, pair.outcomeA(), 2040);
        assertEquals(pair.outcomeA(), pair.outcomeB());
    }

    @Test
    void unequalTerminalDatesAndAmbiguousOutcomesCannotBeConstructed() {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var first = run(fixed(plan, plan, 1, 2027)).outcomes().getFirst().outcomeA();
        var second = run(fixed(plan, plan, 1, 2028)).outcomes().getFirst().outcomeA();
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloPairedOutcome(0,
                HouseholdLifetimeScenario.bothSurvive(), first, second));
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloStrategyOutcome(
                Map.of(), Optional.empty(), Optional.empty()));
        var failedPlan = MonteCarloFundingTest.plan("1", "10", 2);
        var failure = run(fixed(failedPlan, failedPlan, 1, 2028)).outcomes().getFirst().outcomeA();
        assertThrows(IllegalArgumentException.class, () -> new MonteCarloStrategyOutcome(
                Map.of(), first.terminal(), failure.fundingFailure()));
    }
}
