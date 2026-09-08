package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedPrefixEquivalenceTest.*;

/** Opt-in permanent full-partition oracle and warmed sequential end-to-end benchmark. */
class LongevityPrefixEquivalenceBenchmarkTest {
    @Test
    @EnabledIfSystemProperty(named = "stage5c3.fullBenchmark", matches = "true")
    void completeUniverseMatchesEveryReferencePositionAndRunsOnlyResultingRepresentatives() {
        var plan = Stage4TestPlans.plan();
        var candidates = LongevityWeightedEquivalenceBenchmarkTest.universe(plan);
        var scenarios = LongevityWeightedEquivalenceBenchmarkTest.productionScenarios(plan);
        var base = request(plan, candidates, scenarios);
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan, candidates, scenarios,
                base.valuationDate(), base.realDiscountRate(), Optional.empty(), base.detailRetention(), event -> {
                    if (event.phase() == AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE && event.completedWork() % 500 == 0)
                        System.out.println("Stage 5C3 proof progress " + event.completedWork() + "/" + event.totalWork());
                }, AnalysisCancellationToken.none());
        var exactPlanner = new LongevityWeightedStrategyEquivalencePlanner();
        var optimizedPlanner = new LongevityWeightedPrefixEquivalencePlanner();
        System.out.println("Stage 5C3 full reference WARMUP");
        exactPlanner.plan(request);
        System.out.println("Stage 5C3 prefix WARMUP");
        optimizedPlanner.plan(request);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long allocated = bean.getCurrentThreadAllocatedBytes();
        var exact = exactPlanner.plan(request);
        long baselineAllocation = bean.getCurrentThreadAllocatedBytes() - allocated;
        assertEquals(5184, candidates.size());
        long ownPairs = candidates.stream().map(s -> List.of(s.primaryRetirementClaimDate(), s.spouseRetirementClaimDate())).distinct().count();
        int first = plan.getPlanningAssumptions().getProjectionStartDate().getYear();
        int last = first + plan.getPlanningAssumptions().getProjectionLengthYears() - 1;
        long positive = scenarios.scenarios().stream().filter(s -> s.jointProbability().signum() > 0).count();
        // This fixture's uniform per-scenario effective keys were independently enumerated in inspection.
        assertEquals(ownPairs * positive, exact.scheduleCalculations());
        long baselineRows = ownPairs * scenarios.scenarios().stream().filter(s -> s.jointProbability().signum() > 0)
                .mapToLong(s -> Math.max(last, LongevityEquivalenceCoverage.secondDeath(s) - 1) - first + 1).sum();
        System.out.println("Stage 5C3 BASELINE millis=" + exact.elapsedTime().toMillis() + " logical=" + exact.scheduleRequests()
                + " calculations=" + exact.scheduleCalculations() + " fixtureAnnualRows=" + baselineRows
                + " allocatedBytes=" + baselineAllocation + " groups=" + exact.equivalenceGroupCount());
        for (int run = 1; run <= 3; run++) {
            allocated = bean.getCurrentThreadAllocatedBytes();
            var optimized = optimizedPlanner.plan(request);
            long allocation = bean.getCurrentThreadAllocatedBytes() - allocated;
            assertPartitions(exact, optimized);
            var work = optimized.coverageWork().orElseThrow();
            assertEquals(exact.scheduleCalculations(), work.totalAuthoritativeCalculations() + work.scheduleCalculationsAvoidedByCoverage());
            assertEquals(0, work.fallbackScenarios());
            assertEquals(0, work.failedScheduleRequests());
            assertEquals(exact.scheduleRequests(), work.logicalScheduleRequests());
            assertEquals(work.logicalScheduleRequests(), work.memberValidations());
            var coverage = LongevityEquivalenceCoverage.plan(scenarios.scenarios(), last, AnalysisCancellationToken.none());
            assertEquals(coverage.groupCount(), work.coverageGroups());
            assertEquals(ownPairs * coverage.groupCount(), work.carrierScheduleCalculations());
            assertEquals(ownPairs * (positive - coverage.carrierByMember().size()), work.independentScheduleCalculations());
            System.out.println("Stage 5C3 OPTIMIZED run=" + run + " millis=" + optimized.elapsedTime().toMillis()
                    + " allocatedBytes=" + allocation + " groups=" + optimized.equivalenceGroupCount() + " work=" + work);
        }
        // Normal service: one optimized proof, then Stage 5C2 for the resulting representatives only.
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compare(request);
        var proof = result.equivalencePlan().orElseThrow();
        assertPartitions(exact, proof);
        assertEquals(exact.equivalenceGroupCount(), result.work().continuationEvaluations());
        assertEquals(0, result.work().stageFourEvaluations());
        assertEquals(0, result.failedStrategyCount());
        assertEquals(candidates.size(), result.orderedEntries().size());
        for (int index = 0; index < candidates.size(); index++) {
            assertSame(candidates.get(index), result.orderedEntries().get(index).strategy());
            assertEquals(index + 1, result.orderedEntries().get(index).inputOrder());
            int representative = proof.representativeOrders().get(index);
            assertEquals(result.orderedEntries().get(representative - 1).aggregate(), result.orderedEntries().get(index).aggregate());
            assertEquals(result.orderedEntries().get(representative - 1).rank(), result.orderedEntries().get(index).rank());
        }
        System.out.println("Stage 5C3 END_TO_END planningMillis=" + proof.elapsedTime().toMillis()
                + " financialAndAssemblyMillis=" + result.elapsedTime().minus(proof.elapsedTime()).toMillis()
                + " combinedMillis=" + result.elapsedTime().toMillis() + " financialWork=" + result.work());
    }
}
