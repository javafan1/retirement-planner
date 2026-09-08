package com.daviddunn.retirementplanner.app.socialsecurity;

import java.math.BigDecimal;
import java.util.Objects;

/** Compact Stage 4 values, with no per-scenario outcomes or duplicated methodology. */
public record LongevityWeightedStrategyAggregate(
        BigDecimal expectedPvAfterTaxEstate,
        BigDecimal expectedNominalEstateAtSecondDeath,
        BigDecimal minimumNominalScenarioEstate,
        BigDecimal maximumNominalScenarioEstate,
        BigDecimal totalEvaluatedProbability,
        int originalScenarioCount,
        int actualProjectionRunCount) {
    public LongevityWeightedStrategyAggregate {
        Objects.requireNonNull(expectedPvAfterTaxEstate);
        Objects.requireNonNull(expectedNominalEstateAtSecondDeath);
        Objects.requireNonNull(minimumNominalScenarioEstate);
        Objects.requireNonNull(maximumNominalScenarioEstate);
        Objects.requireNonNull(totalEvaluatedProbability);
    }

    public static LongevityWeightedStrategyAggregate from(LongevityWeightedIntegratedStrategyResult result) {
        return new LongevityWeightedStrategyAggregate(result.expectedPvAfterTaxEstate(),
                result.expectedNominalEstateAtSecondDeath(), result.minimumNominalScenarioEstate(),
                result.maximumNominalScenarioEstate(), result.totalEvaluatedProbability(),
                result.originalScenarioCount(), result.actualProjectionRunCount());
    }
}
