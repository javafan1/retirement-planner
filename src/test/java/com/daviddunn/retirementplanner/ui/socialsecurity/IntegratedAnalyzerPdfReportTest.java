package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegratedAnalyzerPdfReportTest {
    @TempDir Path directory;

    static IntegratedReportContext context(boolean weighted) {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        return IntegratedReportContext.capture(plan, weighted, new SocialSecurityAnalyzerInputSummary.AnalyzerValues(
                new BigDecimal("0.80"), BigDecimal.ONE, LocalDate.of(2026, 7, 1), LocalDate.of(2025, 1, 1), new BigDecimal("0.01")),
                CurrentStrategyBaseline.fromPlan(plan));
    }

    static ExhaustiveIntegratedSearchPresentation deterministic() {
        List<IntegratedSocialSecurityCompleteStrategySearchEntry> entries = new ArrayList<>();
        for (int spouse = 62; spouse <= 70; spouse++) for (int primary = 62; primary <= 70; primary++) {
            int id = entries.size() + 1;
            entries.add(DeterministicHeatMapAdapterTest.entry(id, primary, spouse, 67,
                    Integer.toString(1000000 - id * 1000), id, Integer.toString(id * -1000)));
        }
        return DeterministicHeatMapAdapterTest.presentation(entries, 81);
    }

    static IntegratedAnalyzerReport deterministicReport() {
        var model = deterministic();
        var map = DeterministicHeatMapAdapter.from(model);
        return IntegratedAnalyzerReportAdapter.deterministic(context(false), model, map,
                ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL, map.cell(70, 70), model.result().entries().getLast(),
                model.groups(), "SS-only recommendation cross-reference: run current SS-only analysis first.");
    }

    static LongevityWeightedIntegratedPresentation weighted() {
        var entries = List.of(LongevityWeightedIntegratedPresentationTest.entry(1, "100.004", 1),
                LongevityWeightedIntegratedPresentationTest.entry(2, "90.003", 2));
        return LongevityWeightedIntegratedPresentationTest.model(entries, Optional.empty());
    }

    static IntegratedAnalyzerReport weightedReport() {
        var model = weighted();
        var map = LongevityWeightedHeatMapAdapter.from(model);
        return IntegratedAnalyzerReportAdapter.weighted(context(true), model, map,
                ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE, map.cell(62, 62), model.result().orderedEntries().getLast(),
                model.result().orderedEntries(), List.of(), null);
    }

    @Test void deterministicCapturesExactSelectionMetricColorsEveryCellAndAllRows() {
        var report = deterministicReport();
        assertEquals("Deterministic Analysis", report.analysisType());
        assertEquals("Integrated Retirement Plan Social Security Analysis", IntegratedAnalyzerReport.TITLE);
        assertEquals(81, report.heatMap().cells().size());
        assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_OPTIMAL.toString(), report.heatMap().metric());
        var selected = report.heatMap().cells().getLast();
        assertTrue(selected.selected());
        assertFalse(selected.optimal());
        assertEquals("-" + com.daviddunn.retirementplanner.ui.util.UIFormatters.money(new BigDecimal("80000")), selected.value());
        assertEquals(ClaimingHeatMapPalette.SUBSTANTIALLY_WORSE, selected.tier());
        assertEquals("#e9a58d", selected.tier().color);
        assertTrue(report.heatMap().cells().getFirst().optimal());
        assertEquals(1, report.heatMap().cells().stream().filter(IntegratedAnalyzerReport.Cell::selected).count());
        assertEquals(81, report.rankedTables().getFirst().rows().size());
        assertEquals("81", report.rankedTables().getFirst().rows().getLast().getFirst());
        assertTrue(String.join("\n", report.selectedSummary()).contains("Rank: 81"));
        String details = report.results().toString();
        assertTrue(details.contains("Lifetime") || details.contains("Household Social Security"));
        assertTrue(details.contains("Roth conversions"));
        assertTrue(details.contains("Current Plan Financial Metrics"));
        assertTrue(details.contains("Valuation date: not applicable"));
        assertTrue(report.assumptions().toString().contains("Investment return assumptions"));
        assertFalse(report.assumptions().toString().contains("mortality category"));
        assertThrows(UnsupportedOperationException.class, () -> report.rankedTables().getFirst().rows().clear());
    }

    @Test void weightedIncludesFrozenPersonCategoriesFactorsAndAllSupportingResults() {
        var report = weightedReport();
        assertEquals("Longevity-Weighted Analysis", report.analysisType());
        assertEquals(81, report.heatMap().cells().size());
        String text = report.results().toString();
        for (String phrase : List.of("Primary mortality category: MALE; factor: 0.80", "Spouse mortality category: FEMALE",
                "Mortality conditioning date", "Timing convention", "Independent mortality", "Valuation date",
                "real discount rate", "Minimum nominal scenario estate", "Evaluated probability coverage", "Compare Analysis Outcomes",
                "Current Strategy Baseline", "Highest Longevity-Weighted Tested Strategy", "Computation and Coverage")) {
            assertTrue(text.contains(phrase), phrase);
        }
        assertTrue(report.selectedSummary().toString().contains("Rank: 2"));
        assertTrue(report.selectedSummary().toString().contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(new BigDecimal("90.003"))));
        assertFalse(report.selectedSummary().toString().contains("Optimal (highest tested)"));
        assertTrue(report.heatMap().explanation().contains("Colors: % of optimal"));
        assertEquals(2, report.rankedTables().getFirst().rows().size());
        assertEquals("2", report.rankedTables().getFirst().rows().getLast().getFirst());
    }

    @Test void reportContextIsImmutableAndDoesNotReadLaterPersonOrPlanEdits() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.FEMALE);
        var values = new SocialSecurityAnalyzerInputSummary.AnalyzerValues(BigDecimal.ONE, BigDecimal.ONE,
                LocalDate.of(2026, 7, 1), LocalDate.of(2025, 1, 1), BigDecimal.ZERO);
        var frozen = IntegratedReportContext.capture(plan, true, values, CurrentStrategyBaseline.fromPlan(plan));
        plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
        plan.getHousehold().getPrimaryPerson().setFirstName("Changed after analysis");
        assertFalse(frozen.primary().contains("Changed"));
        assertTrue(frozen.assumptions().toString().contains("Primary mortality category: female [Person information]"));
    }

    @Test void tableOrderIsTheCapturedScreenOrderAndDoesNotReRankOrModifyResults() {
        var model = deterministic();
        var before = model.result().entries();
        var reversed = model.groups().reversed();
        var map = DeterministicHeatMapAdapter.from(model);
        var report = IntegratedAnalyzerReportAdapter.deterministic(context(false), model, map,
                ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE, map.cell(62, 62), model.result().entries().getFirst(), reversed, "reference");
        assertEquals("81", report.rankedTables().getFirst().rows().getFirst().getFirst());
        assertEquals("1", report.rankedTables().getFirst().rows().getLast().getFirst());
        assertSame(before, model.result().entries());
        assertTrue(report.selectedSummary().toString().contains("Optimal (highest tested)"));
    }

    @Test void writesBothRealPdfsWithVectorHeatMapAndRepeatingMultipageTableHeaders() throws Exception {
        for (var report : List.of(deterministicReport(), weightedReport())) {
            Path file = directory.resolve(report.analysisType() + ".pdf");
            new IntegratedAnalyzerPdfExporter().export(report, file);
            assertTrue(Files.size(file) > 5000);
            try (var document = Loader.loadPDF(file.toFile())) {
                assertTrue(document.getNumberOfPages() >= 5);
                assertTrue(document.getNumberOfPages() < 30);
                assertEquals(792, document.getPage(0).getMediaBox().getWidth());
                var stripper = new PDFTextStripper();
                var text = stripper.getText(document);
                assertTrue(text.contains("Planning Horizon - end date"));
                assertTrue(text.contains("Planning Horizon - calendar years"));
                assertTrue(text.contains("Survivor Benefit Claiming Age"));
                assertFalse(text.contains("Survivor Claiming Age"));
                assertFalse(text.contains("Configured projection length"));
                for (String phrase : List.of(IntegratedAnalyzerReport.TITLE, report.analysisType(), "SELECTED STRATEGY",
                        "Primary Claiming Age", "Essentially optimal", "Substantially worse", "Ranked Strategies - Complete Elections",
                        "Result-Time Plan and Analysis Assumptions")) assertTrue(text.contains(phrase), phrase);
                int electionsPages = 0;
                for (int page = 1; page <= document.getNumberOfPages(); page++) {
                    stripper.setStartPage(page); stripper.setEndPage(page);
                    var pageText = stripper.getText(document);
                    if (pageText.contains("Ranked Strategies - Complete Elections")) {
                        assertTrue(pageText.contains("Primary Retirement"));
                        // The longer column title wraps across lines in the PDF header.
                        assertTrue(pageText.contains("Spouse Survivor"));
                        assertTrue(pageText.contains("Benefit"));
                        assertTrue(pageText.contains("Claiming Age"));
                        electionsPages++;
                    }
                }
                if (report.analysisType().startsWith("Deterministic")) assertTrue(electionsPages > 1);
                assertFalse(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(), "Heat map must be vector/text, not a screenshot");
                var preview = new PDFRenderer(document).renderImageWithDPI(0, 96);
                int teal = java.awt.Color.decode(ClaimingHeatMapPalette.ESSENTIALLY_OPTIMAL.color).getRGB();
                int count = 0;
                for (int y = 0; y < preview.getHeight(); y++) for (int x = 0; x < preview.getWidth(); x++) {
                    if (preview.getRGB(x, y) == teal) count++;
                }
                assertTrue(count > 100, "Shared tier color is present in the rendered PDF");
                if (Boolean.getBoolean("pdf.preview")) {
                    String name = report.analysisType().startsWith("Deterministic") ? "deterministic" : "weighted";
                    javax.imageio.ImageIO.write(preview, "png", Path.of("target", name + "-pdf-preview.png").toFile());
                    Files.copy(file, Path.of("target", name + "-analysis-sample.pdf"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    @Test void invalidDestinationFailsWithoutMutatingReport() {
        var report = deterministicReport();
        var cells = report.heatMap().cells();
        assertThrows(java.io.IOException.class, () -> new IntegratedAnalyzerPdfExporter().export(report,
                directory.resolve("missing-parent/report.pdf")));
        assertSame(cells, report.heatMap().cells());
    }
}
