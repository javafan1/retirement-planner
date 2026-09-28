package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.util.Objects;

/** Session-only parameters of the underlying normal distribution, before flooring. */
public record MonteCarloInflationSettings(
        BigDecimal expectedInflationRate,
        BigDecimal inflationVolatility,
        BigDecimal minimumInflationRate) {
    public MonteCarloInflationSettings {
        Objects.requireNonNull(expectedInflationRate, "Expected inflation is required.");
        Objects.requireNonNull(inflationVolatility, "Inflation volatility is required.");
        Objects.requireNonNull(minimumInflationRate, "Inflation floor is required.");
        if (minimumInflationRate.compareTo(BigDecimal.ONE.negate()) <= 0
                || expectedInflationRate.compareTo(minimumInflationRate) < 0) {
            throw new IllegalArgumentException("Inflation floor must exceed -100% and cannot exceed expected inflation.");
        }
        if (inflationVolatility.signum() < 0
                || !Double.isFinite(expectedInflationRate.doubleValue())
                || !Double.isFinite(inflationVolatility.doubleValue())
                || !Double.isFinite(minimumInflationRate.doubleValue())) {
            throw new IllegalArgumentException("Inflation parameters must be finite; volatility cannot be negative.");
        }
    }
}
