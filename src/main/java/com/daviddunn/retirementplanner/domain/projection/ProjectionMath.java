package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;

public final class ProjectionMath {

    private ProjectionMath() {
    }

    /**
     * Returns the compound growth multiplier for
     * an annual rate over the specified number of years.
     *
     * Example:
     *
     * 5% for 10 years = 1.628895...
     */
    public static BigDecimal calculateGrowthMultiplier(
            BigDecimal annualRate,
            int years) {

        if (annualRate == null) {
            throw new IllegalArgumentException(
                    "Annual rate is required.");
        }

        if (years < 0) {
            throw new IllegalArgumentException(
                    "Years cannot be negative.");
        }

        return BigDecimal.ONE
                .add(annualRate)
                .pow(years);
    }

    public static BigDecimal applyGrowth(
            BigDecimal amount,
            BigDecimal annualRate,
            int years) {

        return amount.multiply(
                calculateGrowthMultiplier(
                        annualRate,
                        years));
    }
}