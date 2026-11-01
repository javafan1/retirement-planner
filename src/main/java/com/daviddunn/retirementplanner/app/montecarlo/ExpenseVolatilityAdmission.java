package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.Objects;

/** Temporary public-execution boundary until versioned enabled provenance is supported. */
public final class ExpenseVolatilityAdmission {
    private ExpenseVolatilityAdmission() { }

    public static void requireSupported(RetirementPlan plan) {
        if (Objects.requireNonNull(plan).getHousehold().getExpenses().stream()
                .anyMatch(expense -> expense.getEffectiveAnnualExpenseVolatility().signum() > 0)) {
            throw new IllegalArgumentException("Stochastic expense execution is not yet available. "
                    + "Set annual expense volatility to 0% for all expenses before running Monte Carlo analysis.");
        }
    }
}
