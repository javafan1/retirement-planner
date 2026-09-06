package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Exact retirement and independent survivor elections for both spouses. */
public record SocialSecurityHouseholdClaimingStrategy(
        int primaryRetirementAge,
        int spouseRetirementAge,
        LocalDate primaryRetirementClaimDate,
        LocalDate spouseRetirementClaimDate,
        SocialSecuritySurvivorClaimingCandidate primarySurvivorElection,
        SocialSecuritySurvivorClaimingCandidate spouseSurvivorElection) {

    public SocialSecurityHouseholdClaimingStrategy {
        Objects.requireNonNull(primaryRetirementClaimDate);
        Objects.requireNonNull(spouseRetirementClaimDate);
        Objects.requireNonNull(primarySurvivorElection);
        Objects.requireNonNull(spouseSurvivorElection);
    }
}
