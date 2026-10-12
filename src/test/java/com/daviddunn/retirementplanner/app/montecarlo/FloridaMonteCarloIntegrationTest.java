package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FloridaMonteCarloIntegrationTest {
    @Test
    void fixedAndLongevitySeededWorldsUseSameProductionFloridaBoundary() {
        var plan = MonteCarloMortalityExecutionTest.plan();
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(
                StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var checked = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(RetirementPlan scenario,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                assertEquals(StateTaxJurisdiction.FL, scenario.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
                var result = super.projectWithOutcome(scenario, context, path);
                for (var year : result.completedYears()) {
                    assertEquals(StateTaxJurisdiction.FL, year.getStateTaxJurisdiction());
                    assertEquals(0, year.getStateIncomeTax().signum());
                    assertEquals(year.getFederalIncomeTax(), year.getTotalIncomeTax());
                    checked.incrementAndGet();
                }
                return result;
            }
        };
        var settings = MonteCarloSettings.forPlan(plan, 25, 417, new BigDecimal("0.12"));
        var analyzer = new MonteCarloAnalyzer(engine);
        var fixed = analyzer.analyze(plan, settings);
        var longevity = analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings));
        assertEquals(25, fixed.completedCount());
        assertEquals(25, longevity.completedCount());
        assertTrue(checked.get() > 1000);
        assertEquals(fixed.outcomes(), analyzer.analyze(plan, settings).outcomes());
        assertEquals(longevity.outcomes(), analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings)).outcomes());
    }
}
