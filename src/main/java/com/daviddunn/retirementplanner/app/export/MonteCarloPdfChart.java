package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static com.daviddunn.retirementplanner.app.export.PdfReportSupport.*;

/** Vector display geometry over cached quantiles. No stochastic or financial calculation. */
final class MonteCarloPdfChart {
    private static final Color OUTER = new Color(224, 234, 249);
    private static final Color INNER = new Color(175, 199, 234);
    private static final Color MEDIAN = new Color(35, 93, 196);
    private final Chart chart;
    private final PDPageContentStream out;
    private final float left = 111, width = 456, height = 172;
    private final float bottom;
    private final double bound;
    private final int first, last;

    MonteCarloPdfChart(Chart chart, PDPageContentStream out, float top) {
        this.chart = chart; this.out = out; bottom = top - height - 8;
        first = chart.points().isEmpty() ? 0 : chart.points().getFirst().year();
        last = chart.points().isEmpty() ? 1 : chart.points().getLast().year();
        double largest = 1;
        for (var p : chart.points()) {
            if (p.percentiles().isPresent()) {
                largest = Math.max(largest, Math.abs(p.percentiles().orElseThrow().p90().doubleValue()));
                largest = Math.max(largest, Math.abs(p.percentiles().orElseThrow().p10().doubleValue()));
            }
            if (p.reference().isPresent()) largest = Math.max(largest, Math.abs(p.reference().orElseThrow().doubleValue()));
        }
        bound = largest * 1.12;
    }

    void draw() throws IOException {
        if (chart.points().isEmpty()) {
            text(out, "No annual financial rows: no living/comparable annual sample.", 44, bottom + 90, 10, false, INK);
            return;
        }
        // Period backgrounds use two separate edge strips as well as distinct tones.
        for (var p : chart.periods()) {
            boolean roth = p.label().equals("Roth");
            float x = x(p.firstYear() - .45), w = x(p.lastYear() + .45) - x;
            rect(out, x, bottom, w, height, roth ? new Color(246, 240, 250) : new Color(247, 244, 232));
        }
        for (var p : chart.periods()) {
            boolean roth = p.label().equals("Roth");
            float x = x(p.firstYear() - .45), w = x(p.lastYear() + .45) - x;
            rect(out, x, bottom + (roth ? height - 3 : 0), w, 3, roth ? new Color(145, 122, 168) : new Color(163, 147, 88));
        }
        for (int i = 0; i <= 4; i++) {
            double v = chart.difference() ? -bound + i * bound / 2 : i * bound / 4;
            float y = y(v);
            line(out, left, y, left + width, y, new Color(219, 225, 234), .5f);
            String label = currency(BigDecimal.valueOf(Math.abs(v)));
            if (v < 0) label = "-" + label; else if (chart.difference() && v > 0) label = "+" + label;
            float labelWidth = NORMAL.getStringWidth(label) / 1000 * 8;
            text(out, label, left - labelWidth - 6, y - 3, 8, false, INK);
        }
        List<Point> segment = new ArrayList<>();
        for (var point : chart.points()) {
            if (point.percentiles().isEmpty()) { segment(segment); segment.clear(); }
            else segment.add(point);
        }
        segment(segment);
        out.setLineDashPattern(new float[]{5, 3}, 0);
        Point previous = null;
        for (var p : chart.points()) {
            if (p.reference().isEmpty()) { previous = null; continue; }
            if (previous != null) line(out, x(previous.year()), y(previous.reference().orElseThrow().doubleValue()),
                    x(p.year()), y(p.reference().orElseThrow().doubleValue()), ORANGE, 1.6f);
            previous = p;
        }
        out.setLineDashPattern(new float[]{}, 0);
        if (chart.difference()) {
            out.setLineDashPattern(new float[]{7, 3}, 0);
            line(out, left, y(0), left + width, y(0), INK, 1.8f);
            out.setLineDashPattern(new float[]{}, 0);
        }
        int step = Math.max(1, (int) Math.ceil((last - first + 1) / 8.0));
        for (int year = first; year <= last; year += step) {
            line(out, x(year), bottom, x(year), bottom - 4, Color.GRAY, .5f);
            text(out, "" + year, x(year) - 10, bottom - 15, 8, false, INK);
        }
        text(out, "Calendar year", left + width / 2 - 25, bottom - 29, 9, false, INK);
        text(out, chart.difference() ? "Current - Baseline ($)" : "Nominal dollars ($)", left, bottom + height + 5, 8, false, INK);
        int markerIndex = 0;
        for (var marker : chart.markers()) {
            out.setLineDashPattern(new float[]{2, 3}, 0);
            line(out, x(marker.year()), bottom, x(marker.year()), bottom + height, Color.DARK_GRAY, .8f);
            out.setLineDashPattern(new float[]{}, 0);
            text(out, "SS" + (++markerIndex), Math.min(left + width - 20, x(marker.year()) + 3), bottom + height - 12 - (markerIndex % 2) * 12, 8, true, INK);
        }
        float legend = bottom - 47;
        rect(out, 44, legend - 2, 14, 8, OUTER); text(out, "P10-P90", 63, legend, 9, false, INK);
        rect(out, 131, legend - 2, 14, 8, INNER); text(out, "P25-P75", 150, legend, 9, false, INK);
        line(out, 220, legend + 3, 240, legend + 3, MEDIAN, 1.8f); text(out, "Median", 245, legend, 9, false, INK);
        if (chart.difference() || chart.points().stream().anyMatch(p -> p.reference().isPresent())) {
            out.setLineDashPattern(new float[]{5, 3}, 0);
            line(out, 312, legend + 3, 339, legend + 3, chart.difference() ? INK : ORANGE, 1.8f);
            text(out, chart.difference() ? "Zero reference" : "Deterministic", 345, legend, 9, false, INK);
            out.setLineDashPattern(new float[]{}, 0);
        }
    }

    private float x(double year) { return left + (float) ((year - first + .5) / (last - first + 1.0) * width); }
    private float y(double value) {
        return bottom + (float) ((chart.difference() ? (value + bound) / (2 * bound) : value / bound) * height);
    }
    private void segment(List<Point> points) throws IOException {
        if (points.isEmpty()) return;
        band(points, MonteCarloPercentiles::p10, MonteCarloPercentiles::p90, OUTER);
        band(points, MonteCarloPercentiles::p25, MonteCarloPercentiles::p75, INNER);
        for (var metric : List.<Function<MonteCarloPercentiles, BigDecimal>>of(MonteCarloPercentiles::p10, MonteCarloPercentiles::p25,
                MonteCarloPercentiles::p50, MonteCarloPercentiles::p75, MonteCarloPercentiles::p90)) {
            int position = 0;
            out.setStrokingColor(MEDIAN); out.setLineWidth(.5f);
            for (var point : points) {
                float x = x(point.year()), y = y(metric.apply(point.percentiles().orElseThrow()).doubleValue());
                if (position++ == 0) out.moveTo(x, y); else out.lineTo(x, y);
            }
            out.stroke();
        }
        out.setStrokingColor(MEDIAN); out.setLineWidth(1.8f);
        for (int i = 0; i < points.size(); i++) {
            var p = points.get(i); float x = x(p.year()), y = y(p.percentiles().orElseThrow().p50().doubleValue());
            if (i == 0) out.moveTo(x, y); else out.lineTo(x, y);
            if (points.size() == 1) out.lineTo(x + 2, y);
        }
        out.stroke();
    }
    private void band(List<Point> points, Function<MonteCarloPercentiles, BigDecimal> low,
            Function<MonteCarloPercentiles, BigDecimal> high, Color color) throws IOException {
        out.setNonStrokingColor(color);
        boolean firstPoint = true;
        for (var p : points) {
            float x = x(p.year()), y = y(high.apply(p.percentiles().orElseThrow()).doubleValue());
            if (firstPoint) out.moveTo(x, y); else out.lineTo(x, y); firstPoint = false;
        }
        for (var p : points.reversed()) out.lineTo(x(p.year()), y(low.apply(p.percentiles().orElseThrow()).doubleValue()));
        out.closePath(); out.fill();
    }
}
