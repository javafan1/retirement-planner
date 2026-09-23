package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakEvenPdfExportTest {
    @BeforeAll static void startFx() throws Exception { BreakEvenCompactLayoutTest.startFx(); }

    @Test void allChartsExportWithoutChangingSelectionOrCompletedInputsAndProducePreview() throws Exception {
        var baseline = BreakEvenCompactLayoutTest.snapshot(62);
        var current = BreakEvenCompactLayoutTest.snapshot(65);
        var result = new BreakEvenAnalyzer().analyze(baseline, current);
        var insight = new BreakEvenInsightService().prepare(result, baseline.years(), current.years());
        var context = new com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory().create(result,
                com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(LocalDate.of(2027, 1, 1)));
        Path folder = Path.of("target/break-even-pdf-preview"); Files.createDirectories(folder);
        FutureTask<Void> task = new FutureTask<>(() -> {
            var dialog = new BreakEvenAnalysisDialog(result, context, insight); dialog.show();
            try {
                var pane = dialog.getDialogPane(); pane.applyCss(); pane.layout();
                assertNotNull(pane.lookup("#break-even-export-pdf"));
                assertNotNull(pane.lookupButton(ButtonType.CLOSE));
                var scroll = (ScrollPane) pane.getContent();
                @SuppressWarnings("unchecked") var selector = (ComboBox<BreakEvenMetric>) pane.lookup("#break-even-metric");
                var details = pane.lookup("#break-even-insight-details");
                String firstText = null;
                for (var metric : List.of(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY, BreakEvenMetric.TOTAL_NET_WORTH, BreakEvenMetric.AFTER_TAX_ESTATE)) {
                    selector.setValue(metric);
                    double position = scroll.getVvalue(); boolean expanded = details.isVisible();
                    var report = dialog.preparePdfReport();
                    assertSame(result, report.analysis()); assertSame(insight, report.insight()); assertSame(context, report.context());
                    assertEquals(List.of(BreakEvenMetric.values()), report.charts().stream().map(BreakEvenMetricResult::metric).toList());
                    assertEquals(3, report.context().events().size()); assertEquals(30, report.context().survival().size());
                    assertEquals(3, report.insight().observations().size());
                    Path file = folder.resolve("break-even-analysis.pdf"); dialog.exportPdf(file);
                    assertEquals(metric, selector.getValue()); assertEquals(position, scroll.getVvalue()); assertEquals(expanded, details.isVisible());
                    try (var pdf = Loader.loadPDF(file.toFile())) {
                        String text = new PDFTextStripper().getText(pdf);
                        if (firstText != null) assertEquals(firstText, text); firstText = text;
                        assertEquals(6, pdf.getNumberOfPages());
                        for (var chart : report.charts()) {
                            assertTrue(text.contains(BreakEvenPresentation.metricName(chart.metric()) + " Difference"));
                            assertTrue(text.contains("Break-even " + chart.sustainedBreakEvenYear()));
                            var point = BreakEvenPresentation.sustainedPoint(chart);
                            assertTrue(text.contains(BreakEvenPresentation.compactAges(point, result.currentAssumptions())));
                            assertTrue(text.contains(BreakEvenPresentation.probability(context.survival().get(point.year())) + " chance at least one alive"));
                        }
                        for (String required : List.of("BASELINE PLAN", "CURRENT PLAN", "Social Security Claiming Age: 62", "Social Security Claiming Age: 65", "Both plans", "Probability at least one spouse alive", "OBSERVED DRIVER TOTALS", "MORTALITY / SURVIVAL CONTEXT", "2027–2056")) assertTrue(text.contains(required), required);
                        assertFalse(text.contains("Show details"));
                        for (var page : pdf.getPages()) for (var name : page.getResources().getXObjectNames())
                            assertFalse(page.getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject);
                        if (metric == BreakEvenMetric.AFTER_TAX_ESTATE) {
                            var renderer = new PDFRenderer(pdf);
                            for (int i = 0; i < pdf.getNumberOfPages(); i++) javax.imageio.ImageIO.write(renderer.renderImageWithDPI(i, 110), "png", folder.resolve(String.format("page-%02d.png", i + 1)).toFile());
                            Files.writeString(folder.resolve("report-text.txt"), text);
                        }
                    }
                }
            } finally { dialog.close(); }
            return null;
        });
        Platform.runLater(task); task.get(90, TimeUnit.SECONDS);
    }

    @Test void existingStatesAndHorizonLimitsRemainAuthoritative() throws Exception {
        long[][] cases = {{-100,-50,10,40}, {-100,-50,-20}, {10,20,30}, {0,0,0}, {-100,10,-20,30,40}, {-100,10,-20}, {}};
        Path folder = Path.of("target/break-even-pdf-preview/states"); Files.createDirectories(folder);
        for (int n = 0; n < cases.length; n++) {
            var baseline = new ArrayList<com.daviddunn.retirementplanner.domain.projection.ProjectionYear>();
            var current = new ArrayList<com.daviddunn.retirementplanner.domain.projection.ProjectionYear>();
            for (int i = 0; i < cases[n].length; i++) {
                baseline.add(ProjectionYearBuilder.aProjectionYear().withCalendarYear(2027+i).withEndingInvestableAssets(1000).build());
                current.add(ProjectionYearBuilder.aProjectionYear().withCalendarYear(2027+i).withEndingInvestableAssets(1000+cases[n][i]).build());
            }
            // A different horizon must not add fabricated comparison points.
            baseline.add(ProjectionYearBuilder.aProjectionYear().withCalendarYear(2056).withEndingInvestableAssets(1000).build());
            var summary = new BreakEvenPlanSummary(new BreakEvenPlanSummary.PersonSummary("Alex", LocalDate.of(1960,1,1),70), new BreakEvenPlanSummary.PersonSummary("Sam", LocalDate.of(1962,1,1),62));
            var result = new BreakEvenAnalyzer().analyze(new BreakEvenProjectionSnapshot(baseline,List.of(),summary), new BreakEvenProjectionSnapshot(current,List.of(),summary));
            var report = new BreakEvenPdfReport(result, BreakEvenContext.unavailable(), new BreakEvenInsightService().prepare(result));
            Path file = folder.resolve("state-" + n + ".pdf"); new BreakEvenPdfExporter().export(report,file);
            try (var pdf = Loader.loadPDF(file.toFile())) {
                String text = new PDFTextStripper().getText(pdf);
                assertFalse(text.contains("null"));
                assertTrue(text.contains("Planning horizons differ"));
                var metric = result.metrics().get(BreakEvenMetric.INVESTABLE_ASSETS);
                assertTrue(text.contains(BreakEvenPresentation.cardValue(metric)) || text.replaceAll("\\s+", " ").contains(BreakEvenPresentation.cardValue(metric)));
                var stripper = new PDFTextStripper(); stripper.setStartPage(3); stripper.setEndPage(3);
                String chart = stripper.getText(pdf);
                if (metric.sustainedBreakEvenYear() == null) assertFalse(chart.contains("Break-even 20"));
                else assertTrue(chart.contains("Break-even " + metric.sustainedBreakEvenYear()));
                if (n == 4) { assertEquals(2030,metric.sustainedBreakEvenYear()); assertTrue(chart.contains("First crossover: 2028")); }
                assertEquals(cases[n].length, metric.years().size());
            }
        }
    }
}