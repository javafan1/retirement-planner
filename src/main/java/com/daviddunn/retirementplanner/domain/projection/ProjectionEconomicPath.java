package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, run-only nominal annual investment returns. Never persisted on a plan.
 * Rates are full-year inputs; the engine retains its existing partial-year proration.
 */
public final class ProjectionEconomicPath {
    private final Map<Integer, BigDecimal> returns;
    private final BigDecimal constant;

    private ProjectionEconomicPath(Map<Integer, BigDecimal> returns, BigDecimal constant) {
        this.returns = returns;
        this.constant = constant;
    }

    public static ProjectionEconomicPath constant(BigDecimal rate) {
        // Legacy deterministic inputs retain their existing validation and behavior.
        return new ProjectionEconomicPath(Map.of(), Objects.requireNonNull(rate));
    }

    public static ProjectionEconomicPath annual(Map<Integer, BigDecimal> returns) {
        var copy = Map.copyOf(Objects.requireNonNull(returns));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Annual returns are required.");
        }
        copy.forEach((year, rate) -> {
            java.time.Year.of(year);
            if (rate.compareTo(BigDecimal.ONE.negate()) < 0) {
                throw new IllegalArgumentException("Investment return cannot be below -100%.");
            }
        });
        return new ProjectionEconomicPath(copy, null);
    }

    public BigDecimal investmentReturnForYear(int year) {
        if (constant != null) {
            return constant;
        }
        var rate = returns.get(year);
        if (rate == null) {
            throw new IllegalArgumentException("Missing investment return for " + year);
        }
        return rate;
    }

    public void requireCoverage(int firstYear, int lastYear) {
        for (int year = firstYear; year <= lastYear; year++) {
            investmentReturnForYear(year);
        }
    }

    public Map<Integer, BigDecimal> annualReturns() {
        return returns;
    }
}