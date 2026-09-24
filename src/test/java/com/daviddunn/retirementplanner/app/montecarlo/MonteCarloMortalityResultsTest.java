package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityExecutionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloMortalityResultsTest {

    @Test
    void fixedModeSeed417MatchesStartingHeadIncludingAllPercentilesAndDeterministicReference() throws Exception {
        // Captured by compiling the analyzer from d8c96dbc45d3848136ecb8ffd16bd9c9c98fdc0d
        // under a temporary class name and comparing complete result records with the current analyzer.
        var household = MonteCarloFixtures.household();
        var funded = new MonteCarloAnalyzer().analyze(household,
                MonteCarloSettings.forPlan(household, 225, 417, new BigDecimal("0.12")));
        var poor = MonteCarloFundingTest.plan("1000", "100", 15);
        var mixed = new MonteCarloAnalyzer().analyze(poor,
                MonteCarloSettings.forPlan(poor, 225, 417, new BigDecimal("0.25")));
        assertEquals(225, funded.completedCount());
        assertEquals(20, mixed.completedCount());
        assertEquals(205, mixed.fundingFailureCount());
        // Complete record text includes classifications, exact annual/terminal percentile values,
        // completed metrics, and the deterministic reference (including its funding-failed prefix).
        assertEquals("331a465e4025cd0a3d3ad810e8f662133861374b4e5f68203b338af96157833e", fingerprint(funded));
        assertEquals("3fbe8d554275db48949f6877c153b7a390e048845e4d211c8a87cb256f3c286b", fingerprint(mixed));
    }

    private static String fingerprint(MonteCarloAnalysisResult result) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void sequentialLazyExecutionProgressCountsFailuresAndCopiesPlanOnlyOnce() {
        var plan = fundingPlan("250");
        var request = request(plan, 5, "0");
        var generated = new AtomicInteger();
        var executed = new AtomicInteger();
        var isolatedPlans = Collections.newSetFromMap(new IdentityHashMap<RetirementPlan, Boolean>());
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan isolated,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                assertEquals(executed.get() + 1, generated.get());
                assertNotSame(plan, isolated);
                isolatedPlans.add(isolated);
                var outcome = super.projectWithOutcome(isolated, context, path);
                executed.incrementAndGet();
                return outcome;
            }
        };
        var progress = new ArrayList<AnalysisProgress>();
        var result = new MonteCarloAnalyzer(engine).analyzeMortality(plan, request, i -> {
            assertEquals(i, executed.get());
            generated.incrementAndGet();
            return world(i, i % 2 == 0 ? 2029 : 2031, i % 2 == 0 ? 2029 : 2031,
                    ProjectionEconomicPath.constant(BigDecimal.ZERO));
        }, progress::add, AnalysisCancellationToken.none());
        assertEquals(5, executed.get());
        assertEquals(1, isolatedPlans.size());
        assertEquals(3, result.completedCount());
        assertEquals(2, result.fundingFailureCount());
        assertEquals(new BigDecimal("0.6"), result.fundingProbability());
        assertEquals(5, result.requestedSimulationCount());
        assertEquals(0, progress.getFirst().completedWork());
        assertEquals(5, progress.getLast().completedWork());
        int previous = -1;
        for (var update : progress) {
            assertEquals(AnalysisPhase.MONTE_CARLO_SIMULATIONS, update.phase());
            assertEquals(5, update.totalWork());
            assertTrue(update.completedWork() > previous && update.completedWork() <= 5);
            previous = update.completedWork();
        }
        assertThrows(UnsupportedOperationException.class, () -> result.outcomes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.outcomes().getFirst().annualInvestableAssets().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.fundingFailureStatistics().orElseThrow().countByYear().clear());
        var copy = new ArrayList<>(result.outcomes());
        var rebuilt = new MonteCarloMortalityAnalysisResult(request, result.returnModel(), result.mortalityModel(), copy);
        copy.clear();
        assertEquals(result, rebuilt);
        assertThrows(IllegalArgumentException.class,
                () -> new MonteCarloMortalityAnalysisResult(request, "test", "test", copy));
        var reversed = new ArrayList<>(result.outcomes());
        Collections.reverse(reversed);
        assertThrows(IllegalArgumentException.class,
                () -> new MonteCarloMortalityAnalysisResult(request, "test", "test", reversed));
    }

    @Test
    void unexpectedEngineFailureAbortsWithOriginalCauseAfterValidFundingFailure() {
        var plan = fundingPlan("250");
        var calls = new AtomicInteger();
        var original = new ArithmeticException("Injected unexpected failure");
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan isolated,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                if (calls.getAndIncrement() == 1) {
                    throw original;
                }
                return super.projectWithOutcome(isolated, context, path);
            }
        };
        var error = assertThrows(MonteCarloExecutionException.class,
                () -> new MonteCarloAnalyzer(engine).analyzeMortality(plan, request(plan, 3, "0"),
                        i -> world(i, 2031, 2031, ProjectionEconomicPath.constant(BigDecimal.ZERO)),
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertEquals(2, calls.get());
        assertSame(original, error.getCause());
        assertEquals(1, error.scenarioIndex().orElseThrow());
        assertEquals(417, error.seed());
        assertEquals(MonteCarloExecutionException.Phase.SIMULATION, error.phase());
        assertEquals("CALLER_SUPPLIED_WORLDS", error.returnModel());
    }

    @Test
    void worldGenerationErrorsWrongIndicesAndMissingMarketCoverageAbort() {
        var plan = fundingPlan("250");
        var original = new IllegalArgumentException("World generation failure");
        var error = assertThrows(MonteCarloExecutionException.class,
                () -> new MonteCarloAnalyzer().analyzeMortality(plan, request(plan, 1, "0"), i -> {
                    throw original;
                }, AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertSame(original, error.getCause());
        assertThrows(MonteCarloExecutionException.class,
                () -> run(plan, world(1, 2031, 2031, ProjectionEconomicPath.constant(BigDecimal.ZERO))));
        assertThrows(MonteCarloExecutionException.class,
                () -> run(plan, world(0, 2031, 2031, ProjectionEconomicPath.annual(Map.of(2027, BigDecimal.ZERO)))));
    }

    @Test
    void cancellationAtScenarioBoundaryAndFinalProgressPublishesNoResult() {
        var plan = plan();
        var request = request(plan, 4, "0.12");
        var generator = new MonteCarloWorldGenerator(request);
        var expected = generator.generate(2);
        var before = JSON.valueToTree(plan);
        for (int boundary : List.of(0, 2, 4)) {
            var stop = new AtomicBoolean(boundary == 0);
            var generated = new AtomicInteger();
            assertThrows(AnalysisCancelledException.class,
                    () -> new MonteCarloAnalyzer().analyzeMortality(plan, request, i -> {
                        generated.incrementAndGet();
                        return generator.generate(i);
                    }, progress -> {
                        if (progress.completedWork() == boundary) {
                            stop.set(true);
                        }
                    }, stop::get));
            assertEquals(boundary, generated.get());
            assertEquals(before, JSON.valueToTree(plan));
            MonteCarloWorldGeneratorTest.assertWorldEquals(expected, generator.generate(2));
        }
        var resumed = new MonteCarloAnalyzer().analyzeMortality(plan, request);
        assertOracle(plan, expected, resumed.outcomes().get(2));
    }

    @Test
    void allFailedWorldsHaveNoTerminalValuesAndRetainOnlyCompletedPrefix() {
        var plan = fundingPlan("0");
        var result = new MonteCarloAnalyzer().analyzeMortality(plan, request(plan, 2, "0"),
                i -> world(i, 2031, 2031, ProjectionEconomicPath.constant(BigDecimal.ZERO)),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(0, result.completedCount());
        assertEquals(2, result.fundingFailureCount());
        assertEquals(BigDecimal.ZERO, result.fundingProbability());
        for (var outcome : result.outcomes()) {
            assertTrue(outcome.terminal().isEmpty());
            assertTrue(outcome.annualInvestableAssets().isEmpty());
            assertEquals(2027, outcome.fundingFailure().orElseThrow().calendarYear());
        }
    }
}
