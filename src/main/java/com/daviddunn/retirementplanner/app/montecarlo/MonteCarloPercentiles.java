package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Empirical type-7 quantiles: interpolate sorted observations at (n - 1) * p.
 * Empty samples produce Optional.empty(), never fabricated zero percentiles.
 */
public record MonteCarloPercentiles(
        int sampleCount,
        BigDecimal minimum,
        BigDecimal p10,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        BigDecimal p90,
        BigDecimal maximum) {

    public static Optional<MonteCarloPercentiles> of(List<BigDecimal> values) {
        var sorted = new ArrayList<>(values);
        sorted.sort(BigDecimal::compareTo);
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MonteCarloPercentiles(
                sorted.size(), sorted.getFirst(),
                quantile(sorted, "0.10"), quantile(sorted, "0.25"), quantile(sorted, "0.50"),
                quantile(sorted, "0.75"), quantile(sorted, "0.90"), sorted.getLast()));
    }

    private static BigDecimal quantile(List<BigDecimal> sorted, String probability) {
        BigDecimal index = new BigDecimal(probability).multiply(BigDecimal.valueOf(sorted.size() - 1));
        int lower = index.intValue();
        if (lower == sorted.size() - 1) {
            return sorted.getLast();
        }
        return sorted.get(lower).add(sorted.get(lower + 1).subtract(sorted.get(lower))
                .multiply(index.subtract(BigDecimal.valueOf(lower))));
    }
}
