package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEconomicPath;
import com.daviddunn.retirementplanner.domain.projection.ProjectionInflationPath;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable exogenous outcomes, reusable across financial strategies.
 * Each sampled world has empirical weight 1 / simulationCount. Its original
 * mortality probability must never be applied again to its financial outcome.
 */
public record MonteCarloWorld(
        int scenarioIndex,
        ProjectionEconomicPath economicPath,
        MonteCarloLifetime lifetime,
        Optional<ProjectionInflationPath> inflationPath) {

    public MonteCarloWorld(int scenarioIndex, ProjectionEconomicPath economicPath,
            HouseholdLifetimeScenario lifetimeScenario) {
        this(scenarioIndex, economicPath, MonteCarloLifetime.couple(lifetimeScenario), Optional.empty());
    }

    public MonteCarloWorld(int scenarioIndex, ProjectionEconomicPath economicPath,
            HouseholdLifetimeScenario timing, Optional<ProjectionInflationPath> inflationPath) {
        this(scenarioIndex, economicPath, MonteCarloLifetime.couple(timing), inflationPath);
    }

    public HouseholdLifetimeScenario lifetimeScenario() { return lifetime.projectionTiming(); }

    public MonteCarloWorld {
        Objects.requireNonNull(inflationPath, "Inflation path optional is required.");
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        Objects.requireNonNull(economicPath, "Economic path is required.");
        Objects.requireNonNull(lifetime, "Lifetime is required.");
        lifetime.terminalDeathYear();
    }
}