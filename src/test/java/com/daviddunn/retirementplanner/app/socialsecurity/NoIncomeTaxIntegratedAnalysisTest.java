package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NoIncomeTaxIntegratedAnalysisTest {
    static void select(RetirementPlan plan, StateTaxJurisdiction state) {
        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
    }

    @ParameterizedTest @EnumSource(value = StateTaxJurisdiction.class, names = {"AK", "NV", "NH", "SD", "TN", "TX", "WY"})
    void coupleGridAndWeightedOutcomesMatchFlorida(StateTaxJurisdiction state) {
        var fl = Stage4TestPlans.plan(); select(fl, StateTaxJurisdiction.FL);
        var candidate = new RetirementPlanScenarioCopyService().copy(fl); select(candidate, state);
        var grid = new IntegratedRetirementClaimingGridCalculator();
        var a = grid.calculate(fl); var b = grid.calculate(candidate);
        for (int i = 0; i < a.cells().size(); i++) {
            assertEquals(a.cells().get(i).integratedResult().orElseThrow().metrics(), b.cells().get(i).integratedResult().orElseThrow().metrics());
            assertTrue(b.cells().get(i).integratedResult().orElseThrow().projection().getYears().stream()
                    .allMatch(y -> y.getStateTaxJurisdiction() == state && y.getStateIncomeTax().signum() == 0));
        }
        var scenarios = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(fl,
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74, "0.4"), LongevityWeightedIntegratedStrategyEvaluatorTest.prob(80, "0.6")),
                List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73, "1")));
        var weighted = new LongevityWeightedIntegratedStrategyEvaluator();
        var strategy = Stage4TestPlans.strategy(fl);
        var wa = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(fl, strategy, scenarios, LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        var wb = weighted.evaluate(new LongevityWeightedIntegratedStrategyRequest(candidate, strategy, scenarios, LocalDate.of(2030, 1, 1), BigDecimal.ZERO));
        assertEquals(wa.expectedNominalEstateAtSecondDeath(), wb.expectedNominalEstateAtSecondDeath());
        assertEquals(wa.expectedPvAfterTaxEstate(), wb.expectedPvAfterTaxEstate());
        assertEquals(wa.totalEvaluatedProbability(), wb.totalEvaluatedProbability());
    }

    @ParameterizedTest @EnumSource(value = StateTaxJurisdiction.class, names = {"AK", "NV", "NH", "SD", "TN", "TX", "WY"})
    void singleDeterministicAndWeightedRankingsMatchFlorida(StateTaxJurisdiction state) {
        var fl = SinglePersonMortalityAnalysisTest.plan(); select(fl, StateTaxJurisdiction.FL);
        var candidate = new RetirementPlanScenarioCopyService().copy(fl); select(candidate, state);
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var strategy = evaluator.extractCurrentStrategy(fl);
        assertEquals(evaluator.evaluate(fl, strategy).metrics(), evaluator.evaluate(candidate, strategy).metrics());
        var mortality = SinglePersonMortalityAnalysisTest.mortality(fl, "1");
        var a = SinglePersonMortalityAnalysisTest.run(fl, mortality, "0.02", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        var b = SinglePersonMortalityAnalysisTest.run(candidate, mortality, "0.02", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        assertEquals(a.entries(), b.entries());
        assertEquals(a.ranked(), b.ranked());
    }
}
