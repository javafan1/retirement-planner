package com.daviddunn.retirementplanner.domain.financial;

public enum GrowthCategory {

    GENERAL,

    HEALTHCARE,

    NONE;

    /** Presentation label; persisted enum names remain unchanged. */
    public String displayName() {
        return switch (this) {
            case GENERAL -> "General Inflation";
            case HEALTHCARE -> "Healthcare Inflation";
            case NONE -> "No Inflation";
        };
    }
}
