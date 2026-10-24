package com.daviddunn.retirementplanner.app.analysis.parameters;

import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ParameterFixtures {
    private ParameterFixtures() { }
    public static AnalysisParameterConfiguration configuration(AnalysisKind kind) {
        AnalysisParameterConfiguration.Parameters parameters;
        if (kind.isMonteCarlo()) {
            boolean longevity = kind.name().endsWith("LONGEVITY");
            boolean couple = kind.shape() == AnalysisKind.Shape.COUPLE;
            parameters = new AnalysisParameterConfiguration.MonteCarlo(5000, 417, new BigDecimal("0.0500"), new BigDecimal("0.1200"),
                    new AnalysisParameterConfiguration.Inflation("STOCHASTIC", new BigDecimal("0.02750"), new BigDecimal("0.01750"), new BigDecimal("-0.0200")),
                    longevity ? new BigDecimal("1.00") : null, longevity && couple ? new BigDecimal("1.000") : null,
                    longevity && couple ? 67 : null, longevity && couple && kind.name().contains("PAIRED") ? 67 : null);
        } else if (kind == AnalysisKind.COUPLE_QUICK) parameters = new AnalysisParameterConfiguration.Quick(10, "a".repeat(64));
        else if (kind == AnalysisKind.COUPLE_EXHAUSTIVE || kind == AnalysisKind.INDIVIDUAL_DETERMINISTIC)
            parameters = new AnalysisParameterConfiguration.Search("standard-v1", "AFTER_TAX_ESTATE", 20, null);
        else parameters = new AnalysisParameterConfiguration.Mortality(new BigDecimal("1.00"),
                    kind.shape() == AnalysisKind.Shape.COUPLE ? new BigDecimal("1.000") : null,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), new BigDecimal("0.0100"), "standard-v1",
                    kind == AnalysisKind.COUPLE_WEIGHTED ? new AnalysisParameterConfiguration.Baseline(
                            new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.UNAVAILABLE, "6x", "Analyzer override", null),
                            new AnalysisParameterConfiguration.OptionalElection(AnalysisParameterConfiguration.OptionalElection.State.VALUE, "67", "Plan death scenario", 67)) : null);
        return new AnalysisParameterConfiguration(1, kind, kind.shape(), UUID.randomUUID(), Instant.parse("2026-10-23T12:00:00Z"), parameters, Map.of("contract", "1"));
    }
}
