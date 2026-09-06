package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

/** Explicit constrained-search settings. */
public record SocialSecuritySurvivorOptimizationSettings(
        int topRetirementCandidates) {

    public static final int DEFAULT_TOP_RETIREMENT_CANDIDATES = 5;

    public SocialSecuritySurvivorOptimizationSettings {
        if (topRetirementCandidates < 1) {
            throw new IllegalArgumentException(
                    "Top retirement candidate count must be positive.");
        }
    }

    public static SocialSecuritySurvivorOptimizationSettings defaults() {
        return new SocialSecuritySurvivorOptimizationSettings(
                DEFAULT_TOP_RETIREMENT_CANDIDATES);
    }
}
