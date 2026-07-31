package com.daviddunn.retirementplanner.domain.income;

import java.time.LocalDate;
import java.util.Objects;

public final class FullRetirementAgeCalculator {

    private FullRetirementAgeCalculator() {
    }

    public static FullRetirementAge determine(
            LocalDate birthDate) {

        Objects.requireNonNull(
                birthDate,
                "Birth date is required.");

        int year = birthDate.getYear();

        if (year <= 1954) {
            return new FullRetirementAge(66, 0);
        }

        return switch (year) {

            case 1955 ->
                    new FullRetirementAge(66, 2);

            case 1956 ->
                    new FullRetirementAge(66, 4);

            case 1957 ->
                    new FullRetirementAge(66, 6);

            case 1958 ->
                    new FullRetirementAge(66, 8);

            case 1959 ->
                    new FullRetirementAge(66, 10);

            default ->
                    new FullRetirementAge(67, 0);
        };
    }
}