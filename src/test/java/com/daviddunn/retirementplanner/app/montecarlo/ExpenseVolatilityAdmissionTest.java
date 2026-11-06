package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;

class ExpenseVolatilityAdmissionTest {
    static void enable(RetirementPlan plan) {
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(), old.getGrowthCategory(),
                old.getStartDate(), old.getEndDate(), old.getExpenseType(), new BigDecimal("0.25")));
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void generatedComparisonsAndStandaloneSupportExpensesWhileSuppliedSourcesRemainRejected(boolean couple, boolean longevity) {
        var settings = new MonteCarloSettings(1, 42L, new BigDecimal("0.04"), new BigDecimal("0.1"));
        var a = plan(couple); var b = plan(couple);
        enable(b);
        var paired = comparison(a, b, settings, longevity,
                a.getPlanningAssumptions().getProjectionStartDate().getYear() + a.getPlanningAssumptions().getProjectionLengthYears() - 1);
        assertThrows(UnsupportedOperationException.class, () -> new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(paired,
                index -> { throw new AssertionError("World supplier executed"); }, PROGRESS, NEVER));
        enable(a);
        assertNotNull(new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(
                comparison(b, a, settings, longevity, a.getPlanningAssumptions().getProjectionStartDate().getYear())).expenseExecution());
        if (longevity) {
            var request = mortality(a, settings);
            assertNotNull(new MonteCarloAnalyzer().analyzeMortality(a, request).expenseExecution());
        } else {
            assertNotNull(new MonteCarloAnalyzer().analyze(a, settings).expenseExecution());
        }
    }
}
