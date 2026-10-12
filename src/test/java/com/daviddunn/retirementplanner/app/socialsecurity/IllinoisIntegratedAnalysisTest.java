package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IllinoisIntegratedAnalysisTest {
    @Test
    void deterministicAndWeightedEvaluationsUseCommonRoutingAndPreserveBenefits() {
        var mi = Stage4TestPlans.plan();
        var il = new RetirementPlanScenarioCopyService().copy(mi);
        il.setPlanningAssumptions(il.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.IL)));
        var strategy = Stage4TestPlans.strategy(mi);
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var a = evaluator.evaluate(mi, strategy);
        var b = evaluator.evaluate(il, strategy);
        assertEquals(a.metrics().lifetimeHouseholdSocialSecurity(), b.metrics().lifetimeHouseholdSocialSecurity());
        assertTrue(b.projection().getYears().stream().allMatch(y -> y.getStateTaxJurisdiction() == StateTaxJurisdiction.IL));
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(mi,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "1")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator();
        var wa = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(mi, strategy, scenarios,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        var wb = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(il, strategy, scenarios,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(wa.totalEvaluatedProbability(), wb.totalEvaluatedProbability());
        assertEquals(wa.evaluatedStrategy(), wb.evaluatedStrategy());
        assertEquals(StateTaxJurisdiction.IL, il.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        assertEquals(StateTaxJurisdiction.MI, mi.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
    }
}
