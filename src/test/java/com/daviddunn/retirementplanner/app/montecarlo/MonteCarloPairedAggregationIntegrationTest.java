package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonTest.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPairedAggregationTest.money;

class MonteCarloPairedAggregationIntegrationTest {
    @Test
    void lowerExpensesAgreeWithIndependentEngineAndAnnualDifferences() {
        var a = MonteCarloFundingTest.plan("10000", "100", 3);
        var b = MonteCarloFundingTest.plan("10000", "150", 3);
        var request = fixed(a, b, 3, 2029);
        var result = run(request);
        for (var pair : result.outcomes()) {
            var world = request.worldSource().apply(pair.scenarioIndex());
            oracle(a, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeA(), 2029);
            oracle(b, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeB(), 2029);
        }
        var s = result.summary();
        money("150", s.terminalInvestableAssetsDifference().meanDifference().orElseThrow());
        money("150", s.terminalInvestableAssetsDifference().differencePercentiles().orElseThrow().p50());
        assertEquals(3, s.terminalInvestableAssetsDifference().greaterCount());
        money("1", s.terminalInvestableAssetsDifference().greaterProbabilityAmongComparable().orElseThrow());
        for (var entry : s.annualResults().entrySet()) {
            var pair = result.outcomes().getFirst();
            var expected = pair.outcomeA().annualInvestableAssets().get(entry.getKey())
                    .subtract(pair.outcomeB().annualInvestableAssets().get(entry.getKey()));
            assertEquals(0, expected.compareTo(entry.getValue().investableAssetsDifference().meanDifference().orElseThrow()));
        }
    }

    @Test
    void socialSecurityElectionsShareExactPathsAndHaveEngineAuthoritativeDifferences() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        var person = b.getHousehold().getPrimaryPerson();
        var old = person.getIncomeSources().stream().filter(SocialSecurityIncome.class::isInstance).findFirst().orElseThrow();
        person.removeIncomeSource(old);
        person.addIncomeSource(new SocialSecurityIncome("Alex SS", AccountOwnership.PRIMARY,
                person.getBirthDate().plusYears(67), null, new BigDecimal("3000"), 67, BigDecimal.ZERO, 2027));
        var request = longevity(a, b, 1);
        var world = new MonteCarloComparisonWorld(0, ProjectionEconomicPath.constant(BigDecimal.ZERO),
                MonteCarloMortalityExecutionTest.world(0, 2040, 2045, ProjectionEconomicPath.constant(BigDecimal.ZERO)).lifetimeScenario(),
                new MonteCarloInflationGenerator().generate(2027, 2044, request.assumptions().settings(), 0));
        int[] calls = {0};
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan plan, ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                assertSame(world.economicPath(), path);
                assertSame(world.inflationPath().orElseThrow(), context.inflationPath().orElseThrow());
                assertSame(world.lifetimeScenario(), context.householdLifetimeScenario().orElseThrow());
                calls[0]++;
                return super.projectWithOutcome(plan, context, path);
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request, i -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(2, calls[0]);
        var pair = result.outcomes().getFirst();
        var context = ProjectionEvaluationContext.withLifetimeScenario(world.lifetimeScenario())
                .withExactEndingYear(2044).withSurvivorClaimingAge(67).withInflationPath(world.inflationPath().orElseThrow());
        oracle(a, context, world, pair.outcomeA(), 2044);
        oracle(b, context, world, pair.outcomeB(), 2044);
        var delta = pair.deltas().orElseThrow().investableAssets();
        assertNotEquals(0, delta.signum());
        assertEquals(0, delta.compareTo(result.summary().terminalInvestableAssetsDifference().meanDifference().orElseThrow()));
        assertSame(result.summary(), result.summary());
        assertEquals(result.summary(), MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), result.outcomes()));
        assertEquals(2, calls[0], "Reduction never executes projections");
    }

    @Test
    void moreTaxableIncomeProducesPositiveAMinusBTaxes() {
        var a = MonteCarloFundingTest.plan("1000000", "100", 3);
        var b = MonteCarloFundingTest.plan("1000000", "100", 3);
        a.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Taxable pension", AccountOwnership.PRIMARY,
                LocalDate.of(2027, 1, 1), null, new BigDecimal("10000"), BigDecimal.ZERO));
        var request = fixed(a, b, 1, 2029);
        var result = run(request);
        var pair = result.outcomes().getFirst();
        var world = request.worldSource().apply(0);
        oracle(a, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeA(), 2029);
        oracle(b, ProjectionEvaluationContext.empty().withExactEndingYear(2029), world, pair.outcomeB(), 2029);
        var expected = pair.outcomeA().terminal().orElseThrow().lifetimeTaxes()
                .subtract(pair.outcomeB().terminal().orElseThrow().lifetimeTaxes());
        assertTrue(expected.signum() > 0);
        var metric = result.summary().lifetimeTaxesDifference();
        assertEquals(0, expected.compareTo(metric.meanDifference().orElseThrow()));
        assertEquals(1, metric.greaterCount());
        assertEquals(0, metric.lessCount());
    }

    @Test
    void engineProducedFundingStatesReconcileWithoutImputedTerminalBalances() {
        var high = MonteCarloFundingTest.plan("1000", "100", 4);
        var low = MonteCarloFundingTest.plan("250", "100", 4);
        var pairs = new ArrayList<MonteCarloPairedOutcome>();
        for (var plans : List.of(List.of(high, high), List.of(high, low), List.of(low, high), List.of(low, low))) {
            var pair = run(fixed(plans.get(0), plans.get(1), 1, 2030)).outcomes().getFirst();
            pairs.add(new MonteCarloPairedOutcome(pairs.size(), pair.lifetimeScenario(), pair.outcomeA(), pair.outcomeB()));
        }
        var s = MonteCarloStrategyComparisonAccumulator.reduce(fixed(high, low, 4, 2030).assumptions(), pairs);
        assertEquals(new MonteCarloPairedStateSummary(4, 1, 1, 1, 1), s.pairedStates());
        assertEquals(1, s.terminalInvestableAssetsDifference().sampleCount());
        assertEquals(4, s.annualResults().get(2028).comparableCount());
        assertEquals(1, s.annualResults().get(2029).comparableCount());
        assertEquals(2, s.strategyAFailureStatistics().orElseThrow().count());
        assertEquals(2, s.strategyBFailureStatistics().orElseThrow().count());
    }
}
