package com.daviddunn.retirementplanner.ui.socialsecurity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.OptionalInt;

/** A display cell retains one complete successful strategy, or an explicit unavailable result. */
record ClaimingStrategyHeatMapCell(
        int primaryClaimingAge,
        int spouseClaimingAge,
        Optional<Strategy> strategy,
        int failedStrategyCount) {

    ClaimingStrategyHeatMapCell {
        Objects.requireNonNull(strategy);
        if (failedStrategyCount < 0) throw new IllegalArgumentException("Failure count cannot be negative.");
    }

    record SurvivorElection(int ageYears, int ageMonths, LocalDate claimDate) {
        SurvivorElection { Objects.requireNonNull(claimDate); }
    }

    record Strategy(
            int inputOrder,
            Map<ClaimingStrategyHeatMapMetric, BigDecimal> values,
            SurvivorElection primarySurvivor,
            SurvivorElection spouseSurvivor,
            boolean optimal,
            OptionalInt rank) {
        Strategy {
            values = Map.copyOf(values);
            Objects.requireNonNull(primarySurvivor);
            Objects.requireNonNull(spouseSurvivor);
            Objects.requireNonNull(rank);
        }
        Optional<BigDecimal> percentOfOptimal() { return Optional.ofNullable(values.get(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)); }
        BigDecimal differenceFromOptimal() { return values.get(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL); }
        Optional<BigDecimal> differenceFromCurrent() { return Optional.ofNullable(values.get(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT)); }
    }

    Optional<BigDecimal> value(ClaimingStrategyHeatMapMetric metric) {
        Objects.requireNonNull(metric);
        return strategy.map(value -> value.values().get(metric));
    }

    boolean optimal() { return strategy.map(Strategy::optimal).orElse(false); }
}
