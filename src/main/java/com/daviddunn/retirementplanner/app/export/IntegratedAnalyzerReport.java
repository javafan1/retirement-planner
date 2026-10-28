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
        Visualization visualization,
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
        Objects.requireNonNull(visualization);
        assumptions = List.copyOf(assumptions);
        rankedTables = List.copyOf(rankedTables);
        results = List.copyOf(results);
    }

    public sealed interface Visualization permits HeatMap, Individual, SocialSecurityGrid, TablesOnly { }

    /** Stage 1 Social Security values, deliberately distinct from integrated estate heat maps. */
    public record SocialSecurityGrid(List<Integer> primaryAges, List<Integer> spouseAges,
                                     List<SocialSecurityCell> cells) implements Visualization {
        public SocialSecurityGrid {
            primaryAges = List.copyOf(primaryAges); spouseAges = List.copyOf(spouseAges); cells = List.copyOf(cells);
            var primary = primaryAges; var spouse = spouseAges;
            if (primaryAges.isEmpty() || spouseAges.isEmpty() || primaryAges.stream().distinct().count() != primaryAges.size()
                    || spouseAges.stream().distinct().count() != spouseAges.size()
                    || cells.size() != primaryAges.size() * spouseAges.size()
                    || cells.stream().anyMatch(c -> !primary.contains(c.primaryAge()) || !spouse.contains(c.spouseAge()))
                    || cells.stream().map(c -> List.of(c.primaryAge(), c.spouseAge())).distinct().count() != cells.size())
                throw new IllegalArgumentException("Every original SS grid pair must be captured exactly once.");
        }
    }
    public record SocialSecurityCell(int primaryAge, int spouseAge, java.math.BigDecimal presentValue,
            String value, ClaimingHeatMapPalette tier, boolean optimal, boolean retained, boolean selected) {
        public SocialSecurityCell { Objects.requireNonNull(value); Objects.requireNonNull(tier); }
    }
    /** Explicit table-only comparison. No heat map or synthetic strategy values. */
    public record TablesOnly(String explanation) implements Visualization {
        public TablesOnly { Objects.requireNonNull(explanation); }
    }

    /** Compatibility accessor for genuine couple reports only. */
    public HeatMap heatMap() { return (HeatMap) visualization; }

    public record Individual(String metric, List<IndividualAge> ages) implements Visualization {
        public Individual {
            Objects.requireNonNull(metric);
            ages = List.copyOf(ages);
            if (!ages.stream().map(IndividualAge::age).toList().equals(
                    java.util.stream.IntStream.rangeClosed(62, 70).boxed().toList())) {
                throw new IllegalArgumentException("Individual reports require ages 62 through 70 in order.");
            }
        }
    }

    public record IndividualAge(int age, String value, String percentOfOptimal,
            ClaimingHeatMapPalette tier, boolean optimal, boolean selected, boolean current) {
        public IndividualAge {
            Objects.requireNonNull(value);
            Objects.requireNonNull(percentOfOptimal);
            Objects.requireNonNull(tier);
        }
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
                          List<Integer> spouseAges, List<Cell> cells) implements Visualization {
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
