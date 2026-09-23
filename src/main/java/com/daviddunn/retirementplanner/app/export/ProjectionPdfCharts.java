package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.ui.charts.ProjectionChartMetric;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import static com.daviddunn.retirementplanner.app.export.PdfReportSupport.*;

/** Independent vector renderer of the SAME prepared values used by the interactive chart.
 * Floating-point conversion is limited to page coordinates, never financial values.
 */
final class ProjectionPdfCharts {




    private static final Color[] AREAS = {new Color(169, 197, 237), new Color(193, 181, 232), new Color(160, 208, 184)};
    private static final float LEFT = 86, BOTTOM = 130, WIDTH = 660;
    private float height = 310;
    private final ProjectionChartModel model;
    private final String planName;
    private final double first, last;

    ProjectionPdfCharts(ProjectionPdfReport report) {
        model = report.charts();
        planName = report.planName();
        first = model.years().getFirst().year() - 0.5;
        last = model.years().getLast().year() + 0.5;
    }

    void appendTo(PDDocument document) throws IOException {
        // Enum order is the existing selector's outcome-first order, not a second metric list.
        for (var metric : ProjectionChartMetric.values()) {
            PDPage page = new PDPage(new PDRectangle(792, 612));
            document.addPage(page);
            try (var out = new PDPageContentStream(document, page)) {
                render(out, metric);
            }
        }
    }

    private void render(PDPageContentStream out, ProjectionChartMetric metric) throws IOException {
        text(out, "PROJECTION CHARTS", 36, 574, 18, true, INK);
        text(out, planName + " | " + model.years().getFirst().year() + " - " + model.years().getLast().year(), 36, 552, 10, false, INK);
        text(out, metric.toString(), 36, 527, 16, true, INK);
        text(out, "Projected dollars (same values as Projection Summary)", LEFT, 510, 9, false, INK);
        var labels = prepareLabels();
        height = Math.min(340, labels.stream().map(label -> label.top() - label.lines().size() * 10 - 15 - BOTTOM).min(Float::compare).orElse(340f));
        boolean stacked = metric == ProjectionChartMetric.INVESTABLE_ASSETS
                && model.years().stream().allMatch(ProjectionChartModel.Point::compositionComplete);
        BigDecimal maximum = model.years().stream().map(p -> p.values().get(metric).abs())
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE).max(BigDecimal.ONE);
        if (stacked) {
            maximum = maximum.max(model.years().stream().map(p -> p.composition().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal::compareTo).orElse(BigDecimal.ONE));
        }
        // Axis padding only; the source series and composition are never altered.
        BigDecimal scale = maximum.multiply(new BigDecimal("1.10"));
        boolean negative = model.years().stream().anyMatch(p -> p.values().get(metric).signum() < 0);
        float zero = negative ? BOTTOM + height / 2 : BOTTOM;
        float span = negative ? height / 2 : height;

        drawBands(out, model.rothPeriods(), true);
        drawBands(out, model.rmdPeriods(), false);
        out.setLineDashPattern(new float[]{}, 0);
        for (int i = 0; i <= 4; i++) {
            float y = BOTTOM + height * i / 4;
            line(out, LEFT, y, LEFT + WIDTH, y, new Color(218, 224, 233), 0.5f);
            BigDecimal tick = scale.multiply(BigDecimal.valueOf(negative ? (i - 2) / 2.0 : i / 4.0));
            text(out, currency(tick), 36, y - 3, 8, false, INK);
        }
        int count = model.years().size();
        int step = Math.max(1, (count + 9) / 10);
        for (int i = 0; i < count; i++) {
            if (i % step != 0 && i != count - 1) continue;
            if (i == count - 1 && i > 0 && (i - 1) % step == 0) continue;
            int year = model.years().get(i).year();
            text(out, Integer.toString(year), x(year) - 10, BOTTOM - 17, 9, false, INK);
        }
        text(out, "Calendar Year", LEFT + WIDTH / 2 - 30, BOTTOM - 35, 10, false, INK);

        if (stacked) {
            BigDecimal[] lower = new BigDecimal[count];
            Arrays.fill(lower, BigDecimal.ZERO);
            for (var type : ProjectionAssetType.values()) {
                BigDecimal[] upper = new BigDecimal[count];
                for (int i = 0; i < count; i++) upper[i] = lower[i].add(model.years().get(i).composition().get(type));
                out.setNonStrokingColor(AREAS[type.ordinal()]);
                out.moveTo(x(model.years().getFirst().year()), y(lower[0], scale, zero, span));
                for (int i = 0; i < count; i++) out.lineTo(x(model.years().get(i).year()), y(upper[i], scale, zero, span));
                for (int i = count - 1; i >= 0; i--) out.lineTo(x(model.years().get(i).year()), y(lower[i], scale, zero, span));
                out.closePath(); out.fill();
                lower = upper;
            }
        }
        // Independent edge bands remain visible above filled areas, including overlap.
        for (var period : model.rothPeriods()) rect(out, x(period.firstYear() - .5), BOTTOM + height - 3,
                x(period.lastYear() + .5) - x(period.firstYear() - .5), 3, new Color(115, 94, 165));
        for (var period : model.rmdPeriods()) rect(out, x(period.firstYear() - .5), BOTTOM,
                x(period.lastYear() + .5) - x(period.firstYear() - .5), 3, new Color(63, 131, 101));
        // Labels occupy reserved lanes ABOVE the plot, never over the financial series.
        annotations(out, labels);
        out.setStrokingColor(ORANGE); out.setLineWidth(2.2f);
        out.setLineDashPattern(new float[]{}, 0);
        for (int i = 0; i < count; i++) {
            var point = model.years().get(i);
            float px = x(point.year()), py = y(point.values().get(metric), scale, zero, span);
            if (i == 0) out.moveTo(px, py); else out.lineTo(px, py);
        }
        out.stroke();
        for (var point : model.years()) rect(out, x(point.year()) - 1.8f,
                y(point.values().get(metric), scale, zero, span) - 1.8f, 3.6f, 3.6f, ORANGE);
        String legend = stacked ? "Total Investable Assets (orange)   |   Taxable / Cash (blue)   |   Tax-Deferred (purple)   |   Roth (green)"
                : metric + " (orange)";
        text(out, legend, 36, 73, 9, false, INK);
        text(out, "Roth Conversion periods: purple tint / upper edge   |   RMD periods: green tint / lower edge", 36, 57, 9, false, INK);
        if (metric == ProjectionChartMetric.INVESTABLE_ASSETS && !stacked)
            text(out, "Detailed asset composition is unavailable for this projection.", 36, 91, 9, false, INK);
        text(out, "Ending " + metric + ": " + currency(model.years().getLast().values().get(metric)), 36, 38, 10, true, INK);
    }

    private void drawBands(PDPageContentStream out, List<ProjectionChartModel.Period> periods, boolean roth) throws IOException {
        for (var period : periods) {
            // Both tints are translucent so neither hides the other in overlapping years.
            out.saveGraphicsState();
            var state = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
            state.setNonStrokingAlphaConstant(0.09f); out.setGraphicsStateParameters(state);
            rect(out, x(period.firstYear() - .5), BOTTOM, x(period.lastYear() + .5) - x(period.firstYear() - .5), height,
                    roth ? new Color(115, 94, 165) : new Color(63, 131, 101));
            out.restoreGraphicsState();
        }
    }

    private record Annotation(List<String> lines, float left, float top, float width, boolean bold) { }

    private List<Annotation> prepareLabels() throws IOException {
        List<Annotation> labels = new ArrayList<>();
        for (var claim : model.claims()) addLabel(labels, claim.year() + " | " + claim.person() + " claims at " + claim.age(), x(claim.year()), true);
        for (var period : model.rothPeriods()) addLabel(labels, "Roth Conversion" + (period.firstYear() == period.lastYear() ? " " : "s ") + range(period),
                (x(period.firstYear()) + x(period.lastYear())) / 2, false);
        for (var period : model.rmdPeriods()) addLabel(labels, "RMDs " + range(period),
                (x(period.firstYear()) + x(period.lastYear())) / 2, false);
        return List.copyOf(labels);
    }

    private void addLabel(List<Annotation> labels, String value, float center, boolean bold) throws IOException {
        float width = Math.min(210, Math.max(100, BOLD.getStringWidth(safe(value)) / 1000 * 8 + 10));
        float left = Math.max(36, Math.min(center - width / 2, 756 - width));
        var lines = wrap(value, width - 8, 8);
        float top = 493;
        boolean collision;
        do {
            collision = false;
            for (var prior : labels) {
                if (left < prior.left() + prior.width() + 4 && left + width + 4 > prior.left()
                        && top > prior.top() - prior.lines().size() * 10 - 9
                        && top - lines.size() * 10 - 9 < prior.top()) {
                    top = prior.top() - prior.lines().size() * 10 - 12;
                    collision = true; break;
                }
            }
        } while (collision);
        labels.add(new Annotation(lines, left, top, width, bold));
    }

    private void annotations(PDPageContentStream out, List<Annotation> labels) throws IOException {
        for (var claim : model.claims()) {
            out.setLineDashPattern(new float[]{4, 4}, 0);
            line(out, x(claim.year()), BOTTOM, x(claim.year()), BOTTOM + height, new Color(80, 91, 111), 1f);
            out.setLineDashPattern(new float[]{}, 0);
        }
        for (var label : labels) {
            rect(out, label.left(), label.top() - label.lines().size() * 10 - 3,
                    label.width(), label.lines().size() * 10 + 5, new Color(246, 248, 251));
            for (int i = 0; i < label.lines().size(); i++) text(out, label.lines().get(i), label.left() + 4,
                    label.top() - 8 - 10 * i, 8, label.bold(), INK);
        }
    }
    private String range(ProjectionChartModel.Period period) {
        return period.firstYear() == period.lastYear() ? "" + period.firstYear() : period.firstYear() + "-" + period.lastYear();
    }
    private float x(double year) { return LEFT + (float) ((year - first) / (last - first)) * WIDTH; }
    private static float y(BigDecimal value, BigDecimal scale, float zero, float span) {
        return zero + value.divide(scale, MathContext.DECIMAL128).floatValue() * span;
    }
}
