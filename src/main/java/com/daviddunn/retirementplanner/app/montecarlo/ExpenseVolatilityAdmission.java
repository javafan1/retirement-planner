package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.Objects;

/** Supported generated execution with retained validation and supplied-source boundaries. */
public final class ExpenseVolatilityAdmission {
    private ExpenseVolatilityAdmission() { }

    public static void requireStandaloneSupported(RetirementPlan plan) {
        Objects.requireNonNull(plan);
        if (!ExpenseExecution.configured(plan)) return;
        // Pure bounded preparation only. No draws, projections or caller-owned input changes.
        new MonteCarloExpenseGenerator(plan.getHousehold().getExpenses().stream()
                .map(e -> ExpenseSamplingSpecification.from(e, e.getEffectiveAnnualExpenseVolatility())).toList());
    }

    public static void requireComparisonSupported(RetirementPlan current, RetirementPlan baseline) {
        requireStandaloneSupported(current);
        requireStandaloneSupported(baseline);
    }

    public static void requireComparisonSupported(MonteCarloStrategyComparisonRequest request) {
        Objects.requireNonNull(request).strategyA().validateExpenseConfiguration();
        request.strategyB().validateExpenseConfiguration();
    }

    public static void requireSupported(RetirementPlan plan) {
        if (Objects.requireNonNull(plan).getHousehold().getExpenses().stream()
                .anyMatch(expense -> expense.getEffectiveAnnualExpenseVolatility().signum() > 0)) {
            throw new IllegalArgumentException("Positive recurring-expense volatility requires supported generated Monte Carlo execution "
                    + "with validated expense provenance. This execution path does not support it; configured percentages were not changed.");
        }
    }
}
