package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KentuckyIntegratedAcceptanceTest {

    @Test
    void quickComparisonAndWeightedEvaluationPreserveKentuckyAndIsolateThePlan() throws Exception {
        var plan = IowaIntegratedAnalysisTest.plan();
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.KY)));
        var before = Stage4TestPlans.json(plan);
        var strategy = Stage4TestPlans.strategy(plan);
        var quick = new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of(strategy));
        assertEquals(1, quick.uniqueCandidateCount());
        assertTrue(quick.entries().getFirst().successful());
        for (var result : List.of(quick.currentPlanBaseline(),
                quick.entries().getFirst().integratedResult().orElseThrow())) {
            assertFalse(result.projection().getYears().isEmpty());
            assertTrue(result.projection().getYears().stream().allMatch(year ->
                    year.getStateTaxJurisdiction() == StateTaxJurisdiction.KY
                            && year.stateTaxCalculation().orElseThrow().calculationStatus()
                            == StateTaxCalculationStatus.CALCULATED));
        }
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(plan,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(72, "0.25"),
                        LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "0.75")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, strategy, scenarios,
                        LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(0, BigDecimal.ONE.compareTo(weighted.totalEvaluatedProbability()));
        assertEquals(strategy, weighted.evaluatedStrategy());
        assertEquals(2, weighted.actualProjectionRunCount());
        for (var outcome : weighted.scenarioOutcomes()) {
            assertEquals(outcome.secondDeathDate().minusDays(1), outcome.estateSnapshot().balanceDate());
            var lifetime = new HouseholdLifetimeScenarioMapper().map(scenarios.scenarios().stream()
                    .filter(s -> new HouseholdLifetimeScenarioMapper().map(s).primaryDeathYear().orElseThrow().getValue()
                            == outcome.primaryDeathYear()).findFirst().orElseThrow());
            var direct = new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().project(plan,
                    com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext.withSocialSecurityStrategy(strategy, lifetime)
                            .withExactEndingYear(outcome.secondDeathDate().getYear() - 1));
            assertEquals(outcome.secondDeathDate().getYear() - 1, direct.getEndYear());
            assertTrue(direct.getYears().stream().anyMatch(y -> y.getCalendarYear() == 2033));
            for (var year : direct.getYears()) {
                assertEquals(StateTaxJurisdiction.KY, year.getStateTaxJurisdiction());
                assertEquals(StateTaxCalculationStatus.CALCULATED, year.stateTaxCalculation().orElseThrow().calculationStatus());
                assertEquals(year.getFederalIncomeTax().add(year.getStateIncomeTax()), year.getTotalIncomeTax());
                assertTrue(year.stateTaxCalculation().orElseThrow().limitations().getLast().contains("KY-2026-1"));
            }
            assertEquals(new com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator()
                    .calculate(plan, direct, outcome.secondDeathDate()), outcome.estateSnapshot());
        }
        assertEquals(before, Stage4TestPlans.json(plan));
    }
}
