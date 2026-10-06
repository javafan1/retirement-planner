package com.daviddunn.retirementplanner.domain.model;

import java.time.LocalDate;

/** Required completed-editor inputs; draft and legacy Person data remain loadable. */
public final class PersonInformationValidation {
    private PersonInformationValidation() { }

    public static void validate(LocalDate birthDate, MortalityCategory category) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Birth date is required.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Mortality category is required.");
        }
    }
}
