package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityWeightedComparisonTestSupport.strategy;

class LongevityWeightedComparisonBenchmarkTest {
    @Test
    void threeProductionDistributionCandidatesRetainOnlyOneSelectedDetail() throws Exception {
        var plan = Stage4TestPlans.plan();
        var source = Stage4TestPlans.json(plan);
        var table = SocialSecurityMortalityTables.ssaPeriod2022();
        var assumptions = new AnalyzerLongevityAssumptions(SocialSecurityMortalityCategory.MALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), LocalDate.of(2030, 1, 1), table.metadata(),
                SocialSecurityMortalityPartialYearConvention.NEXT_COMPLETE_BIRTHDAY_INTERVAL);
        var scenarios = new HouseholdLongevityScenarioFactory(table).create(plan.getHousehold().getPrimaryPerson().getBirthDate(),
                plan.getHousehold().getSpouse().getBirthDate(), assumptions);
        long started = System.nanoTime();
        var request = new LongevityWeightedIntegratedStrategyComparisonRequest(plan,
                List.of(strategy(plan, 62, 62), strategy(plan, 67, 67), strategy(plan, 70, 70)), scenarios,
                LocalDate.of(2029, 7, 1), new BigDecimal("0.03"), Optional.empty(),
                new LongevityWeightedDetailRetentionPolicy(false, Set.of(2)), progress -> {
                    if (progress.phase() == com.daviddunn.retirementplanner.domain.analysis.AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON
                            && (progress.completedWork() == 1 || progress.completedWork() == 3)) {
                        System.out.println("Stage 5 comparison completedCandidates=" + progress.completedWork()
                                + " elapsedMillis=" + (System.nanoTime() - started) / 1_000_000);
                    }
                }, AnalysisCancellationToken.none());
        var result = new LongevityWeightedIntegratedStrategyComparisonService().compareWithEquivalenceOnly(request);
        System.out.println("Stage 5 benchmark: Stage4Evaluations=" + result.work().stageFourEvaluations()
                + " ProjectionEngineRuns=" + result.work().projectionEngineRuns()
                + " retainedOutcomes=" + result.retainedDetailedScenarioOutcomeCount()
                + " aggregates=" + result.completedStrategyCount()
                + " planningMillis=" + result.equivalencePlan().orElseThrow().elapsedTime().toMillis());
        assertEquals(3, result.completedStrategyCount());
        assertEquals(3, result.work().stageFourEvaluations());
        assertEquals(7800, result.work().projectionEngineRuns());
        assertEquals(7800, result.work().completedScenarioEvaluations());
        assertEquals(2600, result.retainedDetailedScenarioOutcomeCount());
        assertEquals(0, result.failedStrategyCount());
        assertEquals(source, Stage4TestPlans.json(plan));
    }
}
