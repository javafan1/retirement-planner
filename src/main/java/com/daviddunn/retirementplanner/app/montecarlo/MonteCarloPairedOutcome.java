package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Scenario index identifies the world within the result's captured request/source. No paths retained. */
public record MonteCarloPairedOutcome(
        int scenarioIndex,
        HouseholdLifetimeScenario lifetimeScenario,
        MonteCarloStrategyOutcome outcomeA,
        MonteCarloStrategyOutcome outcomeB) {

    public MonteCarloPairedOutcome {
        if (scenarioIndex < 0) {
            throw new IllegalArgumentException("Scenario index cannot be negative.");
        }
        Objects.requireNonNull(lifetimeScenario);
        Objects.requireNonNull(outcomeA);
        Objects.requireNonNull(outcomeB);
        if (outcomeA.terminal().isPresent() && outcomeB.terminal().isPresent()
                && !outcomeA.terminal().orElseThrow().balanceDate()
                .equals(outcomeB.terminal().orElseThrow().balanceDate())) {
            throw new IllegalArgumentException("Completed paired terminal dates must match.");
        }
    }

    public enum Status {
        BOTH_COMPLETED, A_COMPLETED_B_FAILED, A_FAILED_B_COMPLETED, BOTH_FAILED
    }

    public Status status() {
        return outcomeA.terminal().isPresent()
                ? (outcomeB.terminal().isPresent() ? Status.BOTH_COMPLETED : Status.A_COMPLETED_B_FAILED)
                : (outcomeB.terminal().isPresent() ? Status.A_FAILED_B_COMPLETED : Status.BOTH_FAILED);
    }

    /** Nominal A minus B, present only when both authoritative terminal values exist. */
    public Optional<Deltas> deltas() {
        if (status() != Status.BOTH_COMPLETED) {
            return Optional.empty();
        }
        var a = outcomeA.terminal().orElseThrow();
        var b = outcomeB.terminal().orElseThrow();
        return Optional.of(new Deltas(a.endingInvestableAssets().subtract(b.endingInvestableAssets()),
                a.endingNetWorth().subtract(b.endingNetWorth()), a.afterTaxEstate().subtract(b.afterTaxEstate()),
                a.lifetimeTaxes().subtract(b.lifetimeTaxes())));
    }

    public record Deltas(BigDecimal investableAssets, BigDecimal netWorth,
                         BigDecimal afterTaxEstate, BigDecimal lifetimeTaxes) {
        public Deltas {
            Objects.requireNonNull(investableAssets);
            Objects.requireNonNull(netWorth);
            Objects.requireNonNull(afterTaxEstate);
            Objects.requireNonNull(lifetimeTaxes);
        }
    }
}
