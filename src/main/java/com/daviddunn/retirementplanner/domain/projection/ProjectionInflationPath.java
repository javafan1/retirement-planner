package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.time.Year;
import java.util.*;

/**
 * Immutable, run-only general spending inflation. A rate for year Y grows spending
 * from Y-1 into Y. Opening-year spending stays at its original base (including
 * partial-year proration); its rate is recorded but does not inflate that base.
 * Missing years fail explicitly. No plan-wide or specialized-rate fallback.
 */
public final class ProjectionInflationPath {
    public record AnnualRate(int year, BigDecimal rate) {
        public AnnualRate {
            Year.of(year);
            Objects.requireNonNull(rate, "Inflation rate is required.");
            if (rate.compareTo(BigDecimal.ONE.negate()) <= 0) {
                throw new IllegalArgumentException("Inflation must exceed -100%.");
            }
        }
    }

    private final Map<Integer, BigDecimal> rates;

    public ProjectionInflationPath(List<AnnualRate> annualRates) {
        Objects.requireNonNull(annualRates, "Annual inflation rates are required.");
        var copy = new TreeMap<Integer, BigDecimal>();
        for (var annual : annualRates) {
            Objects.requireNonNull(annual, "Annual inflation entry is required.");
            if (copy.putIfAbsent(annual.year(), annual.rate()) != null) {
                throw new IllegalArgumentException("Duplicate inflation year: " + annual.year());
            }
        }
        rates = Collections.unmodifiableMap(copy);
    }

    public BigDecimal inflationForYear(int year) {
        var rate = rates.get(year);
        if (rate == null) {
            throw new IllegalArgumentException("Missing general spending inflation for " + year);
        }
        return rate;
    }

    public void requireCoverage(int firstYear, int lastYear) {
        for (int year = firstYear; year <= lastYear; year++) {
            inflationForYear(year);
        }
    }

    public Map<Integer, BigDecimal> annualRates() {
        return rates;
    }
}
