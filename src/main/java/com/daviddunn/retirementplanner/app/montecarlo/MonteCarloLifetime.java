package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Frozen membership and timing. An empty death belongs to a present, surviving life only. */
public sealed interface MonteCarloLifetime permits MonteCarloLifetime.Individual, MonteCarloLifetime.Couple {
    record Life(Optional<Year> deathYear) {
        public Life {
            Objects.requireNonNull(deathYear);
            if (deathYear.isPresent() && deathYear.orElseThrow().getValue() <= 0) {
                throw new IllegalArgumentException("Death year must be positive.");
            }
        }
        public boolean alive(int year) { return deathYear.map(death -> year < death.getValue()).orElse(true); }
    }

    record Individual(Life primary, HouseholdLifetimeScenario projectionTiming) implements MonteCarloLifetime {
        public Individual(Life primary) { this(primary, HouseholdLifetimeScenario.primaryOnly(primary.deathYear())); }
        public Individual {
            Objects.requireNonNull(primary);
            Objects.requireNonNull(projectionTiming);
            if (!projectionTiming.equals(HouseholdLifetimeScenario.primaryOnly(primary.deathYear())))
                throw new IllegalArgumentException("Individual projection timing must match its only life.");
        }
        @Override public List<Life> lives() { return List.of(primary); }

    }

    record Couple(Life primary, Life spouse, HouseholdLifetimeScenario projectionTiming) implements MonteCarloLifetime {
        public Couple(Life primary, Life spouse) { this(primary, spouse, new HouseholdLifetimeScenario(primary.deathYear(), spouse.deathYear())); }
        public Couple {
            Objects.requireNonNull(primary);
            Objects.requireNonNull(spouse);
            Objects.requireNonNull(projectionTiming);
            if (!projectionTiming.equals(new HouseholdLifetimeScenario(primary.deathYear(), spouse.deathYear())))
                throw new IllegalArgumentException("Couple projection timing must match both lives.");
        }
        @Override public List<Life> lives() { return List.of(primary, spouse); }

    }

    Life primary();
    List<Life> lives();
    HouseholdLifetimeScenario projectionTiming();
    default boolean hasSpouse() { return this instanceof Couple; }
    default boolean living(int year) { return lives().stream().anyMatch(life -> life.alive(year)); }
    default int terminalDeathYear() {
        return lives().stream().mapToInt(life -> life.deathYear().orElseThrow(() ->
                new IllegalArgumentException("Sampled mortality requires a death for every present person.")).getValue())
                .max().orElseThrow();
    }
    static Couple couple(HouseholdLifetimeScenario timing) {
        Objects.requireNonNull(timing);
        return new Couple(new Life(timing.primaryDeathYear()), new Life(timing.spouseDeathYear()), timing);
    }
    static Individual individual(Year death) {
        return new Individual(new Life(Optional.of(death)));
    }
}
