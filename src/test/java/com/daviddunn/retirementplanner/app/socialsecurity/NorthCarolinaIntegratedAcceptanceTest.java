package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NorthCarolinaIntegratedAcceptanceTest {

    @Test
    void quickComparisonAndWeightedEvaluationPreserveNorthCarolinaAndIsolateThePlan() throws Exception {
        var plan = IowaIntegratedAnalysisTest.plan();
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.NC)));
        var before = Stage4TestPlans.json(plan);
        var strategy = Stage4TestPlans.strategy(plan);
        var quick = new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of(strategy));
        assertEquals(1, quick.uniqueCandidateCount());
        assertTrue(quick.entries().getFirst().successful());
        for (var result : List.of(quick.currentPlanBaseline(),
                quick.entries().getFirst().integratedResult().orElseThrow())) {
            assertFalse(result.projection().getYears().isEmpty());
            assertTrue(result.projection().getYears().stream().allMatch(year ->
                    year.getStateTaxJurisdiction() == StateTaxJurisdiction.NC
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
                var rate = year.getCalendarYear() < 2033 ? new BigDecimal("0.0324") : new BigDecimal("0.0299");
                var deduction = year.getCalendarYear() <= Math.min(outcome.primaryDeathYear(), outcome.spouseDeathYear())
                        ? new BigDecimal("25500") : new BigDecimal("12750");
                var base = year.getAdjustedGrossIncome().subtract(year.getTaxableSocialSecurity()).subtract(deduction).max(BigDecimal.ZERO);
                assertEquals(base.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP), year.getStateIncomeTax());
            }
            assertEquals(new com.daviddunn.retirementplanner.domain.estate.EstateAtSecondDeathCalculator()
                    .calculate(plan, direct, outcome.secondDeathDate()), outcome.estateSnapshot());
        }
        assertEquals(before, Stage4TestPlans.json(plan));
    }
}
