package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.Objects;

/** Immutable inputs for the constrained two-stage survivor search. */
public record SocialSecuritySurvivorClaimingOptimizationRequest(
        SocialSecurityMortalityWeightedClaimingGridRequest retirementGridRequest,
        SocialSecuritySurvivorOptimizationSettings settings) {

    public SocialSecuritySurvivorClaimingOptimizationRequest {
        Objects.requireNonNull(retirementGridRequest, "Retirement grid request is required.");
        Objects.requireNonNull(settings, "Survivor optimization settings are required.");
    }

    public SocialSecuritySurvivorClaimingOptimizationRequest(
            SocialSecurityMortalityWeightedClaimingGridRequest retirementGridRequest) {
        this(retirementGridRequest, SocialSecuritySurvivorOptimizationSettings.defaults());
    }
}
