package com.daviddunn.retirementplanner.domain.income;

import java.time.LocalDate;
import java.util.Objects;

public final class SurvivorFullRetirementAgeCalculator {

    private SurvivorFullRetirementAgeCalculator() {
    }

    public static FullRetirementAge determine(
            LocalDate birthDate) {

        Objects.requireNonNull(
                birthDate,
                "Birth date is required.");

        int birthYear =
                birthDate.getYear();

        /*
         * Survivor FRA schedule:
         *
         * 1945-1956 -> 66
         * 1957      -> 66 + 2 months
         * 1958      -> 66 + 4 months
         * 1959      -> 66 + 6 months
         * 1960      -> 66 + 8 months
         * 1961      -> 66 + 10 months
         * 1962+     -> 67
         *
         * The current Retirement Planner MVP
         * supports the 66-67 survivor FRA range.
         */

        if (birthYear <= 1956) {

            return new FullRetirementAge(
                    66,
                    0);
        }

        return switch (birthYear) {

            case 1957 ->
                    new FullRetirementAge(
                            66,
                            2);

            case 1958 ->
                    new FullRetirementAge(
                            66,
                            4);

            case 1959 ->
                    new FullRetirementAge(
                            66,
                            6);

            case 1960 ->
                    new FullRetirementAge(
                            66,
                            8);

            case 1961 ->
                    new FullRetirementAge(
                            66,
                            10);

            default ->
                    new FullRetirementAge(
                            67,
                            0);
        };
    }
}