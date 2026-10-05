package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.Household;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Immutable effective timing shared by all death-sensitive calculations in one run. */
public final class EffectiveHouseholdDeathView {
    public enum PersonState { ABSENT, ALIVE, DECEASED }

    private final HouseholdLifetimeScenario timing;
    private final boolean hasSpouse;

    private EffectiveHouseholdDeathView(HouseholdLifetimeScenario timing, boolean hasSpouse) {
        this.timing = timing;
        this.hasSpouse = hasSpouse;
        if (!hasSpouse && timing.spouseDeathYear().isPresent()) {
            throw new IllegalArgumentException("An absent spouse cannot have a death date.");
        }
    }

    public static EffectiveHouseholdDeathView resolve(
            Household household,
            DeathScenarioAssumptions persisted,
            Optional<HouseholdLifetimeScenario> scenario) {
        Objects.requireNonNull(household, "Household membership is required.");
        Objects.requireNonNull(persisted, "Persisted death assumptions are required.");
        Objects.requireNonNull(scenario, "Lifetime scenario optional is required.");
        return new EffectiveHouseholdDeathView(scenario.orElseGet(() ->
                new HouseholdLifetimeScenario(
                        persisted.getDeathScenario() == DeathScenario.PRIMARY_DIES
                                ? Optional.of(Year.of(persisted.getDeathYear())) : Optional.empty(),
                        persisted.getDeathScenario() == DeathScenario.SPOUSE_DIES
                                ? Optional.of(Year.of(persisted.getDeathYear())) : Optional.empty())), household.hasSpouse());
    }

    public PersonState state(AccountOwnership owner, int year) {
        Objects.requireNonNull(owner, "Owner is required.");
        if (owner == AccountOwnership.JOINT) {
            throw new IllegalArgumentException("Joint ownership is not a person.");
        }
        if (owner == AccountOwnership.SPOUSE && !hasSpouse) {
            return PersonState.ABSENT;
        }
        return deathDate(owner).map(date -> year >= date.getYear()).orElse(false)
                ? PersonState.DECEASED : PersonState.ALIVE;
    }

    public Optional<LocalDate> deathDate(AccountOwnership owner) {
        return switch (Objects.requireNonNull(owner, "Owner is required.")) {
            case PRIMARY -> timing.primaryDeathDate();
            case SPOUSE -> {
                if (!hasSpouse) {
                    throw new IllegalArgumentException("An absent spouse has no death-date state; query state() first.");
                }
                yield timing.spouseDeathDate();
            }
            case JOINT -> throw new IllegalArgumentException("Joint ownership has no person death date.");
        };
    }

    public boolean isAlive(AccountOwnership owner, int year) {
        Objects.requireNonNull(owner, "Owner is required.");
        if (owner == AccountOwnership.JOINT) {
            return !isHouseholdDeceased(year);
        }
        return state(owner, year) == PersonState.ALIVE;
    }

    public Optional<Year> firstDeathYear() {
        return Stream.concat(timing.primaryDeathYear().stream(), timing.spouseDeathYear().stream())
                .min(Year::compareTo);
    }

    public boolean hasAnyDeathOccurred(int year) {
        return firstDeathYear().map(death -> year >= death.getValue()).orElse(false);
    }

    public boolean areBothDeceased(int year) {
        if (!hasSpouse) {
            throw new UnsupportedOperationException("Single-person household not yet supported by areBothDeceased; use isHouseholdDeceased.");
        }
        return isHouseholdDeceased(year);
    }

    public boolean isHouseholdDeceased(int year) {
        return !isAlive(AccountOwnership.PRIMARY, year)
                && !isAlive(AccountOwnership.SPOUSE, year);
    }
}
