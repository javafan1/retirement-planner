package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoIncomeTaxMonteCarloIntegrationTest {
    @Test void texasFixedSeededWorldsMatchFlorida() { compare(StateTaxJurisdiction.TX, false); }
    @Test void newHampshireLongevitySeededWorldsMatchFlorida() { compare(StateTaxJurisdiction.NH, true); }

    private void compare(StateTaxJurisdiction state, boolean longevity) {
        var fl = MonteCarloMortalityExecutionTest.plan();
        fl.setPlanningAssumptions(fl.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var candidate = new RetirementPlanScenarioCopyService().copy(fl);
        candidate.setPlanningAssumptions(candidate.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
        var checked = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(RetirementPlan scenario,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                var result = super.projectWithOutcome(scenario, context, path);
                for (var year : result.completedYears()) {
                    assertEquals(state, year.getStateTaxJurisdiction());
                    assertEquals(0, year.getStateIncomeTax().signum());
                    assertEquals(year.getFederalIncomeTax(), year.getTotalIncomeTax());
                    checked.incrementAndGet();
                }
                return result;
            }
        };
        var settings = MonteCarloSettings.forPlan(fl, 25, 417, new BigDecimal("0.12"));
        if (longevity) {
            var expected = new MonteCarloAnalyzer().analyzeMortality(fl, MonteCarloWorldGeneratorTest.request(fl, settings));
            var actual = new MonteCarloAnalyzer(engine).analyzeMortality(candidate, MonteCarloWorldGeneratorTest.request(candidate, settings));
            assertEquals(expected.outcomes(), actual.outcomes());
        } else {
            var expected = new MonteCarloAnalyzer().analyze(fl, settings);
            var actual = new MonteCarloAnalyzer(engine).analyze(candidate, settings);
            assertEquals(expected.outcomes(), actual.outcomes());
        }
        assertTrue(checked.get() > 500);
    }

    @Test void alaskaSinglePersonSeededWorldsMatchFlorida() {
        var fl = SinglePersonMortalityAnalysisTest.plan();
        fl.setPlanningAssumptions(fl.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var ak = new RetirementPlanScenarioCopyService().copy(fl);
        ak.setPlanningAssumptions(ak.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.AK)));
        var settings = MonteCarloSettings.forPlan(fl, 25, 417, new BigDecimal("0.12"));
        assertEquals(new MonteCarloAnalyzer().analyze(fl, settings).outcomes(), new MonteCarloAnalyzer().analyze(ak, settings).outcomes());
    }
}
