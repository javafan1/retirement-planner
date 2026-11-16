package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken;
import com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ArizonaMonteCarloAcceptanceTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void controlledFixedWorldsMatchDeterministicArizonaTaxAndFunding(boolean couple) {
        var plan = ArizonaProjectionAcceptanceTest.plan(couple);
        var before = MonteCarloPlanDocument.capture(plan);
        var calls = new AtomicInteger();
        var analyzer = new MonteCarloAnalyzer(observingEngine(plan, calls));
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        var reference = new ProjectionEngine().project(plan);
        var result = analyzer.analyze(plan, settings, reference, index ->
                ProjectionEconomicPath.constant(new BigDecimal(index == 0 ? "0.04" : "-0.02")));
        assertEquals(2, result.completedCount());
        assertEquals(2, calls.get());
        assertEquals(before, MonteCarloPlanDocument.capture(plan));
        assertEquals(result.outcomes(), analyzer.analyze(plan, settings, reference, index ->
                ProjectionEconomicPath.constant(new BigDecimal(index == 0 ? "0.04" : "-0.02"))).outcomes());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void controlledDeathsMatchDeterministicTaxThroughJointDeathYearAndSingle2029(boolean primaryDiesFirst) {
        var plan = ArizonaProjectionAcceptanceTest.plan(true);
        var before = MonteCarloPlanDocument.capture(plan);
        var world = MonteCarloMortalityExecutionTest.world(0,
                primaryDiesFirst ? 2028 : 2030,
                primaryDiesFirst ? 2030 : 2028,
                ProjectionEconomicPath.constant(BigDecimal.ZERO));
        var calls = new AtomicInteger();
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        var result = new MonteCarloAnalyzer(observingEngine(plan, calls)).analyzeMortality(
                plan, IowaMonteCarloIntegrationTest.mortalityRequest(plan, settings), index -> world,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(1, calls.get());
        assertEquals(1, result.completedCount());
        var outcome = result.outcomes().getFirst();
        assertEquals(2029, outcome.finalLivingFinancialYear());
        assertEquals(3, outcome.annualInvestableAssets().size());
        MonteCarloMortalityExecutionTest.assertOracle(plan, world, outcome);
        var rows = new ProjectionEngine().projectWithOutcome(plan,
                MonteCarloMortalityExecutionTest.context(world), world.economicPath()).completedYears();
        assertEquals(2, rows.get(0).getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertEquals(1, rows.get(1).getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertEquals(1, rows.get(2).getMedicarePremiumCalculation().coveredMedicareParticipants());
        assertTrue(rows.get(2).getFederalStandardDeduction().compareTo(rows.get(1).getFederalStandardDeduction()) < 0);
        assertEquals(0, (primaryDiesFirst ? rows.get(1).getPrimaryRothConversion()
                : rows.get(1).getSpouseRothConversion()).signum());
        assertEquals(before, MonteCarloPlanDocument.capture(plan));
    }

    private static ProjectionEngine observingEngine(RetirementPlan original, AtomicInteger calls) {
        return new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(
                    RetirementPlan isolated, ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                assertNotSame(original, isolated);
                assertEquals(StateTaxJurisdiction.AZ,
                        isolated.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
                var expected = new ProjectionEngine().projectWithOutcome(original, context, path);
                var actual = super.projectWithOutcome(isolated, context, path);
                assertInstanceOf(ProjectionExecutionResult.Completed.class, actual);
                assertEquals(expected.completedYears().size(), actual.completedYears().size());
                for (int index = 0; index < actual.completedYears().size(); index++) {
                    var a = expected.completedYears().get(index);
                    var b = actual.completedYears().get(index);
                    ArizonaProjectionAcceptanceTest.assertArizonaYear(b);
                    assertEquals(a.stateTaxCalculation(), b.stateTaxCalculation());
                    assertEquals(a.getFederalIncomeTax(), b.getFederalIncomeTax());
                    assertEquals(a.getTaxFundingWithdrawal(), b.getTaxFundingWithdrawal());
                    assertEquals(a.getRmdDistributedInProjection(), b.getRmdDistributedInProjection());
                    assertEquals(a.getRothConversion(), b.getRothConversion());
                    assertEquals(a.getEndingInvestableAssets(), b.getEndingInvestableAssets());
                }
                calls.incrementAndGet();
                return actual;
            }
        };
    }
}
