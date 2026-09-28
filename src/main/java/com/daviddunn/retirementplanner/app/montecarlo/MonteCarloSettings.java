package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Session inputs. Expected return is annual nominal ARITHMETIC mean;
 * volatility is the standard deviation of annual simple returns, not log returns.
 * No implicit volatility assumption: callers must supply it explicitly.
 */
public record MonteCarloSettings(int simulationCount, long seed,
                                 BigDecimal expectedReturn, BigDecimal returnVolatility,
                                 Optional<MonteCarloInflationSettings> inflation) {
    public MonteCarloSettings(int simulationCount, long seed,
            BigDecimal expectedReturn, BigDecimal returnVolatility) {
        this(simulationCount, seed, expectedReturn, returnVolatility, Optional.empty());
    }

    public MonteCarloSettings withInflation(MonteCarloInflationSettings settings) {
        return new MonteCarloSettings(simulationCount, seed, expectedReturn, returnVolatility,
                Optional.of(Objects.requireNonNull(settings)));
    }

    public MonteCarloSettings {
        Objects.requireNonNull(inflation, "Inflation settings optional is required.");
        Objects.requireNonNull(expectedReturn);
        Objects.requireNonNull(returnVolatility);
        if (simulationCount < 1 || simulationCount > 100_000) {
            throw new IllegalArgumentException("Simulation count must be 1 through 100,000.");
        }
        if (expectedReturn.compareTo(BigDecimal.ONE.negate()) <= 0) {
            throw new IllegalArgumentException("Expected return must exceed -100%.");
        }
        if (returnVolatility.signum() < 0 || !Double.isFinite(returnVolatility.doubleValue())
                || !Double.isFinite(expectedReturn.doubleValue())) {
            throw new IllegalArgumentException("Return parameters must be finite; volatility cannot be negative.");
        }
    }

    public static MonteCarloSettings forPlan(RetirementPlan plan, int count, long seed, BigDecimal volatility) {
        return new MonteCarloSettings(count, seed,
                plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn(), volatility);
    }

    /** Preserve legacy deterministic run metadata and its complete-result fingerprints. */
    @Override
    public String toString() {
        return "MonteCarloSettings[simulationCount=" + simulationCount + ", seed=" + seed
                + ", expectedReturn=" + expectedReturn + ", returnVolatility=" + returnVolatility
                + inflation.map(value -> ", inflation=" + value).orElse("") + "]";
    }
}
