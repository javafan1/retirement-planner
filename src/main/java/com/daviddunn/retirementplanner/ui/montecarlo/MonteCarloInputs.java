package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;

import java.math.BigDecimal;

/**
 * Session-only percentages; validation never clamps or changes the retirement plan.
 */
public record MonteCarloInputs(int simulations, String expectedReturn, String volatility, String seed) {
    public static final int MAX_SIMULATIONS = 10_000;

    public MonteCarloSettings settings() {
        if (simulations < 1 || simulations > MAX_SIMULATIONS) {
            throw new IllegalArgumentException("Simulations must be between 1 and 10,000.");
        }
        BigDecimal mean = percentage(expectedReturn, "Expected return", new BigDecimal("-99"), new BigDecimal("100"));
        BigDecimal deviation = percentage(volatility, "Volatility", BigDecimal.ZERO, new BigDecimal("100"));
        long parsedSeed;
        try {
            parsedSeed = Long.parseLong(seed.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Seed must be a whole number in the signed 64-bit range.");
        }
        return new MonteCarloSettings(simulations, parsedSeed, mean, deviation);
    }

    private static BigDecimal percentage(String text, String field, BigDecimal minimum, BigDecimal maximum) {
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a finite percentage.");
        }
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " must be between " + minimum + "% and " + maximum + "%.");
        }
        return value.movePointLeft(2);
    }
}
