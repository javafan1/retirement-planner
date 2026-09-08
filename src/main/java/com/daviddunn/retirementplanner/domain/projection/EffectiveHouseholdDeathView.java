package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Immutable effective timing shared by all death-sensitive calculations in one run. */
public final class EffectiveHouseholdDeathView {
    private final HouseholdLifetimeScenario timing;

    private EffectiveHouseholdDeathView(HouseholdLifetimeScenario timing) {
        this.timing = timing;
    }

    public static EffectiveHouseholdDeathView resolve(
            DeathScenarioAssumptions persisted,
            Optional<HouseholdLifetimeScenario> scenario) {
        Objects.requireNonNull(persisted, "Persisted death assumptions are required.");
        Objects.requireNonNull(scenario, "Lifetime scenario optional is required.");
        return new EffectiveHouseholdDeathView(scenario.orElseGet(() ->
                new HouseholdLifetimeScenario(
                        persisted.getDeathScenario() == DeathScenario.PRIMARY_DIES
                                ? Optional.of(Year.of(persisted.getDeathYear())) : Optional.empty(),
                        persisted.getDeathScenario() == DeathScenario.SPOUSE_DIES
                                ? Optional.of(Year.of(persisted.getDeathYear())) : Optional.empty())));
    }

    public Optional<LocalDate> deathDate(AccountOwnership owner) {
        return switch (Objects.requireNonNull(owner, "Owner is required.")) {
            case PRIMARY -> timing.primaryDeathDate();
            case SPOUSE -> timing.spouseDeathDate();
            case JOINT -> throw new IllegalArgumentException("Joint ownership has no person death date.");
        };
    }

    public boolean isAlive(AccountOwnership owner, int year) {
        Objects.requireNonNull(owner, "Owner is required.");
        if (owner == AccountOwnership.JOINT) {
            return !areBothDeceased(year);
        }
        return deathDate(owner).map(date -> year < date.getYear()).orElse(true);
    }

    public Optional<Year> firstDeathYear() {
        return Stream.concat(timing.primaryDeathYear().stream(), timing.spouseDeathYear().stream())
                .min(Year::compareTo);
    }

    public boolean hasAnyDeathOccurred(int year) {
        return firstDeathYear().map(death -> year >= death.getValue()).orElse(false);
    }

    public boolean areBothDeceased(int year) {
        return !isAlive(AccountOwnership.PRIMARY, year)
                && !isAlive(AccountOwnership.SPOUSE, year);
    }
}
