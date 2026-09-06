package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.Objects;

/** Chronological birthday-based conversion for deterministic longevity scenarios. */
public final class SocialSecurityDeathDateCalculator {

    public static final int MINIMUM_DEATH_AGE = 1;
    public static final int MAXIMUM_DEATH_AGE = 120;

    private SocialSecurityDeathDateCalculator() {
    }

    public static LocalDate calculateDeathDate(
            LocalDate birthDate,
            int deathAge) {

        Objects.requireNonNull(birthDate, "Birth date is required.");
        validateDeathAge(deathAge, "Death age");
        return birthDate.plusYears(deathAge);
    }

    public static void validateDeathAge(
            int deathAge,
            String description) {

        if (deathAge < MINIMUM_DEATH_AGE
                || deathAge > MAXIMUM_DEATH_AGE) {
            throw new IllegalArgumentException(
                    description
                            + " must be between "
                            + MINIMUM_DEATH_AGE
                            + " and "
                            + MAXIMUM_DEATH_AGE
                            + ".");
        }
    }
}
