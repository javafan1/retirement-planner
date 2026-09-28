package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.FundingFailure;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult.TerminalOutcome;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** World-independent compact observations; reuses the existing authoritative terminal value type. */
public record MonteCarloStrategyOutcome(
        Map<Integer, BigDecimal> annualInvestableAssets,
        Optional<TerminalOutcome> terminal,
        Optional<FundingFailure> fundingFailure) {

    public MonteCarloStrategyOutcome {
        Objects.requireNonNull(terminal);
        Objects.requireNonNull(fundingFailure);
        if (terminal.isPresent() == fundingFailure.isPresent()) {
            throw new IllegalArgumentException("Exactly one completion or funding failure is required.");
        }
        annualInvestableAssets = Collections.unmodifiableMap(new TreeMap<>(annualInvestableAssets));
        annualInvestableAssets.values().forEach(Objects::requireNonNull);
    }
}
