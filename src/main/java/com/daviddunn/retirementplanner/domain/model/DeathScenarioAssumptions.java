package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class DeathScenarioAssumptions {

    private static final BigDecimal DEFAULT_POST_DEATH_EXPENSE_FACTOR =
            BigDecimal.ONE;

    private final DeathScenario deathScenario;
    private final Integer deathYear;
    private final Integer survivorClaimingAge;
    private final BigDecimal postDeathExpenseFactor;

    @JsonCreator
    public DeathScenarioAssumptions(

            @JsonProperty("deathScenario")
            DeathScenario deathScenario,

            @JsonProperty("deathYear")
            Integer deathYear,

            @JsonProperty("survivorClaimingAge")
            Integer survivorClaimingAge,

            @JsonProperty("postDeathExpenseFactor")
            BigDecimal postDeathExpenseFactor) {

        this.deathScenario =
                Objects.requireNonNull(
                        deathScenario,
                        "Death scenario is required.");

        this.deathYear =
                deathYear;

        this.survivorClaimingAge =
                survivorClaimingAge;

        /*
         * Older saved plans will not contain
         * postDeathExpenseFactor.
         *
         * Default to 100% so existing plans
         * retain their current behavior.
         */
        this.postDeathExpenseFactor =
                postDeathExpenseFactor != null
                        ? postDeathExpenseFactor
                        : DEFAULT_POST_DEATH_EXPENSE_FACTOR;

        if (deathScenario != DeathScenario.BOTH_SURVIVE
                && deathYear == null) {

            throw new IllegalArgumentException(
                    "Death year is required for a death scenario.");
        }

        if (deathYear != null
                && deathYear <= 0) {

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

        if (this.postDeathExpenseFactor.compareTo(
                BigDecimal.ZERO) < 0
                || this.postDeathExpenseFactor.compareTo(
                BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Post-death expense factor must "
                            + "be between 0 and 1.");
        }
    }

    /*
     * Compatibility constructor for existing code/tests
     * that do not specify survivor claiming age or
     * post-death expense factor.
     *
     * Default survivor claiming age = 67.
     * Default post-death expense factor = 100%.
     */
    public DeathScenarioAssumptions(
            DeathScenario deathScenario,
            Integer deathYear) {

        this(
                deathScenario,
                deathYear,
                deathScenario == DeathScenario.BOTH_SURVIVE
                        ? null
                        : 67,
                DEFAULT_POST_DEATH_EXPENSE_FACTOR);
    }

    /*
     * Compatibility constructor for existing code/tests
     * that specify survivor claiming age but not the
     * post-death expense factor.
     */
    public DeathScenarioAssumptions(
            DeathScenario deathScenario,
            Integer deathYear,
            Integer survivorClaimingAge) {

        this(
                deathScenario,
                deathYear,
                survivorClaimingAge,
                DEFAULT_POST_DEATH_EXPENSE_FACTOR);
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

    public BigDecimal getPostDeathExpenseFactor() {
        return postDeathExpenseFactor;
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
                ", postDeathExpenseFactor=" +
                postDeathExpenseFactor +
                '}';
    }
}