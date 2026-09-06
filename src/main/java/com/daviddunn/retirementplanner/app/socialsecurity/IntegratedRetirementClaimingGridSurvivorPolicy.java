package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;

import java.util.Objects;

/** Fixed exact survivor elections held constant across a retirement-age grid. */
public record IntegratedRetirementClaimingGridSurvivorPolicy(
        SocialSecuritySurvivorClaimingCandidate primaryElection,
        SocialSecuritySurvivorClaimingCandidate spouseElection,
        String description) {

    public IntegratedRetirementClaimingGridSurvivorPolicy {
        Objects.requireNonNull(primaryElection, "Primary survivor election is required.");
        Objects.requireNonNull(spouseElection, "Spouse survivor election is required.");
        Objects.requireNonNull(description, "Survivor policy description is required.");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Survivor policy description cannot be blank.");
        }
    }
}
