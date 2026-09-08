package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class HouseholdLifetimeScenarioTest {
    @Test
    void validatesYearsAndDerivesJanuaryFirstDates() {
        var scenario = new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.empty());
        assertEquals(Optional.of(LocalDate.of(2031, 1, 1)), scenario.primaryDeathDate());
        assertTrue(scenario.spouseDeathDate().isEmpty());
        assertThrows(NullPointerException.class, () -> new HouseholdLifetimeScenario(null, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new HouseholdLifetimeScenario(Optional.of(Year.of(0)), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new HouseholdLifetimeScenario(Optional.empty(), Optional.of(Year.of(-1))));
    }

    @Test
    void effectiveViewUsesPersistedTimingOnlyWhenOverrideIsAbsent() {
        var persisted = new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2031);
        var ordinary = EffectiveHouseholdDeathView.resolve(persisted, Optional.empty());
        assertTrue(ordinary.isAlive(AccountOwnership.PRIMARY, 2030));
        assertFalse(ordinary.isAlive(AccountOwnership.PRIMARY, 2031));
        assertTrue(ordinary.isAlive(AccountOwnership.SPOUSE, 2050));
        assertTrue(ordinary.isAlive(AccountOwnership.JOINT, 2050));
        var override = EffectiveHouseholdDeathView.resolve(persisted, Optional.of(HouseholdLifetimeScenario.bothSurvive()));
        assertTrue(override.isAlive(AccountOwnership.PRIMARY, 2050));
        assertFalse(override.hasAnyDeathOccurred(2050));
        var both = EffectiveHouseholdDeathView.resolve(persisted, Optional.of(
                new HouseholdLifetimeScenario(Optional.of(Year.of(2031)), Optional.of(Year.of(2033)))));
        assertEquals(Optional.of(Year.of(2031)), both.firstDeathYear());
        assertFalse(both.areBothDeceased(2032));
        assertTrue(both.areBothDeceased(2033));
        assertThrows(IllegalArgumentException.class, () -> both.deathDate(AccountOwnership.JOINT));
    }

    @Test
    void contextKeepsLegacyConstructorAndValidatesNewOptional() {
        assertEquals(ProjectionEvaluationContext.empty(), new ProjectionEvaluationContext(Optional.empty()));
        assertThrows(NullPointerException.class, () -> new ProjectionEvaluationContext(Optional.empty(), null));
        assertEquals(HouseholdLifetimeScenario.bothSurvive(), ProjectionEvaluationContext
                .withLifetimeScenario(HouseholdLifetimeScenario.bothSurvive()).householdLifetimeScenario().orElseThrow());
    }
}
