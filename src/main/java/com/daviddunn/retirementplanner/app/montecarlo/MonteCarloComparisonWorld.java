package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionInflationPath;

import java.util.Objects;
import java.util.Optional;

/** Shared exogenous inputs. Unlike mortality-only MonteCarloWorld, permits fixed survival horizons. */
public record MonteCarloComparisonWorld(
        int scenarioIndex,
        ProjectionEconomicPath economicPath,
        HouseholdLifetimeScenario lifetimeScenario,
        Optional<ProjectionInflationPath> inflationPath) {

    public MonteCarloComparisonWorld {
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        Objects.requireNonNull(economicPath);
        Objects.requireNonNull(lifetimeScenario);
        Objects.requireNonNull(inflationPath);
    }

    /** Retains the exact path/scenario objects; does not regenerate or copy draws. */
    public static MonteCarloComparisonWorld from(MonteCarloWorld world) {
        Objects.requireNonNull(world);
        return new MonteCarloComparisonWorld(world.scenarioIndex(), world.economicPath(),
                world.lifetimeScenario(), world.inflationPath());
    }
}
