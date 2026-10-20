package com.daviddunn.retirementplanner.app.socialsecurity.results;

import java.time.Instant;
import java.util.*;

/** Frozen display/report data, never a live plan or a JavaFX object. */
public record SnapshotContext(String household, String primary, String spouse,
        Instant startedAt, List<Section> assumptions, List<Section> request,
        List<Section> comparisons, Map<String, String> preferences, Baseline baseline,
        List<HistoricalComparison> historicalComparisons) {
    public record ComparisonRow(String role, WeightedSnapshots.Strategy strategy, Integer deterministicRank, Integer weightedRank,
            java.math.BigDecimal deterministicEstate, java.math.BigDecimal weightedPv,
            java.math.BigDecimal deterministicInvestableAssets, java.math.BigDecimal weightedInvestableAssets,
            java.math.BigDecimal weightedHeirValue) {
        public ComparisonRow {
            Objects.requireNonNull(role); strategy.restore();
            if (deterministicRank != null && deterministicRank < 1 || weightedRank != null && weightedRank < 1)
                throw new IllegalArgumentException("Invalid historical comparison rank.");
        }
    }
    public record HistoricalComparison(AnalysisKind sourceKind, UUID sourceRunId, String sourceInputFingerprint,
            String explanation, List<ComparisonRow> rows) {
        public HistoricalComparison {
            Objects.requireNonNull(sourceKind); Objects.requireNonNull(explanation); rows = List.copyOf(rows);
            if (sourceInputFingerprint != null && !sourceInputFingerprint.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid historical source fingerprint.");
        }
    }
    public record Election(String name, String value, String source) {
        public Election { Objects.requireNonNull(name); Objects.requireNonNull(value); Objects.requireNonNull(source); }
    }
    public record Baseline(List<Election> elections, WeightedSnapshots.Strategy strategy, String problem) {
        public Baseline {
            elections = List.copyOf(elections); Objects.requireNonNull(problem);
            if (strategy != null) strategy.restore();
        }
        public List<Section> fingerprintSections() {
            return elections.stream().map(e -> new Section(e.name(), List.of(e.value(), e.source()))).toList();
        }
    }
    public SnapshotContext(String household, String primary, String spouse, Instant startedAt,
            List<Section> assumptions, List<Section> request, List<Section> comparisons, Map<String, String> preferences) {
        this(household, primary, spouse, startedAt, assumptions, request, comparisons, preferences, null, List.of());
    }
    public SnapshotContext(String household, String primary, String spouse, Instant startedAt,
            List<Section> assumptions, List<Section> request, List<Section> comparisons, Map<String, String> preferences, Baseline baseline) {
        this(household, primary, spouse, startedAt, assumptions, request, comparisons, preferences, baseline, List.of());
    }
    public record Section(String title, List<String> lines) {
        public Section { Objects.requireNonNull(title); lines = List.copyOf(lines); }
    }
    public SnapshotContext {
        Objects.requireNonNull(household); Objects.requireNonNull(primary);
        Objects.requireNonNull(startedAt);
        assumptions = List.copyOf(assumptions); request = List.copyOf(request);
        comparisons = List.copyOf(comparisons); preferences = Map.copyOf(preferences);
        historicalComparisons = List.copyOf(historicalComparisons);
        if (!Set.of("metric", "occurrence", "tab", "claimingAge").containsAll(preferences.keySet())) {
            throw new IllegalArgumentException("Unknown presentation preference.");
        }
        if (preferences.containsKey("metric") && !Set.of("PERCENT_OF_OPTIMAL", "EXPECTED_PV_AFTER_TAX_ESTATE",
                "DIFFERENCE_FROM_OPTIMAL", "DIFFERENCE_FROM_CURRENT", "EXPECTED_PV_SOCIAL_SECURITY", "FUTURE_DOLLAR_ESTATE")
                .contains(preferences.get("metric"))) throw new IllegalArgumentException("Unknown saved display metric.");
        if (preferences.containsKey("tab") && !Set.of("ranked", "heat-map").contains(preferences.get("tab")))
            throw new IllegalArgumentException("Unknown saved result tab.");
        if (preferences.containsKey("occurrence") && (!preferences.get("occurrence").matches("[1-9][0-9]{0,5}")))
            throw new IllegalArgumentException("Invalid saved occurrence.");
        if (preferences.containsKey("claimingAge") && !preferences.get("claimingAge").matches("6[2-9]|70"))
            throw new IllegalArgumentException("Invalid saved claiming age.");
    }
}
