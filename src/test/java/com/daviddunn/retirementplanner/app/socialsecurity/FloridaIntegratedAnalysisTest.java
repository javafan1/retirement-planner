package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FloridaIntegratedAnalysisTest {
    @Test
    void deterministicAndWeightedAnalysesCarryStateThroughScenarioCopies() {
        var mi = Stage4TestPlans.plan();
        mi.getHousehold().getPrimaryPerson().addIncomeSource(new com.daviddunn.retirementplanner.domain.income.Pension(
                "Additional pension", AccountOwnership.PRIMARY, LocalDate.of(2030, 1, 1), null,
                new BigDecimal("3000"), BigDecimal.ZERO, new BigDecimal("1500")));
        var fl = new RetirementPlanScenarioCopyService().copy(mi);
        fl.setPlanningAssumptions(fl.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
        var strategy = Stage4TestPlans.strategy(mi);
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var a = evaluator.evaluate(mi, strategy);
        var b = evaluator.evaluate(fl, strategy);
        assertEquals(a.metrics().lifetimeHouseholdSocialSecurity(), b.metrics().lifetimeHouseholdSocialSecurity());
        for (var year : b.projection().getYears()) {
            assertEquals(StateTaxJurisdiction.FL, year.getStateTaxJurisdiction());
            assertEquals(0, year.getStateIncomeTax().signum());
        }
        assertTrue(b.projection().getYears().getLast().getAfterTaxEstateValue()
                .compareTo(a.projection().getYears().getLast().getAfterTaxEstateValue()) > 0);
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(mi,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "1")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator();
        var wa = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(mi, strategy, scenarios,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        var wb = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(fl, strategy, scenarios,
                LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertTrue(wb.expectedNominalEstateAtSecondDeath().compareTo(wa.expectedNominalEstateAtSecondDeath()) > 0);
        assertEquals(wa.totalEvaluatedProbability(), wb.totalEvaluatedProbability());
        assertEquals(wa.evaluatedStrategy(), wb.evaluatedStrategy());
    }
}
