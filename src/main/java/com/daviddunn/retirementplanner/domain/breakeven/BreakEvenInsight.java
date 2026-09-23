package com.daviddunn.retirementplanner.domain.breakeven;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Observed values only; no causal attribution or mortality weighting. */
public record BreakEvenInsight(
        Integer referenceYear,
        Map<BreakEvenMetric, BreakEvenYearResult> snapshot,
        List<Observation> observations) {
    public BreakEvenInsight {
        snapshot = Map.copyOf(snapshot);
        observations = List.copyOf(observations);
    }

    public enum Driver { GROSS_PORTFOLIO_WITHDRAWALS, INCOME_TAXES, INVESTMENT_GROWTH }

    public record Observation(Driver driver, BigDecimal baseline, BigDecimal current) {
        public BigDecimal difference() { return current.subtract(baseline); }
    }
}
