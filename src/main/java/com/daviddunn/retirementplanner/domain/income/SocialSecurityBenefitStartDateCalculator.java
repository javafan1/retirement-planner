package com.daviddunn.retirementplanner.domain.income;

import com.daviddunn.retirementplanner.domain.model.Person;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class SocialSecurityBenefitStartDateCalculator {

    private SocialSecurityBenefitStartDateCalculator() {
    }

    public static Optional<LocalDate> calculate(
            Person person,
            int claimingAge) {

        Objects.requireNonNull(
                person,
                "Person is required.");

        if (claimingAge < 62 || claimingAge > 70) {
            throw new IllegalArgumentException(
                    "Claiming age must be between 62 and 70.");
        }

        LocalDate birthDate = person.getBirthDate();

        if (birthDate == null) {
            return Optional.empty();
        }

        return Optional.of(
                birthDate.plusYears(claimingAge));
    }
}
