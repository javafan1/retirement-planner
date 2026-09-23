package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Year and amount quantiles use type-7 interpolation.
 * Year probabilities use all requested simulations; conditional fractions use funding failures.
 * Funding-constraint shortfall amounts combine allocator stages, not finalized unmet spending.
 */
public record FundingFailureStatistics(
        long count,
        Map<Integer, Long> countByYear,
        Map<Integer, BigDecimal> probabilityByYear,
        Map<Integer, BigDecimal> fractionOfFailuresByYear,
        MonteCarloPercentiles firstShortfallYear,
        MonteCarloPercentiles shortfallAmount) {

    public FundingFailureStatistics {
        countByYear = Collections.unmodifiableMap(new TreeMap<>(countByYear));
        probabilityByYear = Collections.unmodifiableMap(new TreeMap<>(probabilityByYear));
        fractionOfFailuresByYear = Collections.unmodifiableMap(new TreeMap<>(fractionOfFailuresByYear));
    }

    static Optional<FundingFailureStatistics> from(List<FundingFailure> failures, int requested) {
        if (failures.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, Long> counts = new TreeMap<>();
        failures.forEach(failure -> counts.merge(failure.calendarYear(), 1L, Long::sum));
        Map<Integer, BigDecimal> rates = new TreeMap<>();
        Map<Integer, BigDecimal> conditional = new TreeMap<>();
        counts.forEach((year, count) -> {
            rates.put(year, BigDecimal.valueOf(count).divide(
                    BigDecimal.valueOf(requested), MathContext.DECIMAL128));
            conditional.put(year, BigDecimal.valueOf(count).divide(
                    BigDecimal.valueOf(failures.size()), MathContext.DECIMAL128));
        });
        return Optional.of(new FundingFailureStatistics(
                failures.size(), counts, rates, conditional,
                MonteCarloPercentiles.of(failures.stream()
                        .map(failure -> BigDecimal.valueOf(failure.calendarYear())).toList()).orElseThrow(),
                MonteCarloPercentiles.of(failures.stream()
                        .map(FundingFailure::shortfallAmount).toList()).orElseThrow()));
    }
}
