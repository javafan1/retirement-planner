package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Unexpected execution failure. No partial Monte Carlo analysis is published.
 */
public final class MonteCarloExecutionException extends RuntimeException {

    public enum Phase {
        DETERMINISTIC_REFERENCE,
        SIMULATION
    }

    private final Phase phase;
    private final OptionalInt scenarioIndex;
    private final long seed;
    private final String returnModel;

    MonteCarloExecutionException(
            Phase phase,
            OptionalInt scenarioIndex,
            long seed,
            String returnModel,
            RuntimeException cause) {
        super("Monte Carlo " + phase
                + (scenarioIndex.isPresent() ? " scenario " + scenarioIndex.getAsInt() : "")
                + " failed (configured seed=" + seed + ", return model=" + returnModel + "): "
                + cause.getClass().getName() + ": " + Objects.toString(cause.getMessage(), ""), cause);
        this.phase = Objects.requireNonNull(phase);
        this.scenarioIndex = Objects.requireNonNull(scenarioIndex);
        this.seed = seed;
        this.returnModel = Objects.requireNonNull(returnModel);
    }

    public Phase phase() {
        return phase;
    }

    /**
     * Zero-based scenario index; absent for deterministic-reference failures.
     */
    public OptionalInt scenarioIndex() {
        return scenarioIndex;
    }

    /**
     * Configured seed; caller-supplied paths need not have been generated from it.
     */
    public long seed() {
        return seed;
    }

    public String returnModel() {
        return returnModel;
    }
}
