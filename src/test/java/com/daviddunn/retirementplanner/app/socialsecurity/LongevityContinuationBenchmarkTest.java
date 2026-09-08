package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityContinuationTest.*;

class LongevityContinuationBenchmarkTest {
    @Test
    void olderProductionEveryOutcomeMatchesAndWorkCountsComeFromMortalityInputs() throws Exception {
        var p = Stage4TestPlans.plan();
        var s = LongevityWeightedEquivalenceBenchmarkTest.productionScenarios(p);
        var req = request(p, s);
        var exactEvaluator = new LongevityWeightedIntegratedStrategyEvaluator();
        var optimizedEvaluator = new LongevityWeightedContinuationEvaluator();
        exactEvaluator.evaluate(req);
        optimizedEvaluator.evaluate(req);
        long started = System.nanoTime();
        var exact = exactEvaluator.evaluate(req);
        long rawNanos = System.nanoTime() - started;
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        started = System.nanoTime();
        var optimized = optimizedEvaluator.evaluate(req, work::set);
        long optimizedNanos = System.nanoTime() - started;
        financialEquals(exact, optimized);
        int last = p.getPlanningAssumptions().getProjectionStartDate().getYear()
                + p.getPlanningAssumptions().getProjectionLengthYears() - 1;
        var eligible = s.scenarios().stream().filter(x -> x.jointProbability().signum() > 0
                && Math.max(x.primaryDeathDate().getYear(), x.spouseDeathDate().getYear()) > last).toList();
        long primaryPaths = eligible.stream().filter(x -> x.primaryDeathDate().getYear() < x.spouseDeathDate().getYear())
                .map(x -> x.primaryDeathDate().getYear()).distinct().count();
        long spousePaths = eligible.stream().filter(x -> x.spouseDeathDate().getYear() < x.primaryDeathDate().getYear())
                .map(x -> x.spouseDeathDate().getYear()).distinct().count();
        long simultaneous = eligible.stream().anyMatch(x -> x.primaryDeathDate().getYear() == x.spouseDeathDate().getYear()) ? 1 : 0;
        long early = s.scenarios().size() - eligible.size();
        long rawRows = s.scenarios().stream().mapToLong(x -> Math.max(last,
                Math.max(x.primaryDeathDate().getYear(), x.spouseDeathDate().getYear()) - 1) - 2030 + 1).sum();
        assertEquals(primaryPaths + spousePaths + simultaneous, work.get().carrierAttempts());
        assertEquals(early, work.get().independentEarlyHorizonRuns());
        assertEquals(primaryPaths + spousePaths + simultaneous + early, work.get().projectionStarts());
        assertEquals(0, work.get().fallbackIndependentRuns());
        assertEquals(work.get().projectionStarts(), work.get().completedProjections());
        System.out.println("Stage 5C2 OLDER rawRuns=" + exact.actualProjectionRunCount() + " rawRows=" + rawRows
                + " rawMillis=" + rawNanos / 1_000_000 + " optimizedMillis=" + optimizedNanos / 1_000_000
                + " speedup=" + BigDecimal.valueOf(rawNanos).divide(BigDecimal.valueOf(optimizedNanos), 2, java.math.RoundingMode.HALF_UP)
                + " work=" + work.get());
    }

    @Test
    void youngerEveryIndividualOutcomeIncludingFailuresMatchesStageFour() throws Exception {
        var p = LongevityWeightedStrategyEquivalenceTest.youngPlan();
        String before = Stage4TestPlans.json(p);
        var s = LongevityWeightedEquivalenceBenchmarkTest.productionScenarios(p);
        var req = request(p, s);
        // Public evaluators both abort at the same first failed member.
        var firstFailureWork = new AtomicReference<>(LongevityContinuationWork.zero());
        sameFailure(req, firstFailureWork);
        var session = new LongevityContinuationSession(p, req, s.scenarios().size(), ignored -> { });
        int success = 0, failed = 0;
        long independentNanos = 0, optimizedNanos = 0;
        for (var member : s.scenarios()) {
            var singleton = request(p, mortality(p, List.of(member.primaryDeathDate().getYear()),
                    List.of(member.spouseDeathDate().getYear())));
            LongevityWeightedIntegratedScenarioOutcome reference = null;
            RuntimeException referenceFailure = null;
            long started = System.nanoTime();
            try {
                reference = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(singleton).scenarioOutcomes().getFirst();
            } catch (RuntimeException exception) {
                referenceFailure = (RuntimeException) exception.getCause();
            }
            independentNanos += System.nanoTime() - started;
            session.scenarioStarted();
            started = System.nanoTime();
            try {
                var actual = session.snapshot(member);
                assertNull(referenceFailure);
                assertEquals(reference.estateSnapshot(), actual);
                var factor = new com.daviddunn.retirementplanner.domain.estate.EstatePresentValueCalculator().discountFactor(
                        req.valuationDate(), actual.effectiveSecondDeathDate(),
                        p.getPlanningAssumptions().getGeneralInflationRate(), req.realDiscountRate());
                assertEquals(reference.pvEstate(), actual.nominalAfterTaxEstate().multiply(factor));
                session.scenarioCompleted();
                success++;
            } catch (RuntimeException exception) {
                assertNotNull(referenceFailure);
                assertEquals(referenceFailure.getClass(), exception.getClass());
                assertEquals(referenceFailure.getMessage(), exception.getMessage());
                failed++;
            }
            optimizedNanos += System.nanoTime() - started;
        }
        assertEquals(s.scenarios().size(), success + failed);
        assertTrue(success > 0 && failed > 0);
        assertEquals(before, Stage4TestPlans.json(p));
        System.out.println("Stage 5C2 YOUNGER individual diagnostic success=" + success + " failures=" + failed
                + " rawMillis=" + independentNanos / 1_000_000 + " optimizedMillis=" + optimizedNanos / 1_000_000
                + " work=" + session.work() + " firstFailureWork=" + firstFailureWork.get());
    }

    @ParameterizedTest
    @ValueSource(strings={"older","young-primary","young-spouse","simultaneous","early","bracket","fixed","failure"})
    void comparisonIntegratesEquivalenceAndContinuationsWithoutChangingFinancialEntries(String fixture) throws Exception {
        var p = fixture.startsWith("young") || fixture.equals("failure")
                ? LongevityWeightedStrategyEquivalenceTest.youngPlan() : Stage4TestPlans.plan();
        if(fixture.equals("fixed") || fixture.equals("bracket")) p.setRothConversionRequest(
                new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true,2030,new BigDecimal("100000"),
                        fixture.equals("fixed") ? com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD
                                : com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                        fixture.equals("fixed") ? com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT
                                : com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                        com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
        var s = fixture.equals("young-spouse") ? mortality(p,List.of(2040,2050),List.of(2036))
                : fixture.equals("simultaneous") ? mortality(p,List.of(2040,2050),List.of(2040,2050))
                : fixture.equals("early") ? mortality(p,List.of(2031),List.of(2032,2033))
                : fixture.equals("failure") ? mortality(p,List.of(2031),List.of(2040,2081))
                : mortality(p, List.of(2036), List.of(2040,2050));
        var candidates = List.of(Stage4TestPlans.strategy(p), Stage4TestPlans.strategy(p),
                LongevityWeightedComparisonTestSupport.strategy(p,62,70));
        var req = new LongevityWeightedIntegratedStrategyComparisonRequest(p,candidates,s,
                LocalDate.of(2029,7,1),new BigDecimal("0.03"),Optional.of(candidates.getFirst()),
                new LongevityWeightedDetailRetentionPolicy(true,Set.of(1,2,3)),AnalysisProgressListener.none(),AnalysisCancellationToken.none());
        var service = new LongevityWeightedIntegratedStrategyComparisonService();
        var exact = service.compareExact(req);
        var optimized = service.compare(req);
        assertEquals(exact.metadata(), optimized.metadata());
        assertEquals(exact.status(), optimized.status());
        assertEquals(exact.failures(),optimized.failures());
        assertEquals(exact.rankedSuccessfulEntries().stream().map(LongevityWeightedIntegratedStrategyComparisonEntry::inputOrder).toList(),
                optimized.rankedSuccessfulEntries().stream().map(LongevityWeightedIntegratedStrategyComparisonEntry::inputOrder).toList());
        var exactEntries=new ArrayList<>(exact.orderedEntries()); exactEntries.add(exact.baseline().orElseThrow());
        var optimizedEntries=new ArrayList<>(optimized.orderedEntries()); optimizedEntries.add(optimized.baseline().orElseThrow());
        for (int i=0;i<exactEntries.size();i++) {
            var a=exactEntries.get(i); var b=optimizedEntries.get(i);
            for(var component:LongevityWeightedIntegratedStrategyComparisonEntry.class.getRecordComponents()) {
                if(!component.getName().equals("aggregate")) assertEquals(
                        component.getAccessor().invoke(a),component.getAccessor().invoke(b));
            }
            if(!a.successful()) continue;
            for(var component:LongevityWeightedStrategyAggregate.class.getRecordComponents()) {
                if(!component.getName().equals("actualProjectionRunCount")) assertEquals(
                        component.getAccessor().invoke(a.aggregate().orElseThrow()),
                        component.getAccessor().invoke(b.aggregate().orElseThrow()));
            }
        }
        assertEquals(0,optimized.work().stageFourEvaluations());
        assertTrue(optimized.work().continuationEvaluations()>0);
        if(!fixture.equals("failure")) {
            assertTrue(optimized.work().projectionEngineRuns()<exact.work().projectionEngineRuns());
            assertTrue(optimized.evaluationsAvoided()>0);
        }
    }

    @Test
    @EnabledIfSystemProperty(named="stage5c2.allRepresentatives",matches="true")
    void allEightyOneRepresentativesSequentialFinancialWorkOnly() {
        var p=Stage4TestPlans.plan();
        var s=LongevityWeightedEquivalenceBenchmarkTest.productionScenarios(p);
        var evaluator=new LongevityWeightedContinuationEvaluator();
        evaluator.evaluate(request(p,s));
        long started=System.nanoTime();
        var total=new AtomicReference<>(LongevityContinuationWork.zero());
        int count=0;
        for(int primary=62;primary<=70;primary++) {
            for(int spouse=62;spouse<=70;spouse++) {
                var current=new AtomicReference<>(LongevityContinuationWork.zero());
                var result=evaluator.evaluate(new LongevityWeightedIntegratedStrategyRequest(p,
                        LongevityWeightedComparisonTestSupport.strategy(p,primary,spouse),s,
                        LocalDate.of(2029,7,1),new BigDecimal("0.03")),current::set);
                assertEquals(s.scenarios().size(),result.scenarioOutcomes().size());
                total.set(total.get().plus(current.get()));
                count++;
            }
        }
        System.out.println("Stage 5C2 representatives="+count+" financialMillis="+(System.nanoTime()-started)/1_000_000+" work="+total.get());
    }
}
