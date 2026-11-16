package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArizonaIntegratedAcceptanceTest {

    @Test
    void quickComparisonAndWeightedEvaluationPreserveArizonaAndIsolateThePlan() throws Exception {
        var plan = IowaIntegratedAnalysisTest.plan();
        plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.AZ)));
        var before = Stage4TestPlans.json(plan);
        var strategy = Stage4TestPlans.strategy(plan);
        var quick = new IntegratedSocialSecurityStrategyComparisonService().compare(plan, List.of(strategy));
        assertEquals(1, quick.uniqueCandidateCount());
        assertTrue(quick.entries().getFirst().successful());
        for (var result : List.of(quick.currentPlanBaseline(),
                quick.entries().getFirst().integratedResult().orElseThrow())) {
            assertFalse(result.projection().getYears().isEmpty());
            assertTrue(result.projection().getYears().stream().allMatch(year ->
                    year.getStateTaxJurisdiction() == StateTaxJurisdiction.AZ
                            && year.stateTaxCalculation().orElseThrow().calculationStatus()
                            == StateTaxCalculationStatus.CALCULATED));
        }
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(plan,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "1")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator().evaluate(
                new LongevityWeightedIntegratedStrategyRequest(plan, strategy, scenarios,
                        LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(0, BigDecimal.ONE.compareTo(weighted.totalEvaluatedProbability()));
        assertEquals(strategy, weighted.evaluatedStrategy());
        assertEquals(before, Stage4TestPlans.json(plan));
    }
}
