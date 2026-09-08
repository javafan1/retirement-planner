package com.daviddunn.retirementplanner.domain.projection;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;

/** Run-only annual death timing. Empty years explicitly mean survival through the horizon. */
public record HouseholdLifetimeScenario(
        Optional<Year> primaryDeathYear,
        Optional<Year> spouseDeathYear) {

    public HouseholdLifetimeScenario {
        validate(primaryDeathYear);
        validate(spouseDeathYear);
    }

    private static void validate(Optional<Year> year) {
        Objects.requireNonNull(year, "Death year optional is required.");
        if (year.isPresent() && year.get().getValue() <= 0) {
            throw new IllegalArgumentException("Death year must be positive.");
        }
    }

    public static HouseholdLifetimeScenario bothSurvive() {
        return new HouseholdLifetimeScenario(Optional.empty(), Optional.empty());
    }

    public Optional<LocalDate> primaryDeathDate() {
        return primaryDeathYear.map(year -> year.atDay(1));
    }

    public Optional<LocalDate> spouseDeathDate() {
        return spouseDeathYear.map(year -> year.atDay(1));
    }
}
