package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Exact elections for present people. Spouse and survivor elections are absent for a single person. */
public record SocialSecurityHouseholdClaimingStrategy(
        int primaryRetirementAge,
        Integer spouseRetirementAge,
        LocalDate primaryRetirementClaimDate,
        LocalDate spouseRetirementClaimDate,
        SocialSecuritySurvivorClaimingCandidate primarySurvivorElection,
        SocialSecuritySurvivorClaimingCandidate spouseSurvivorElection) {

    public SocialSecurityHouseholdClaimingStrategy {
        Objects.requireNonNull(primaryRetirementClaimDate);
        if (spouseRetirementAge == null) {
            if (primaryRetirementAge < 62 || primaryRetirementAge > 70) {
                throw new IllegalArgumentException("Primary retirement claiming age must be between 62 and 70.");
            }
            if (spouseRetirementClaimDate != null || primarySurvivorElection != null || spouseSurvivorElection != null) {
                throw new IllegalArgumentException("A single-person strategy has no spouse or survivor elections.");
            }
        } else {
            Objects.requireNonNull(spouseRetirementClaimDate);
            Objects.requireNonNull(primarySurvivorElection);
            Objects.requireNonNull(spouseSurvivorElection);
        }
    }

    public static SocialSecurityHouseholdClaimingStrategy primaryOnly(int age, LocalDate date) {
        return new SocialSecurityHouseholdClaimingStrategy(age, null, date, null, null, null);
    }

    public boolean hasSpouse() { return spouseRetirementAge != null; }

    public java.util.OptionalInt spouseRetirementAgeIfPresent() {
        return hasSpouse() ? java.util.OptionalInt.of(spouseRetirementAge) : java.util.OptionalInt.empty();
    }
}
