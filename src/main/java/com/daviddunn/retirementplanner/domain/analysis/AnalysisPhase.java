package com.daviddunn.retirementplanner.domain.analysis;

/** Stable phases shared by headless analysis services and UI adapters. */
public enum AnalysisPhase {
    SOCIAL_SECURITY_RETIREMENT_GRID("Stage 1 of 2: Retirement claiming grid"),
    SOCIAL_SECURITY_SURVIVOR_STRATEGIES("Stage 2 of 2: Survivor strategy evaluation"),
    CURRENT_PLAN_BASELINE("Evaluating current-plan baseline"),
    QUICK_COMPARISON_CANDIDATES("Evaluating integrated candidates"),
    EXHAUSTIVE_INTEGRATED_STRATEGIES("Evaluating integrated strategies");

    private final String displayName;

    AnalysisPhase(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
