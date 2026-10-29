package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Detached sampling inputs only; not a persisted Expense extension or financial fingerprint.
 * Decimal normalization is solely for exact sampling correspondence. Original plan values
 * and their scale-sensitive financial identities are never modified.
 */
public record ExpenseSamplingSpecification(
        ExpenseType expenseType,
        BigDecimal annualAmount,
        LocalDate startDate,
        LocalDate endDate,
        GrowthCategory escalationPolicy,
        BigDecimal annualVolatility) {

    public ExpenseSamplingSpecification {
        Objects.requireNonNull(expenseType, "Expense type is required.");
        Objects.requireNonNull(escalationPolicy, "Escalation policy is required.");
        annualAmount = normalize(Objects.requireNonNull(annualAmount, "Annual amount is required."));
        annualVolatility = normalize(annualVolatility == null ? BigDecimal.ZERO : annualVolatility);
        if (annualVolatility.signum() < 0 || annualVolatility.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Annual relative expense standard deviation must be between 0 and 1.");
        }
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Expense end date cannot precede start date.");
        }
        if (annualVolatility.signum() != 0
                && (expenseType != ExpenseType.RECURRING || annualAmount.signum() < 0)) {
            throw new IllegalArgumentException("Enabled expense sampling requires a nonnegative recurring expense.");
        }
    }

    public static ExpenseSamplingSpecification from(Expense expense, BigDecimal annualVolatility) {
        Objects.requireNonNull(expense, "Expense is required.");
        return new ExpenseSamplingSpecification(expense.getExpenseType(), expense.getAnnualAmount(),
                expense.getStartDate(), expense.getEndDate(), expense.getGrowthCategory(), annualVolatility);
    }

    public boolean enabled() {
        return annualVolatility.signum() != 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value.precision() > 4096) {
            throw new IllegalArgumentException("Sampling decimal exceeds the 4096-digit bound.");
        }
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
