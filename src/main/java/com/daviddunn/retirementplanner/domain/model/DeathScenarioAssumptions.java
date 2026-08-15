package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class DeathScenarioAssumptions {

    private final DeathScenario deathScenario;
    private final Integer deathYear;
    private final Integer survivorClaimingAge;

    @JsonCreator
    public DeathScenarioAssumptions(

            @JsonProperty("deathScenario")
            DeathScenario deathScenario,

            @JsonProperty("deathYear")
            Integer deathYear,

            @JsonProperty("survivorClaimingAge")
            Integer survivorClaimingAge) {

        this.deathScenario =
                Objects.requireNonNull(
                        deathScenario,
                        "Death scenario is required.");

        this.deathYear = deathYear;

        this.survivorClaimingAge =
                survivorClaimingAge;

        if (deathScenario != DeathScenario.BOTH_SURVIVE
                && deathYear == null) {

            throw new IllegalArgumentException(
                    "Death year is required for a death scenario.");
        }

        if (deathYear != null && deathYear <= 0) {

            throw new IllegalArgumentException(
                    "Death year must be greater than zero.");
        }

        if (deathScenario != DeathScenario.BOTH_SURVIVE
                && survivorClaimingAge == null) {

            throw new IllegalArgumentException(
                    "Survivor claiming age is required "
                            + "for a death scenario.");
        }

        if (survivorClaimingAge != null
                && (survivorClaimingAge < 62
                || survivorClaimingAge > 70)) {

            throw new IllegalArgumentException(
                    "Survivor claiming age must be "
                            + "between 62 and 70.");
        }
    }

    /*
     * Compatibility constructor for existing code/tests
     * that do not yet specify a survivor claiming age.
     *
     * The default is age 67.
     */
    public DeathScenarioAssumptions(
            DeathScenario deathScenario,
            Integer deathYear) {

        this(
                deathScenario,
                deathYear,
                deathScenario == DeathScenario.BOTH_SURVIVE
                        ? null
                        : 67);
    }

    public DeathScenario getDeathScenario() {
        return deathScenario;
    }

    public Integer getDeathYear() {
        return deathYear;
    }

    public Integer getSurvivorClaimingAge() {
        return survivorClaimingAge;
    }

    public boolean isDeathScenarioActive(
            int calendarYear) {

        return deathScenario != DeathScenario.BOTH_SURVIVE
                && calendarYear >= deathYear;
    }

    public boolean isIncomeActive(
            AccountOwnership ownership,
            int calendarYear) {

        Objects.requireNonNull(
                ownership,
                "Account ownership is required.");

        /*
         * Before the death year, everyone is alive.
         */
        if (!isDeathScenarioActive(calendarYear)) {
            return true;
        }

        return switch (deathScenario) {

            case BOTH_SURVIVE ->
                    true;

            case PRIMARY_DIES ->
                    ownership != AccountOwnership.PRIMARY;

            case SPOUSE_DIES ->
                    ownership != AccountOwnership.SPOUSE;
        };
    }

    @Override
    public String toString() {

        return "DeathScenarioAssumptions{" +
                "deathScenario=" +
                deathScenario +
                ", deathYear=" +
                deathYear +
                ", survivorClaimingAge=" +
                survivorClaimingAge +
                '}';
    }
}