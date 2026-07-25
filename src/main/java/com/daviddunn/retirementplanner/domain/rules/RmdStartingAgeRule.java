package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class RmdStartingAgeRule {

    private final int minimumBirthYear;
    private final Integer maximumBirthYear;
    private final int rmdStartingAge;

    @JsonCreator
    public RmdStartingAgeRule(

            @JsonProperty("minimumBirthYear")
            int minimumBirthYear,

            @JsonProperty("maximumBirthYear")
            Integer maximumBirthYear,

            @JsonProperty("rmdStartingAge")
            int rmdStartingAge) {

        if (minimumBirthYear <= 0) {
            throw new IllegalArgumentException(
                    "Minimum birth year must be greater than zero.");
        }

        if (maximumBirthYear != null &&
                maximumBirthYear < minimumBirthYear) {

            throw new IllegalArgumentException(
                    "Maximum birth year cannot be less than minimum birth year.");
        }

        if (rmdStartingAge <= 0) {
            throw new IllegalArgumentException(
                    "RMD starting age must be greater than zero.");
        }

        this.minimumBirthYear =
                minimumBirthYear;

        this.maximumBirthYear =
                maximumBirthYear;

        this.rmdStartingAge =
                rmdStartingAge;
    }

    public int getMinimumBirthYear() {
        return minimumBirthYear;
    }

    public Integer getMaximumBirthYear() {
        return maximumBirthYear;
    }

    public int getRmdStartingAge() {
        return rmdStartingAge;
    }

    public boolean appliesTo(
            int birthYear) {

        if (birthYear < minimumBirthYear) {
            return false;
        }

        return maximumBirthYear == null ||
                birthYear <= maximumBirthYear;
    }

    @Override
    public String toString() {

        return "RmdStartingAgeRule{" +
                "minimumBirthYear=" +
                minimumBirthYear +
                ", maximumBirthYear=" +
                maximumBirthYear +
                ", rmdStartingAge=" +
                rmdStartingAge +
                '}';
    }
}