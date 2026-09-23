package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Fully captured, immutable report values. No controls, live plan, or calculation services. */
public record IntegratedAnalyzerReport(
        String analysisType,
        String household,
        Instant runStarted,
        Instant exportedAt,
        List<String> context,
        List<String> selectedSummary,
        HeatMap heatMap,
        List<Section> assumptions,
        List<Table> rankedTables,
        List<Section> results) {
    public static final String TITLE = "Integrated Retirement Plan Social Security Analysis";

    public IntegratedAnalyzerReport {
        Objects.requireNonNull(analysisType);
        Objects.requireNonNull(household);
        Objects.requireNonNull(runStarted);
        Objects.requireNonNull(exportedAt);
        context = List.copyOf(context);
        selectedSummary = List.copyOf(selectedSummary);
        Objects.requireNonNull(heatMap);
        assumptions = List.copyOf(assumptions);
        rankedTables = List.copyOf(rankedTables);
        results = List.copyOf(results);
    }

    public record Section(String title, List<String> lines) {
        public Section { lines = List.copyOf(lines); }
    }

    public record Column(String title, int width, boolean numeric) { }

    public record Table(String title, List<Column> columns, List<List<String>> rows) {
        public Table {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
            int columnCount = columns.size();
            if (rows.stream().anyMatch(row -> row.size() != columnCount)) {
                throw new IllegalArgumentException("Report table column count does not match its rows.");
            }
        }
    }

    public record HeatMap(String metric, String explanation, List<Integer> primaryAges,
                          List<Integer> spouseAges, List<Cell> cells) {
        public HeatMap {
            primaryAges = List.copyOf(primaryAges);
            spouseAges = List.copyOf(spouseAges);
            cells = List.copyOf(cells);
            if (cells.size() != primaryAges.size() * spouseAges.size()) {
                throw new IllegalArgumentException("Every heat-map cell must be captured.");
            }
        }
    }

    public record Cell(int primaryAge, int spouseAge, String value,
                       ClaimingHeatMapPalette tier, boolean optimal, boolean selected) { }
}
