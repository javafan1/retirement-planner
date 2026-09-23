package com.daviddunn.retirementplanner.ui.socialsecurity;

import java.util.*;

/** Presentation vocabulary and supported metrics, independent of financial engines. */
record ClaimingStrategyHeatMapProfile(
        List<ClaimingStrategyHeatMapMetric> displayMetrics,
        List<ClaimingStrategyHeatMapMetric> detailMetrics,
        Map<ClaimingStrategyHeatMapMetric, String> helpOverrides,
        String explanation,
        String rankLabel) {
    ClaimingStrategyHeatMapProfile {
        displayMetrics = List.copyOf(displayMetrics);
        detailMetrics = List.copyOf(detailMetrics);
        helpOverrides = Map.copyOf(helpOverrides);
        Objects.requireNonNull(explanation);
        Objects.requireNonNull(rankLabel);
    }

    String help(ClaimingStrategyHeatMapMetric metric) { return helpOverrides.getOrDefault(metric, metric.help()); }

    static ClaimingStrategyHeatMapProfile weighted() {
        return new ClaimingStrategyHeatMapProfile(List.of(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE, ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL,
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE),
                List.of(ClaimingStrategyHeatMapMetric.values()), Map.of(),
                "Each cell uses the best successful complete strategy for that age pair, including its survivor elections. "
                        + "Values reflect taxes, withdrawals, Roth conversions, investments, RMDs, Social Security, survivor benefits, and mortality weighting. "
                        + "Optimal means highest among the successfully tested strategies, not a recommendation.", "Weighted rank");
    }

    static ClaimingStrategyHeatMapProfile deterministic() {
        var metrics = List.of(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL, ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT,
                ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE);
        return new ClaimingStrategyHeatMapProfile(metrics, metrics, Map.of(
                ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL,
                "Deterministic after-tax estate divided by the highest tested strategy's after-tax estate, multiplied by 100. Percentages require a positive highest value.",
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL,
                "This strategy's deterministic after-tax estate minus the highest tested strategy's after-tax estate. Zero means equal value; negative means lower value.",
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT,
                "This strategy's deterministic after-tax estate minus the current plan's deterministic after-tax estate, using its persisted death scenario and survivor policy.",
                ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE,
                "After-Tax Heir Value at the configured deterministic projection horizon, in future dollars. This is the deterministic ranking objective; no discounting or mortality weighting is applied."),
                "Each cell uses the highest-ranked successful complete strategy for that age pair under deterministic plan assumptions. "
                        + "Taxes, withdrawals, Roth conversions, investments, RMDs, Social Security and survivor benefits use the existing deterministic projection. "
                        + "No mortality weighting or expected-value calculation is used. Optimal means highest tested after-tax estate, not a recommendation.",
                "Deterministic estate rank");
    }
}
