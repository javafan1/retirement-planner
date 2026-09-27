package com.daviddunn.retirementplanner.ui.montecarlo;

public enum MonteCarloMode {
    FIXED_LIFESPAN("Fixed Lifespan"),
    LONGEVITY_ADJUSTED("Longevity-Adjusted");

    private final String label;

    MonteCarloMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
