package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.function.IntFunction;

/** Frozen session input. World generation reads only assumptions, never either candidate. */
public record MonteCarloStrategyComparisonRequest(
        MonteCarloStrategyCandidate strategyA,
        MonteCarloStrategyCandidate strategyB,
        Assumptions assumptions) {

    public MonteCarloStrategyComparisonRequest {
        Objects.requireNonNull(strategyA);
        Objects.requireNonNull(strategyB);
        Objects.requireNonNull(assumptions);
    }

    public sealed interface Assumptions permits Fixed, Longevity {
        MonteCarloSettings settings();
        LocalDate start();
    }

    /** Exact common horizon. Both plans must have this start and configured death timing. */
    public record Fixed(MonteCarloSettings settings, LocalDate start, int endingYear,
                        HouseholdLifetimeScenario lifetimeScenario) implements Assumptions {
        public Fixed {
            Objects.requireNonNull(settings);
            Objects.requireNonNull(start);
            Objects.requireNonNull(lifetimeScenario);
            Year.of(endingYear);
            if (endingYear < start.getYear()) {
                throw new IllegalArgumentException("Fixed ending year precedes start.");
            }
            if (lifetimeScenario.primaryDeathYear().isPresent()
                    && lifetimeScenario.spouseDeathYear().isPresent()) {
                throw new IllegalArgumentException("Fixed configured scenarios support at most one death.");
            }
        }
    }

    /** Mortality request owns demographics/table/adjustments; its shared survivor election is not used.
     * Each candidate supplies its own persisted survivor election for either first-death direction. */
    public record Longevity(MonteCarloMortalityRequest mortality) implements Assumptions {
        public Longevity {
            Objects.requireNonNull(mortality);
        }

        @Override
        public MonteCarloSettings settings() {
            return mortality.settings();
        }

        @Override
        public LocalDate start() {
            return mortality.longevityAssumptions().mortalityBaseDate();
        }
    }

    /** Indexed reusable source: count and candidate changes cannot change a world's prefix. */
    public IntFunction<MonteCarloComparisonWorld> worldSource() {
        if (assumptions instanceof Longevity longevity) {
            var generator = new MonteCarloWorldGenerator(longevity.mortality());
            return index -> MonteCarloComparisonWorld.from(generator.generate(index));
        }
        var fixed = (Fixed) assumptions;
        var market = new MonteCarloScenarioGenerator();
        var inflation = new MonteCarloInflationGenerator();
        return index -> new MonteCarloComparisonWorld(index,
                market.generate(fixed.start().getYear(), fixed.endingYear(), fixed.settings(), index),
                fixed.lifetimeScenario(),
                inflation.generate(fixed.start().getYear(), fixed.endingYear(), fixed.settings(), index));
    }
}
