package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Complete evaluation only. Aggregates are unrounded; monetary presentation is the caller's concern. */
public record LongevityWeightedIntegratedStrategyResult(
        SocialSecurityHouseholdClaimingStrategy evaluatedStrategy,
        BigDecimal expectedPvAfterTaxEstate,
        BigDecimal expectedNominalEstateAtSecondDeath,
        BigDecimal totalEvaluatedProbability,
        int originalScenarioCount,
        int actualProjectionRunCount,
        BigDecimal minimumNominalScenarioEstate,
        BigDecimal maximumNominalScenarioEstate,
        LocalDate valuationDate,
        BigDecimal generalInflationRate,
        BigDecimal realDiscountRate,
        AnalyzerLongevityAssumptions longevityAssumptions,
        String methodology,
        List<String> financialLimitations,
        List<LongevityWeightedIntegratedScenarioOutcome> scenarioOutcomes) {
    public LongevityWeightedIntegratedStrategyResult {
        Objects.requireNonNull(evaluatedStrategy);
        Objects.requireNonNull(expectedPvAfterTaxEstate);
        Objects.requireNonNull(expectedNominalEstateAtSecondDeath);
        Objects.requireNonNull(totalEvaluatedProbability);
        Objects.requireNonNull(minimumNominalScenarioEstate);
        Objects.requireNonNull(maximumNominalScenarioEstate);
        Objects.requireNonNull(valuationDate);
        Objects.requireNonNull(generalInflationRate);
        Objects.requireNonNull(realDiscountRate);
        Objects.requireNonNull(longevityAssumptions);
        Objects.requireNonNull(methodology);
        financialLimitations = List.copyOf(financialLimitations);
        scenarioOutcomes = List.copyOf(scenarioOutcomes);
    }
}
