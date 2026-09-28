package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonTest.*;

class MonteCarloPairedWorldTest {
    @Test
    void seed417WorldFingerprints() throws Exception {
        var plan = MonteCarloMortalityExecutionTest.plan();
        // Captured with generation sources byte-for-byte unchanged from starting HEAD 8249c50.
        var expected = List.of(
                "ce5a5aaf18e7184d57d6f758b5d752804902dc239a67bddbae9d0661c57f2361",
                "eec8301f4bcd29e757261dad5008e4e4a741037d6610e916ff7dfeb3040c9562",
                "31199beef286f444b6832a0db83232d82ec5d58bf2fa523b3f4ddbe493087521",
                "1811e7ca1691041f222f71a9dba48f101c9d05d54155a396fcd3ac247c8b9231",
                "5417539c75e51d6b8464c5337d2c33b0e26657b523b09b0a8c1b3347927cf220",
                "0b7744f1391085301cbf735088416568b493987dbfcdf3a6808b52887b50bfdc");
        int position = 0;
        for (boolean mortality : new boolean[]{false, true}) {
            var request = mortality ? longevity(plan, plan, 225) : fixedWithInflation(plan, 225, 2056);
            var source = request.worldSource();
            for (int index : List.of(0, 1, 224)) {
                var world = source.apply(index);
                String canonical = index + "|" + new TreeMap<>(world.economicPath().annualReturns())
                        + "|" + world.lifetimeScenario() + "|" + world.inflationPath().orElseThrow().annualRates();
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertEquals(expected.get(position++), hash);
                if (mortality) {
                    var original = new MonteCarloWorldGenerator(
                            ((MonteCarloStrategyComparisonRequest.Longevity) request.assumptions()).mortality());
                    sameWorld(MonteCarloComparisonWorld.from(original.generate(index)), world);
                } else {
                    var settings = request.assumptions().settings();
                    assertEquals(new MonteCarloScenarioGenerator().generate(2027, 2056, settings, index).annualReturns(),
                            world.economicPath().annualReturns());
                    assertEquals(new MonteCarloInflationGenerator().generate(2027, 2056, settings, index)
                                    .orElseThrow().annualRates(), world.inflationPath().orElseThrow().annualRates());
                }
            }
        }
    }

    static void sameWorld(MonteCarloComparisonWorld a, MonteCarloComparisonWorld b) {
        assertEquals(a.scenarioIndex(), b.scenarioIndex());
        assertEquals(a.lifetimeScenario(), b.lifetimeScenario());
        assertEquals(a.economicPath().annualReturns(), b.economicPath().annualReturns());
        assertEquals(a.inflationPath().map(ProjectionInflationPath::annualRates),
                b.inflationPath().map(ProjectionInflationPath::annualRates));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void generatesExactlyOnceAndBothSidesReceiveIdenticalPathObjects(boolean mortality) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var settings = MonteCarloSettings.forPlan(plan, 4, 417, new BigDecimal("0.12"))
                .withInflation(MonteCarloInflationTest.inflation("0.0175"));
        var request = mortality ? longevity(plan, plan, 4) : request(plan, plan,
                new MonteCarloStrategyComparisonRequest.Fixed(settings, LocalDate.of(2027, 1, 1), 2030,
                        HouseholdLifetimeScenario.bothSurvive()));
        var source = request.worldSource();
        var generated = new AtomicInteger();
        var evaluated = new AtomicInteger();
        var current = new AtomicReference<MonteCarloComparisonWorld>();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                var world = current.get();
                assertEquals(evaluated.getAndIncrement() / 2, world.scenarioIndex());
                assertSame(world.economicPath(), path);
                assertSame(world.inflationPath().orElseThrow(), c.inflationPath().orElseThrow());
                if (mortality) {
                    assertSame(world.lifetimeScenario(), c.householdLifetimeScenario().orElseThrow());
                } else {
                    assertTrue(c.householdLifetimeScenario().isEmpty(), "Preserve configured fixed-mode engine behavior");
                }
                return super.projectWithOutcome(p, c, path);
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request, i -> {
            assertEquals(i, generated.getAndIncrement());
            assertEquals(i * 2, evaluated.get());
            var world = source.apply(i);
            current.set(world);
            return world;
        }, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(4, generated.get());
        assertEquals(8, evaluated.get());
        assertEquals(4, result.outcomes().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eitherCandidateCanChangeWithoutAffectingAnyWorldOrTheOtherOutcome(boolean changeA) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var changed = MonteCarloMortalityExecutionTest.plan();
        changed.getHousehold().addExpense(new Expense("Extra", new BigDecimal("1000")));
        var original = longevity(plan, plan, 3);
        var replacement = new MonteCarloStrategyCandidate("Changed", changed);
        var request = new MonteCarloStrategyComparisonRequest(changeA ? replacement : original.strategyA(),
                changeA ? original.strategyB() : replacement, original.assumptions());
        var one = original.worldSource();
        var two = request.worldSource();
        for (int i : List.of(0, 1, 224)) {
            sameWorld(one.apply(i), two.apply(i));
        }
        var before = run(original);
        var after = run(request);
        for (int i = 0; i < 3; i++) {
            assertEquals(changeA ? before.outcomes().get(i).outcomeB() : before.outcomes().get(i).outcomeA(),
                    changeA ? after.outcomes().get(i).outcomeB() : after.outcomes().get(i).outcomeA());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void directSequentialReverseAndLargerCountSourcesHaveIdenticalPrefixes(boolean mortality) {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var small = mortality ? longevity(plan, plan, 2) : fixedWithInflation(plan, 2, 2030);
        var large = mortality ? longevity(plan, plan, 5000) : fixedWithInflation(plan, 5000, 2030);
        var sequential = small.worldSource();
        var direct = small.worldSource().apply(224);
        var values = new ArrayList<MonteCarloComparisonWorld>();
        for (int i = 0; i <= 224; i++) values.add(sequential.apply(i));
        sameWorld(direct, values.get(224));
        var larger = large.worldSource();
        for (int i = 224; i >= 0; i--) {
            sameWorld(values.get(i), sequential.apply(i));
            sameWorld(values.get(i), larger.apply(i));
        }
        var first = run(small).outcomes();
        var slightlyLarger = mortality ? longevity(plan, plan, 3) : fixedWithInflation(plan, 3, 2030);
        assertEquals(first, run(slightlyLarger).outcomes().subList(0, 2));
        if (!mortality) {
            var longer = fixedWithInflation(plan, 2, 2056).worldSource();
            for (int i : List.of(0, 1, 224)) {
                var shortWorld = sequential.apply(i);
                var longWorld = longer.apply(i);
                shortWorld.economicPath().annualReturns().forEach((year, rate) ->
                        assertEquals(rate, longWorld.economicPath().investmentReturnForYear(year)));
                shortWorld.inflationPath().orElseThrow().annualRates().forEach((year, rate) ->
                        assertEquals(rate, longWorld.inflationPath().orElseThrow().inflationForYear(year)));
            }
        }
    }

    private static MonteCarloStrategyComparisonRequest fixedWithInflation(RetirementPlan plan, int count, int last) {
        return request(plan, plan, new MonteCarloStrategyComparisonRequest.Fixed(
                MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal("0.12"))
                        .withInflation(MonteCarloInflationTest.inflation("0.0175")),
                LocalDate.of(2027, 1, 1), last, HouseholdLifetimeScenario.bothSurvive()));
    }

    @Test
    void progressCountsWorldsAndCancellationBetweenSidesPublishesNoPair() {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var request = fixed(plan, plan, 3, 2028);
        var progress = new ArrayList<AnalysisProgress>();
        new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request, progress::add, AnalysisCancellationToken.none());
        assertEquals(List.of(0, 1, 2, 3), progress.stream().map(AnalysisProgress::completedWork).toList());
        assertTrue(progress.stream().allMatch(p -> p.totalWork() == 3));
        var calls = new AtomicInteger();
        var cancelled = new AtomicBoolean();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                calls.incrementAndGet();
                var result = super.projectWithOutcome(p, c, path);
                cancelled.set(true);
                return result;
            }
        };
        progress.clear();
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloStrategyComparisonAnalyzer(engine)
                .analyzeComparison(request, progress::add, cancelled::get));
        assertEquals(1, calls.get());
        assertEquals(List.of(0), progress.stream().map(AnalysisProgress::completedWork).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void unexpectedErrorsAbortWithWorldOrSideContext(int failingStage) {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var request = fixed(plan, plan, 3, 2028);
        var calls = new AtomicInteger();
        var cause = new ArithmeticException("Injected software error");
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                if (calls.incrementAndGet() == failingStage) throw cause;
                return super.projectWithOutcome(p, c, path);
            }
        };
        var source = request.worldSource();
        var error = assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class,
                () -> new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request,
                        i -> { if (failingStage == 0) throw cause; return source.apply(i); },
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none()));
        assertEquals(failingStage, calls.get());
        assertEquals(0, error.scenarioIndex());
        assertEquals(MonteCarloStrategyComparisonAnalyzer.Stage.values()[failingStage], error.stage());
        assertSame(cause, error.getCause());
        assertTrue(error.getMessage().contains("417"));
    }

    @Test
    void incompatibleStartDemographicsAndFixedDeathTimingAreRejected() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        MonteCarloMortalityExecutionTest.configure(b, LocalDate.of(2028, 1, 1), 30, 67);
        var startMismatch = fixed(a, b, 1, 2056);
        assertThrows(IllegalArgumentException.class, () -> run(startMismatch));
        b = MonteCarloMortalityExecutionTest.plan();
        b.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1964, 1, 1));
        var mismatch = longevity(a, b, 1);
        assertThrows(IllegalArgumentException.class, () -> run(mismatch));
        var death = new HouseholdLifetimeScenario(Optional.of(java.time.Year.of(2030)), Optional.empty());
        var wrong = request(a, a, new MonteCarloStrategyComparisonRequest.Fixed(
                MonteCarloSettings.forPlan(a, 1, 417, BigDecimal.ZERO), LocalDate.of(2027, 1, 1), 2056, death));
        assertThrows(IllegalArgumentException.class, () -> run(wrong));
    }

    @Test
    void malformedWorldIsRejectedBeforeEitherSide() {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var request = fixed(plan, plan, 1, 2028);
        var world = new MonteCarloComparisonWorld(0, ProjectionEconomicPath.annual(Map.of(2027, BigDecimal.ZERO)),
                HouseholdLifetimeScenario.bothSurvive(), Optional.empty());
        var error = assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class, () -> run(request, world));
        assertEquals(MonteCarloStrategyComparisonAnalyzer.Stage.WORLD, error.stage());
        var wrongIndex = new MonteCarloComparisonWorld(1, ProjectionEconomicPath.constant(BigDecimal.ZERO),
                HouseholdLifetimeScenario.bothSurvive(), Optional.empty());
        assertEquals(MonteCarloStrategyComparisonAnalyzer.Stage.WORLD,
                assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class,
                        () -> run(request, wrongIndex)).stage());
    }

    @Test
    void cancellationBeforeGenerationAndAfterSecondSideNeverPublishesPartialResults() {
        var plan = MonteCarloFundingTest.plan("1000", "10", 2);
        var request = fixed(plan, plan, 1, 2028);
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloStrategyComparisonAnalyzer()
                .analyzeComparison(request, i -> { fail("Cancelled source must not be called"); return null; },
                        AnalysisProgressListener.none(), () -> true));
        var calls = new AtomicInteger();
        var progress = new ArrayList<AnalysisProgress>();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan p, ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                calls.incrementAndGet();
                return super.projectWithOutcome(p, c, path);
            }
        };
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloStrategyComparisonAnalyzer(engine)
                .analyzeComparison(request, progress::add, () -> calls.get() == 2));
        assertEquals(2, calls.get());
        assertEquals(List.of(0), progress.stream().map(AnalysisProgress::completedWork).toList());
    }

    @Test
    void longevityRequiresCandidateSurvivorElectionsAndValidCoverage() {
        var a = MonteCarloMortalityExecutionTest.plan();
        var b = MonteCarloMortalityExecutionTest.plan();
        MonteCarloMortalityExecutionTest.configure(b, LocalDate.of(2027, 1, 1), 30, null);
        var missing = longevity(a, b, 1);
        assertThrows(IllegalArgumentException.class, () -> run(missing));
        var request = longevity(a, a, 1);
        var early = MonteCarloComparisonWorld.from(MonteCarloMortalityExecutionTest.world(0, 2026, 2026,
                ProjectionEconomicPath.constant(BigDecimal.ZERO)));
        assertEquals(MonteCarloStrategyComparisonAnalyzer.Stage.WORLD,
                assertThrows(MonteCarloStrategyComparisonAnalyzer.ExecutionException.class, () -> run(request, early)).stage());
    }
}
