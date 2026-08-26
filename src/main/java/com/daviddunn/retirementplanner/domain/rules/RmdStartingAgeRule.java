package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public final class RmdStartingAgeRule {

    private final Integer minimumBirthYear;
    private final Integer maximumBirthYear;
    private final LocalDate minimumBirthDate;
    private final LocalDate maximumBirthDate;
    private final int rmdStartingAge;

    @JsonCreator
    public RmdStartingAgeRule(
            @JsonProperty("minimumBirthYear")
            Integer minimumBirthYear,
            @JsonProperty("maximumBirthYear")
            Integer maximumBirthYear,
            @JsonProperty("minimumBirthDate")
            LocalDate minimumBirthDate,
            @JsonProperty("maximumBirthDate")
            LocalDate maximumBirthDate,
            @JsonProperty("rmdStartingAge")
            int rmdStartingAge) {

        boolean usesBirthYearRange =
                minimumBirthYear != null ||
                        maximumBirthYear != null;

        boolean usesBirthDateRange =
                minimumBirthDate != null ||
                        maximumBirthDate != null;

        if (usesBirthYearRange == usesBirthDateRange) {
            throw new IllegalArgumentException(
                    "An RMD starting age rule must use either a birth-year or birth-date range.");
        }

        if (usesBirthYearRange &&
                (minimumBirthYear == null ||
                        minimumBirthYear <= 0)) {
            throw new IllegalArgumentException(
                    "Birth-year rules require a positive minimum birth year.");
        }

        if (maximumBirthYear != null &&
                maximumBirthYear < minimumBirthYear) {
            throw new IllegalArgumentException(
                    "Maximum birth year cannot be less than minimum birth year.");
        }

        if (minimumBirthDate != null &&
                maximumBirthDate != null &&
                maximumBirthDate.isBefore(minimumBirthDate)) {
            throw new IllegalArgumentException(
                    "Maximum birth date cannot be before minimum birth date.");
        }

        if (rmdStartingAge <= 0) {
            throw new IllegalArgumentException(
                    "RMD starting age must be greater than zero.");
        }

        this.minimumBirthYear = minimumBirthYear;
        this.maximumBirthYear = maximumBirthYear;
        this.minimumBirthDate = minimumBirthDate;
        this.maximumBirthDate = maximumBirthDate;
        this.rmdStartingAge = rmdStartingAge;
    }

    public RmdStartingAgeRule(
            int minimumBirthYear,
            Integer maximumBirthYear,
            int rmdStartingAge) {

        this(
                minimumBirthYear,
                maximumBirthYear,
                null,
                null,
                rmdStartingAge);
    }

    public Integer getMinimumBirthYear() {
        return minimumBirthYear;
    }

    public Integer getMaximumBirthYear() {
        return maximumBirthYear;
    }

    public LocalDate getMinimumBirthDate() {
        return minimumBirthDate;
    }

    public LocalDate getMaximumBirthDate() {
        return maximumBirthDate;
    }

    public int getRmdStartingAge() {
        return rmdStartingAge;
    }

    public boolean appliesTo(
            int birthYear) {

        if (minimumBirthYear == null) {
            return false;
        }

        if (birthYear < minimumBirthYear) {
            return false;
        }

        return maximumBirthYear == null ||
                birthYear <= maximumBirthYear;
    }

    public boolean appliesTo(
            LocalDate birthDate) {

        if (minimumBirthDate == null &&
                maximumBirthDate == null) {
            return appliesTo(
                    birthDate.getYear());
        }

        if (minimumBirthDate != null &&
                birthDate.isBefore(minimumBirthDate)) {
            return false;
        }

        return maximumBirthDate == null ||
                !birthDate.isAfter(maximumBirthDate);
    }

    @Override
    public String toString() {

        return "RmdStartingAgeRule{" +
                "minimumBirthYear=" + minimumBirthYear +
                ", maximumBirthYear=" + maximumBirthYear +
                ", minimumBirthDate=" + minimumBirthDate +
                ", maximumBirthDate=" + maximumBirthDate +
                ", rmdStartingAge=" + rmdStartingAge +
                '}';
    }
}
