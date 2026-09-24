package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;

import java.util.Objects;

/**
 * Immutable exogenous outcomes, reusable across financial strategies.
 * Each sampled world has empirical weight 1 / simulationCount. Its original
 * mortality probability must never be applied again to its financial outcome.
 */
public record MonteCarloWorld(
        int scenarioIndex,
        ProjectionEconomicPath economicPath,
        HouseholdLifetimeScenario lifetimeScenario) {

    public MonteCarloWorld {
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        Objects.requireNonNull(economicPath, "Economic path is required.");
        Objects.requireNonNull(lifetimeScenario, "Lifetime scenario is required.");
        if (lifetimeScenario.primaryDeathYear().isEmpty()
                || lifetimeScenario.spouseDeathYear().isEmpty()) {
            throw new IllegalArgumentException("Mortality worlds require both household death years.");
        }
    }
}
