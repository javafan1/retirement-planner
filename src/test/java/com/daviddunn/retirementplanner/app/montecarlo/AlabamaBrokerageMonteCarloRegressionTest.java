package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AlabamaBrokerageMonteCarloRegressionTest {

    @ParameterizedTest
    @ValueSource(strings = {"principal", "mixed", "conversion"})
    void twoControlledBrokerageWorldsMatchDeterministicAlabamaProjection(String scenario) {
        var plan = AlabamaBrokerageProjectionRegressionTest.plan(scenario);
        var before = MonteCarloPlanDocument.capture(plan);
        var calls = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(
                    RetirementPlan isolated, ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                assertNotSame(plan, isolated);
                var expected = new ProjectionEngine().projectWithOutcome(plan, context, path);
                var actual = super.projectWithOutcome(isolated, context, path);
                assertInstanceOf(ProjectionExecutionResult.Completed.class, expected);
                assertInstanceOf(ProjectionExecutionResult.Completed.class, actual);
                assertEquals(expected.completedYears().size(), actual.completedYears().size());
                AlabamaBrokerageProjectionRegressionTest.assertOrdinaryBrokerage(actual.completedYears().getFirst());
                for (int index = 0; index < actual.completedYears().size(); index++) {
                    var a = expected.completedYears().get(index);
                    var b = actual.completedYears().get(index);
                    assertEquals(a.stateTaxCalculation(), b.stateTaxCalculation());
                    assertEquals(a.getAdjustedGrossIncome(), b.getAdjustedGrossIncome());
                    assertEquals(a.getFederalIncomeTax(), b.getFederalIncomeTax());
                    assertEquals(a.getTaxFundingWithdrawal(), b.getTaxFundingWithdrawal());
                    assertEquals(a.getPortfolioWithdrawal(), b.getPortfolioWithdrawal());
                    assertEquals(a.getRothConversion(), b.getRothConversion());
                    assertEquals(a.getEndingInvestableAssets(), b.getEndingInvestableAssets());
                    AlabamaBrokerageProjectionRegressionTest.assertCashConservation(b);
                }
                calls.incrementAndGet();
                return actual;
            }
        };
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO);
        var reference = new ProjectionEngine().project(plan);
        var result = new MonteCarloAnalyzer(engine).analyze(plan, settings, reference, index ->
                ProjectionEconomicPath.constant(new BigDecimal(index == 0 ? "0.04" : "-0.02")));
        assertEquals(2, result.completedCount());
        assertEquals(2, calls.get());
        assertEquals(before, MonteCarloPlanDocument.capture(plan));
    }
}
