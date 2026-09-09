package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.PreparedLongevityTestSupport;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.socialsecurity.LongevityContinuationTest.*;

class Stage5GContinuationTest {
    @Test
    void inferredProductionGeometryHas112PathsWithoutAnyFinancialEvaluation() {
        var p = LongevityWeightedStrategyEquivalenceTest.youngPlan();
        var primary = IntStream.range(0, 56).mapToObj(index ->
                new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability(
                        61 + index, new BigDecimal(index == 55 ? "0.45" : "0.01"))).toList();
        var spouse = IntStream.range(0, 58).mapToObj(index ->
                new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityProbability(
                        59 + index, new BigDecimal(index == 57 ? "0.43" : "0.01"))).toList();
        var scenarios = PreparedLongevityTestSupport.create(p.getHousehold().getPrimaryPerson().getBirthDate(),
                p.getHousehold().getSpouse().getBirthDate(), LocalDate.of(2030, 1, 1), primary, spouse).scenarios();
        assertEquals(3248, scenarios.size());
        assertEquals(1225, scenarios.stream().filter(s -> LongevityScenarioContinuationPlanner.secondDeathYear(s) <= 2065).count());
        var groups = new LongevityScenarioContinuationPlanner().plan(scenarios, LocalDate.of(2030, 1, 1),
                AnalysisCancellationToken.none());
        assertEquals(112, groups.size());
        assertEquals(3248, groups.values().stream().mapToInt(group -> group.members().size()).sum());
    }

    @Test
    void entireEarlyCartesianRegionUsesFirstDeathPathsAndMatchesIndependentReference() throws Exception {
        var p = Stage4TestPlans.plan();
        Stage5GPrefixCharacterizationTest.configure(p, 20, false);
        var years = IntStream.rangeClosed(2031, 2038).boxed().toList();
        var req = request(p, mortality(p, years, years));
        String before = Stage4TestPlans.json(p);
        var reference = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req);
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        var actual = new LongevityWeightedContinuationEvaluator().evaluate(req, work::set);
        financialEquals(reference, actual);
        assertEquals(64, reference.actualProjectionRunCount());
        assertEquals(15, actual.actualProjectionRunCount(), "7 primary-first + 7 spouse-first + 1 simultaneous");
        assertEquals(15, work.get().carrierAttempts());
        assertEquals(15, work.get().successfulCarriers());
        assertEquals(0, work.get().independentEarlyHorizonRuns());
        assertEquals(0, work.get().fallbackIndependentRuns());
        assertEquals(0, work.get().failedCarriers());
        assertEquals(64, work.get().scenariosStarted());
        assertEquals(64, work.get().outcomesProduced());
        assertEquals(64, work.get().reusedOutcomes());
        assertEquals(work.get().carrierAttempts() + work.get().fallbackIndependentRuns(), work.get().projectionStarts());
        assertEquals(work.get().projectionStarts(), work.get().completedProjections());
        assertEquals(49, work.get().evaluationsAvoided());
        assertEquals(0, BigDecimal.ONE.compareTo(actual.totalEvaluatedProbability()));
        assertEquals(before, Stage4TestPlans.json(p));
    }

    @Test
    void openingMemberNeverCallsEngineAndSharesNoCarrierState() throws Exception {
        var p = Stage4TestPlans.plan();
        var req = request(p, mortality(p, List.of(2030), List.of(2030)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        var engine = new ProjectionEngine() {
            @Override public Projection project(com.daviddunn.retirementplanner.domain.model.RetirementPlan plan,
                    ProjectionEvaluationContext context) {
                fail("Opening snapshot must not invoke the engine");
                return null;
            }
        };
        var session = new LongevityContinuationSession(p, req, 1, work::set, engine);
        session.scenarioStarted();
        assertEquals(new EstateAtSecondDeathCalculator().calculateOpening(p, LocalDate.of(2030, 1, 1)),
                session.snapshot(req.longevityScenarios().scenarios().getFirst()));
        session.scenarioCompleted();
        assertEquals(0, work.get().projectionStarts());
        assertEquals(0, work.get().carrierAttempts());
        assertEquals(1, work.get().outcomesProduced());
        financialEquals(new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req),
                new LongevityWeightedContinuationEvaluator().evaluate(req));
    }

    @Test
    void failedLongCarrierPreservesSuccessfulShortMemberWithExactFallback() {
        var p = Stage4TestPlans.plan();
        Stage5GPrefixCharacterizationTest.configure(p, 20, false);
        p.getHousehold().addExpense(new Expense("Required only for long survivor", new BigDecimal("100000000"),
                GrowthCategory.GENERAL, LocalDate.of(2034, 1, 1), LocalDate.of(2034, 12, 31), ExpenseType.ONE_TIME));
        var req = request(p, mortality(p, List.of(2031), List.of(2032, 2036)));
        var work = new AtomicReference<>(LongevityContinuationWork.zero());
        sameFailure(req, work);
        assertEquals(1, work.get().failedCarriers());
        assertEquals(2, work.get().fallbackIndependentRuns());
        assertEquals(3, work.get().projectionStarts());
        assertEquals(1, work.get().completedProjections());
        assertEquals(1, work.get().outcomesProduced());
    }

    @Test
    void mixedOpeningAndReusedScenariosKeepProbabilityAndComparisonAccounting() throws Exception {
        var p = Stage4TestPlans.plan();
        var scenarios = mortality(p, List.of(2030, 2031), List.of(2030, 2032));
        var req = request(p, scenarios);
        var reference = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(req);
        var actual = new LongevityWeightedContinuationEvaluator().evaluate(req);
        financialEquals(reference, actual);
        assertEquals(3, reference.actualProjectionRunCount());
        assertEquals(3, actual.actualProjectionRunCount());
        var comparison = new LongevityWeightedIntegratedStrategyComparisonRequest(p,
                List.of(req.strategy(), req.strategy()), scenarios, req.valuationDate(), req.realDiscountRate(),
                Optional.of(req.strategy()), LongevityWeightedDetailRetentionPolicy.aggregateOnly(),
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        var service = new LongevityWeightedIntegratedStrategyComparisonService(2);
        var parallel = service.compare(comparison);
        Stage5DSequentialCharacterizationTest.sameResult(service.compareSequential(comparison), parallel);
        assertEquals(2, parallel.work().continuationEvaluations());
        assertEquals(8, parallel.work().scenarioEvaluations());
        assertEquals(6, parallel.work().projectionEngineRuns());
        assertEquals(6, parallel.work().completedProjectionEngineRuns());
        assertEquals(8, parallel.work().completedScenarioEvaluations());
    }
}
