package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.*;

class Stage5DSequentialCharacterizationTest {
    static void sameResult(Object expected, Object actual) throws Exception {
        if (expected instanceof Optional<?> a && actual instanceof Optional<?> b) {
            assertEquals(a.isPresent(), b.isPresent());
            if (a.isPresent()) sameResult(a.orElseThrow(), b.orElseThrow());
        } else if (expected instanceof List<?> a && actual instanceof List<?> b) {
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) sameResult(a.get(i), b.get(i));
        } else if (expected != null && expected.getClass().isRecord()) {
            assertEquals(expected.getClass(), actual.getClass());
            for (var component : expected.getClass().getRecordComponents()) {
                if (!component.getName().equals("elapsedTime")) {
                    sameResult(component.getAccessor().invoke(expected), component.getAccessor().invoke(actual));
                }
            }
        } else {
            assertEquals(expected, actual);
        }
    }

    @Test
    void successfulEquivalenceRetainsOccurrencesBaselineDetailsAndOrderedProgress() throws Exception {
        var plan = Stage4TestPlans.plan();
        var a = strategy(plan, 67, 67);
        var b = strategy(plan, 67, 67);
        var progress = new ArrayList<AnalysisProgress>();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(a,b,a), scenarios(plan),
                LocalDate.of(2029,7,1), new BigDecimal("0.03"), Optional.of(a),
                new LongevityWeightedDetailRetentionPolicy(true, Set.of(2)), progress::add, AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request);
        assertEquals(2, result.work().continuationEvaluations());
        assertEquals(2, result.evaluationsAvoided());
        assertEquals(List.of(1,1,1), result.orderedEntries().stream().map(e -> e.rank().orElseThrow()).toList());
        assertSame(b, result.orderedEntries().get(1).strategy());
        assertEquals(List.of(false,true,false), result.orderedEntries().stream().map(e -> e.scenarioDetails().isPresent()).toList());
        assertTrue(result.baseline().orElseThrow().scenarioDetails().isPresent());
        assertEquals(List.of(0,1,2,3,4), progress.stream()
                .filter(p -> p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON).map(AnalysisProgress::completedWork).toList());
        sameResult(result, new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request));
    }

    @Test
    void failedRepresentativesReplayEveryOriginalAndKeepBaselineFirst() {
        var plan = LongevityWeightedStrategyEquivalenceTest.youngPlan();
        var a = Stage4TestPlans.strategy(plan);
        var mortality = LongevityContinuationTest.mortality(plan, List.of(2031), List.of(2040,2081));
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(a,a,a), mortality,
                LocalDate.of(2029,7,1), new BigDecimal("0.03"), Optional.of(a),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request);
        assertEquals(List.of(0,1,2,3), result.failures().stream().map(e -> e.inputOrder()).toList());
        assertEquals(4, result.work().continuationEvaluations());
        assertEquals(4, result.work().continuation().failedCarriers());
        assertEquals(8, result.work().continuation().fallbackIndependentRuns());
        assertEquals(12, result.work().projectionEngineRuns());
        assertEquals(0, result.evaluationsAvoided());
        assertTrue(result.rankedSuccessfulEntries().isEmpty());
    }

    @Test
    void finalProgressCancellationDoesNotReturnComparison() {
        var plan = Stage4TestPlans.plan();
        var cancelled = new AtomicBoolean();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, List.of(strategy(plan,67,67)), scenarios(plan),
                LocalDate.of(2029,7,1), new BigDecimal("0.03"), Optional.empty(),
                LongevityWeightedDetailRetentionPolicy.aggregateOnly(), p -> {
                    if (p.phase() == AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON && p.completedWork() == p.totalWork()) cancelled.set(true);
                }, cancelled::get);
        assertThrows(AnalysisCancelledException.class, () -> new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request));
    }

    @Test
    void emptyInputHasNoFinancialWork() {
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareSequential(request(Stage4TestPlans.plan(), List.of()));
        assertEquals(0, result.work().projectionEngineRuns());
        assertEquals(0, result.work().continuationEvaluations());
        assertTrue(result.orderedEntries().isEmpty());
    }
}
