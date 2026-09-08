package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityJointMortalityScenario;

import java.time.Year;
import java.util.Objects;
import java.util.Optional;

/** Coarsens birthday outcomes to annual full-plan timing without changing mortality probabilities. */
public final class HouseholdLifetimeScenarioMapper {
    public HouseholdLifetimeScenario map(SocialSecurityJointMortalityScenario scenario) {
        Objects.requireNonNull(scenario, "Mortality scenario is required.");
        return new HouseholdLifetimeScenario(
                Optional.of(Year.from(scenario.primaryDeathDate())),
                Optional.of(Year.from(scenario.spouseDeathDate())));
    }
}
