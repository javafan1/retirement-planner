package com.daviddunn.retirementplanner.app.export;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.ui.breakeven.BreakEvenPresentation;
import com.daviddunn.retirementplanner.ui.breakeven.BreakEvenInsightPresentation;
import org.apache.pdfbox.pdmodel.*;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static com.daviddunn.retirementplanner.app.export.PdfReportSupport.*;

/** Read-only report composition over completed results; no engines, controllers or JavaFX nodes. */
public final class BreakEvenPdfExporter {
    public void export(BreakEvenPdfReport report, Path file) throws IOException {
        Objects.requireNonNull(report); Objects.requireNonNull(file);
        try (var document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Break-Even Analysis");
            try (var writer = new TextPages(document, "Break-Even Analysis")) {
                writer.paragraph("Generated: " + LocalDate.now(), 9, false);
                writer.paragraph(BreakEvenPresentation.period(report.analysis()), 10, false);
                writer.paragraph(plan("BASELINE PLAN", report.analysis().baselineAssumptions()), 10, true);
                writer.paragraph(plan("CURRENT PLAN", report.analysis().currentAssumptions()), 10, true);
                writer.paragraph("BREAK-EVEN SUMMARY", 13, true);
                summaryCards(writer, report);
                writer.paragraph("BREAK-EVEN INSIGHT", 13, true);
                // Stable report headline uses the default UI metric; selection never changes PDF content.
                writer.paragraph(BreakEvenInsightPresentation.headline(report.analysis(), BreakEvenMetric.TOTAL_NET_WORTH), 10, false);
                snapshot(writer, report);
                String withdrawals = BreakEvenInsightPresentation.withdrawal(report.insight());
                if (!withdrawals.isEmpty()) writer.paragraph(withdrawals, 10, false);
            }
            for (var metric : report.charts()) new BreakEvenPdfChart(report, metric).appendTo(document);
            try (var writer = new TextPages(document, "Break-Even Insight — Details & Mortality")) {
                writer.paragraph(BreakEvenPresentation.period(report.analysis()), 10, false);
                writer.paragraph("Social Security compares cumulative benefits. Wealth metrics compare balances, which also reflect spending, taxes, investment growth and retained cash.", 10, false);
                writer.paragraph(report.analysis().comparisonEndYear() == null ? "No comparable years are available."
                        : "Observed differences, not causal attribution. Totals include only shared years through "
                        + report.insight().referenceYear() + ". Sustained means at or above Baseline through "
                        + report.analysis().comparisonEndYear() + "; no conclusion is made beyond the comparable period.", 10, false);
                writer.paragraph(BreakEvenInsightPresentation.snapshotHeading(report.analysis(), report.insight()), 11, true);
                for (var metric : BreakEvenMetric.values()) {
                    var point = report.insight().snapshot().get(metric);
                    if (point != null) writer.paragraph(values(BreakEvenPresentation.metricName(metric),
                            point.baselineValue(), point.currentValue(), point.difference()), 10, false);
                }
                writer.paragraph("OBSERVED DRIVER TOTALS", 12, true);
                for (var observation : report.insight().observations()) writer.paragraph(values(
                        BreakEvenInsightPresentation.driverName(observation.driver()), observation.baseline(), observation.current(), observation.difference()), 10, false);
                if (report.insight().observations().isEmpty()) writer.paragraph("Annual driver totals unavailable for this comparison.", 10, false);
                writer.paragraph("Gross withdrawals may include RMD cash later redeposited; they are not household consumption. Taxes and growth are observed totals, not an additive explanation of the asset gap.", 10, false);
                writer.paragraph("MORTALITY / SURVIVAL CONTEXT", 12, true);
                writer.paragraph(report.context().survivalExplanation(), 10, false);
                Integer ssYear = report.analysis().metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).sustainedBreakEvenYear();
                if (ssYear != null && report.context().survival().containsKey(ssYear)) writer.paragraph("At SS break-even (" + ssYear + "): "
                        + BreakEvenPresentation.probability(report.context().survival().get(ssYear)) + " modeled probability at least one alive.", 10, false);
                writer.paragraph("Financial differences are deterministic and are not multiplied by survival probabilities.", 10, true);
            }
            pageNumbers(document);
            document.save(file.toFile());
        }
    }

    private static String plan(String heading, BreakEvenPlanSummary plan) {
        return heading + ": " + plan.primary().name() + " — Social Security Claiming Age: "
                + BreakEvenPresentation.age(plan.primary().retirementClaimingAge()) + "  ·  "
                + plan.spouse().name() + " — Social Security Claiming Age: " + BreakEvenPresentation.age(plan.spouse().retirementClaimingAge());
    }
    private static String values(String title, java.math.BigDecimal baseline, java.math.BigDecimal current, java.math.BigDecimal difference) {
        return title + " — Baseline: " + BreakEvenPresentation.money(baseline) + "; Current: "
                + BreakEvenPresentation.money(current) + "; Difference: " + BreakEvenPresentation.signedMoney(difference);
    }
    private static void snapshot(TextPages writer, BreakEvenPdfReport report) throws IOException {
        if (report.insight().snapshot().isEmpty()) return;
        writer.paragraph(BreakEvenInsightPresentation.snapshotHeading(report.analysis(), report.insight()), 10, true);
        List<String> values = new ArrayList<>();
        for (var metric : List.of(BreakEvenMetric.INVESTABLE_ASSETS, BreakEvenMetric.TOTAL_NET_WORTH, BreakEvenMetric.AFTER_TAX_ESTATE)) {
            var point = report.insight().snapshot().get(metric);
            if (point != null) values.add(BreakEvenPresentation.metricName(metric) + ": " + BreakEvenPresentation.signedMoney(point.difference()));
        }
        writer.paragraph(String.join("  ·  ", values), 10, false);
    }

    private static void summaryCards(TextPages writer, BreakEvenPdfReport report) throws IOException {
        float width = 171, gap = 12, height = 135;
        record Card(java.util.List<String> title, java.util.List<String> value, java.util.List<String> detail, float valueSize) { }
        List<Card> cards = new ArrayList<>();
        for (var metric : report.charts()) {
            float valueSize = metric.status() == BreakEvenStatus.BREAK_EVEN_REACHED ? 26 : 16;
            var title = wrap(BreakEvenPresentation.summaryName(metric.metric()), width - 16, 10);
            var value = wrap(BreakEvenPresentation.cardValue(metric), width - 16, valueSize);
            String detail = BreakEvenPresentation.cardDetail(report.analysis(), metric);
            var survival = metric.sustainedBreakEvenYear() == null ? null : report.context().survival().get(metric.sustainedBreakEvenYear());
            if (survival != null) detail = BreakEvenPresentation.probability(survival) + " chance at least one alive\n" + detail;
            List<String> lines = new ArrayList<>();
            for (String line : detail.split("\n")) lines.addAll(wrap(line, width - 16, 9));
            height = Math.max(height, 24 + title.size() * 12 + value.size() * (valueSize + 3) + lines.size() * 11);
            cards.add(new Card(title, value, lines, valueSize));
        }
        writer.ensure(height + 10);
        for (int i = 0; i < cards.size(); i++) {
            float x = 36 + i * (width + gap), y = writer.y - 12;
            rect(writer.out, x, writer.y - height, width, height, new Color(241, 245, 249));
            var card = cards.get(i);
            for (String line : card.title()) { text(writer.out, line, x + 8, y, 10, true, INK); y -= 12; }
            y -= card.valueSize();
            for (String line : card.value()) { text(writer.out, line, x + 8, y, card.valueSize(), true, INK); y -= card.valueSize() + 3; }
            y += card.valueSize() - 8;
            for (String line : card.detail()) { text(writer.out, line, x + 8, y, 9, false, INK); y -= 11; }
        }
        writer.y -= height + 12;
    }

    private static final class TextPages implements AutoCloseable {
        private final PDDocument document;
        private final String heading;
        private PDPageContentStream out;
        private float y;
        TextPages(PDDocument document, String heading) throws IOException { this.document = document; this.heading = heading; newPage(); }
        private void newPage() throws IOException {
            if (out != null) out.close();
            PDPage page = new PDPage(LANDSCAPE); document.addPage(page);
            out = new PDPageContentStream(document, page);
            text(out, heading, 36, 574, 19, true, INK); y = 545;
        }
        void ensure(float required) throws IOException { if (y - required < 38) newPage(); }
        void paragraph(String value, float size, boolean bold) throws IOException {
            for (String paragraph : value.split("\n")) for (String line : wrap(paragraph, 720, size)) {
                ensure(size + 5); text(out, line, 36, y, size, bold, INK); y -= size + 4;
            }
            y -= 5;
        }
        @Override public void close() throws IOException { if (out != null) out.close(); }
    }
}