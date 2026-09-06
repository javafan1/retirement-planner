package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.OptionalInt;

/** One exact, auditable survivor filing candidate. */
public record SocialSecuritySurvivorClaimingCandidate(
        LocalDate claimDate,
        int ageYears,
        int ageMonths,
        String label) {

    public SocialSecuritySurvivorClaimingCandidate {
        Objects.requireNonNull(claimDate, "Survivor claim date is required.");
        Objects.requireNonNull(label, "Survivor candidate label is required.");
        if (ageYears < 60 || ageMonths < 0 || ageMonths > 11) {
            throw new IllegalArgumentException("Survivor candidate age is invalid.");
        }
    }

    public OptionalInt wholeYearAge() {
        return ageMonths == 0 ? OptionalInt.of(ageYears) : OptionalInt.empty();
    }

    static SocialSecuritySurvivorClaimingCandidate from(
            LocalDate effectiveBirthDate,
            LocalDate claimDate) {
        Period age = Period.between(effectiveBirthDate, claimDate);
        String label = age.getMonths() == 0
                ? "Age " + age.getYears()
                : "Age " + age.getYears() + " years " + age.getMonths() + " months";
        return new SocialSecuritySurvivorClaimingCandidate(
                claimDate, age.getYears(), age.getMonths(), label);
    }
}
