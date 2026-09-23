package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.ui.breakeven.BreakEvenPresentation;
import org.apache.pdfbox.pdmodel.*;
import java.awt.Color;
import java.io.IOException;
import java.math.*;
import java.util.*;
import static com.daviddunn.retirementplanner.app.export.PdfReportSupport.*;

/** Vector presentation of existing annual differences, events and survival points. */
final class BreakEvenPdfChart {
    private static final float LEFT = 88, WIDTH = 660, BOTTOM = 155, TOP = 430;
    private final BreakEvenPdfReport report;
    private final BreakEvenMetricResult metric;
    private BigDecimal minimum, span;
    private int first, last;
    BreakEvenPdfChart(BreakEvenPdfReport report, BreakEvenMetricResult metric) {
        this.report = report;
        this.metric = metric;
    }
    void appendTo(PDDocument document) throws IOException {
        var page = new PDPage(LANDSCAPE);
        document.addPage(page);
        try (var out = new PDPageContentStream(document, page)) {
            text(out, BreakEvenPresentation.metricName(metric.metric()) + " Difference", 36, 576, 18, true, INK);
            text(out, "Current - Baseline", 36, 556, 11, false, INK);
            text(out, BreakEvenPresentation.banner(report.analysis(), metric), 36, 535, 11, true, INK);
            float headerY = 518;
            for (String line : wrap(BreakEvenPresentation.summary(report.analysis(), metric), 710, 9)) {
                text(out, line, 36, headerY, 9, false, INK); headerY -= 12;
            }
            if (metric.years().isEmpty()) return;
            first = metric.years().getFirst().year(); last = metric.years().getLast().year();
            minimum = BigDecimal.ZERO;
            BigDecimal maximum = BigDecimal.ZERO;
            for (var point : metric.years()) {
                minimum = minimum.min(point.difference()); maximum = maximum.max(point.difference());
            }
            BigDecimal padding = maximum.subtract(minimum).multiply(new BigDecimal("0.12")).max(BigDecimal.ONE);
            minimum = minimum.subtract(padding); maximum = maximum.add(padding); span = maximum.subtract(minimum);
            text(out, "CURRENT PLAN AHEAD (+)  /  BASELINE AHEAD (-)", LEFT, 463, 9, true, INK);
            text(out, metric.metric() == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY
                    ? "Cumulative dollar difference" : "End-of-year dollar difference", LEFT, 448, 9, false, INK);
            for (int i = 0; i <= 5; i++) {
                BigDecimal value = minimum.add(span.multiply(BigDecimal.valueOf(i)).divide(BigDecimal.valueOf(5)));
                float y = y(value);
                line(out, LEFT, y, LEFT + WIDTH, y, new Color(220, 226, 234), 0.5f);
                text(out, currency(value), 12, y - 3, 8, false, INK);
            }
            line(out, LEFT, y(BigDecimal.ZERO), LEFT + WIDTH, y(BigDecimal.ZERO), new Color(74, 85, 104), 1.6f);
            line(out, LEFT, BOTTOM, LEFT, TOP, INK, 0.7f);
            line(out, LEFT, BOTTOM, LEFT + WIDTH, BOTTOM, INK, 0.7f);
            List<BreakEvenYearResult> ticks = new ArrayList<>();
            int step = Math.max(1, (metric.years().size() + 9) / 10);
            for (int i = 0; i < metric.years().size(); i += step) ticks.add(metric.years().get(i));
            if (ticks.getLast() != metric.years().getLast()) ticks.add(metric.years().getLast());
            if (ticks.size() > 2 && x(ticks.getLast().year()) - x(ticks.get(ticks.size() - 2).year()) < 32) ticks.remove(ticks.size() - 2);
            for (var tick : ticks) {
                float x = x(tick.year());
                line(out, x, BOTTOM, x, BOTTOM - 4, INK, 0.7f);
                text(out, Integer.toString(tick.year()), x - 11, BOTTOM - 17, 9, false, INK);
                var survival = report.context().survival().get(tick.year());
                text(out, survival == null ? "—" : BreakEvenPresentation.probability(survival), x - 10, 99, 9, true, INK);
            }
            text(out, "Calendar Year", 380, 119, 10, false, INK);
            text(out, "Probability at least one spouse alive", LEFT, 79, 10, true, INK);
            text(out, "Modeled survival context, not an individual lifespan prediction. See mortality notes.", LEFT, 63, 9, false, INK);
            text(out, "Comparison: " + first + "–" + last + " · " + metric.years().size() + " shared years", LEFT, 45, 9, false, INK);
            List<float[]> occupied = new ArrayList<>();
            for (var event : report.context().events()) {
                if (event.year() < first || event.year() > last) continue;
                out.setLineDashPattern(new float[]{4, 3}, 0);
                line(out, x(event.year()), BOTTOM, x(event.year()), TOP, new Color(85, 98, 117), 1);
                out.setLineDashPattern(new float[]{}, 0);
            }
            for (var event : report.context().events()) {
                if (event.year() < first || event.year() > last) continue;
                label(out, BreakEvenPresentation.eventLabel(event), x(event.year()), TOP - 4, 135, occupied, false);
            }
            BreakEvenYearResult previous = null;
            for (var point : metric.years()) {
                if (previous != null) line(out, x(previous.year()), y(previous.difference()), x(point.year()), y(point.difference()), ORANGE, 2.5f);
                else rect(out, x(point.year()) - 2, y(point.difference()) - 2, 4, 4, ORANGE);
                previous = point;
            }
            var sustained = BreakEvenPresentation.sustainedPoint(metric);
            if (sustained != null) {
                float px = x(sustained.year()), py = y(sustained.difference());
                Color green = new Color(31, 107, 73);
                rect(out, px - 4, py - 4, 8, 8, green);
                String label = "Break-even " + sustained.year() + "\n"
                        + BreakEvenPresentation.compactAges(sustained, report.analysis().currentAssumptions());
                var survival = report.context().survival().get(sustained.year());
                if (survival != null) label += "\n" + BreakEvenPresentation.probability(survival) + " chance at least one alive";
                float[] bounds = label(out, label, px, Math.min(TOP - 4, Math.max(BOTTOM + 100, py + 90)), 185, occupied, true);
                line(out, px, py + 5, Math.max(bounds[0], Math.min(bounds[0] + bounds[2], px)), bounds[1], green, 0.8f);
            }
        }
    }
    private float[] label(PDPageContentStream out, String value, float anchorX, float top, float width,
                          List<float[]> occupied, boolean primary) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String part : value.split("\n")) lines.addAll(wrap(part, width - 12, 9));
        float height = lines.size() * 11 + 12;
        float left = Math.max(LEFT + 2, Math.min(LEFT + WIDTH - width - 2, anchorX + 5));
        boolean collision;
        do {
            collision = false;
            for (float[] box : occupied) if (left < box[0] + box[2] + 4 && left + width + 4 > box[0]
                    && top > box[1] - 4 && top - height < box[1] + box[3] + 4) {
                top = box[1] - 6; collision = true; break;
            }
        } while (collision);
        top = Math.max(BOTTOM + height + 5, top);
        float[] box = {left, top - height, width, height}; occupied.add(box);
        rect(out, left, top - height, width, height, primary ? new Color(235, 247, 240) : Color.WHITE);
        out.setStrokingColor(primary ? new Color(31, 107, 73) : new Color(156, 164, 178));
        out.setLineWidth(primary ? 1.2f : 0.6f); out.addRect(left, top - height, width, height); out.stroke();
        float textY = top - 13;
        for (String line : lines) { text(out, line, left + 6, textY, 9, true, INK); textY -= 11; }
        return box;
    }
    private float x(int year) { return first == last ? LEFT + WIDTH / 2 : LEFT + WIDTH * (year - first) / (last - first); }
    private float y(BigDecimal value) { return BOTTOM + value.subtract(minimum).divide(span, MathContext.DECIMAL64).floatValue() * (TOP - BOTTOM); }
}