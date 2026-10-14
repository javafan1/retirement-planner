package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

/** Observes the planner's base; omitted legal deductions are unknown, not verified zeros. */
public record FederalTaxBaseBreakdown(
        BigDecimal modeledAdjustedGrossIncome,
        BigDecimal modeledStandardDeduction,
        BigDecimal derivedRawTaxableIncome,
        BigDecimal modeledClampedTaxableIncome,
        Coverage coverage) {

    public enum Coverage {
        MODELED_STANDARD_DEDUCTION_ONLY_V1
    }

    public FederalTaxBaseBreakdown {
        requireNonnegative(modeledAdjustedGrossIncome);
        requireNonnegative(modeledStandardDeduction);
        requireNonnegative(modeledClampedTaxableIncome);
        Objects.requireNonNull(derivedRawTaxableIncome);
        Objects.requireNonNull(coverage);
        if (derivedRawTaxableIncome.compareTo(
                modeledAdjustedGrossIncome.subtract(modeledStandardDeduction)) != 0
                || modeledClampedTaxableIncome.compareTo(
                derivedRawTaxableIncome.max(BigDecimal.ZERO)) != 0) {
            throw new IllegalArgumentException("Federal result does not match the modeled standard-deduction base.");
        }
    }

    public static FederalTaxBaseBreakdown fromModeledResult(FederalTaxCalculation result) {
        Objects.requireNonNull(result);
        return new FederalTaxBaseBreakdown(
                result.getAdjustedGrossIncome(),
                result.getStandardDeduction(),
                result.getAdjustedGrossIncome().subtract(result.getStandardDeduction()),
                result.getTaxableIncome(),
                Coverage.MODELED_STANDARD_DEDUCTION_ONLY_V1);
    }

    private static void requireNonnegative(BigDecimal amount) {
        if (Objects.requireNonNull(amount).signum() < 0) {
            throw new IllegalArgumentException("Modeled federal amount cannot be negative.");
        }
    }
}
