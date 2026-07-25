package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.time.LocalDate;
import java.util.Objects;

public final class RmdEligibilityCalculator {

    public boolean isRmdRequired(
            LocalDate dateOfBirth,
            int projectionYear,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                dateOfBirth,
                "Date of birth is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (projectionYear < dateOfBirth.getYear()) {
            throw new IllegalArgumentException(
                    "Projection year cannot be before birth year.");
        }

        int rmdStartingAge =
                governmentRules
                        .getRmdRules()
                        .getRmdStartingAge(
                                dateOfBirth.getYear());

        int ageAtEndOfYear =
                projectionYear -
                        dateOfBirth.getYear();

        return ageAtEndOfYear >=
                rmdStartingAge;
    }
}