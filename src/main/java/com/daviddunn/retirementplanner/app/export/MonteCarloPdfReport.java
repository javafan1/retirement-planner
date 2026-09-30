package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable, completed-result report input. No plan, controller, JavaFX node or execution service. */
public record MonteCarloPdfReport(String title, String type, String filename, Instant exportedAt,
        List<String> context, Chart chart, List<Section> sections) {
    public MonteCarloPdfReport {
        Objects.requireNonNull(title); Objects.requireNonNull(type); Objects.requireNonNull(filename);
        Objects.requireNonNull(exportedAt); Objects.requireNonNull(chart);
        context = List.copyOf(context); sections = List.copyOf(sections);
    }

    public record Section(String title, List<String> paragraphs, List<Table> tables) {
        public Section { Objects.requireNonNull(title); paragraphs = List.copyOf(paragraphs); tables = List.copyOf(tables); }
    }

    public record Table(List<String> headings, List<List<String>> rows, List<Integer> widths) {
        public Table {
            headings = List.copyOf(headings); widths = List.copyOf(widths);
            rows = rows.stream().map(List::copyOf).toList();
            int columns = widths.size();
            if (headings.isEmpty() || headings.size() != widths.size() || widths.stream().anyMatch(w -> w <= 0)
                    || widths.stream().mapToInt(Integer::intValue).sum() != 540
                    || rows.stream().anyMatch(row -> row.size() != columns)) {
                throw new IllegalArgumentException("Report table must fit the 540-point content width.");
            }
        }
    }

    public record Point(int year, Optional<MonteCarloPercentiles> percentiles, Optional<BigDecimal> reference) {
        public Point { Objects.requireNonNull(percentiles); Objects.requireNonNull(reference); }
    }
    public record Marker(int year, String label) { }
    public record Period(int firstYear, int lastYear, String label) { }
    public record Chart(String title, String explanation, boolean difference, List<Point> points,
            List<Marker> markers, List<Period> periods) {
        public Chart {
            Objects.requireNonNull(title); Objects.requireNonNull(explanation);
            points = List.copyOf(points); markers = List.copyOf(markers); periods = List.copyOf(periods);
        }
    }
}
