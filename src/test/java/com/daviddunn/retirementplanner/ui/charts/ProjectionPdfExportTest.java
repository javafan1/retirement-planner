package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.noninvestable.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import javafx.scene.control.ComboBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionPdfExportTest {
    @BeforeAll static void startFx() throws Exception { ProjectionChartViewTest.startFx(); }

    @Test void allChartsUsePreparedDataAndExportIgnoresMetricSelectionWithoutChangingCsv() throws Exception {
        ProjectionChartViewTest.fx(() -> {
            try {
                var plan = ProjectionChartReconciliationTest.roundingPlan();
                var primary = plan.getHousehold().getPrimaryPerson();
                var spouse = plan.getHousehold().getSpouse();
                primary.setFirstName("Alex"); spouse.setFirstName("Sam");
                primary.addIncomeSource(new SocialSecurityIncome("Alex SS", AccountOwnership.PRIMARY,
                        LocalDate.of(2033, 6, 4), null, new BigDecimal("3000"), 70, BigDecimal.ZERO, 2027));
                spouse.addIncomeSource(new SocialSecurityIncome("Sam SS", AccountOwnership.SPOUSE,
                        LocalDate.of(2027, 2, 28), null, new BigDecimal("2000"), 62, BigDecimal.ZERO, 2027));
                plan.addNonInvestableAsset(new NonInvestableAsset("Home equity", new BigDecimal("500000"), new BigDecimal("0.02")));
                plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(true, 2027,
                        new BigDecimal("75000"), com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.NEVER,
                        com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                        com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ANNUAL));
                // Only fixture setup runs a projection. Both exports use these exact results.
                var projection = new ProjectionEngine().project(plan);
                var non = new NonInvestableAssetProjectionService().project(plan.getNonInvestableAssets(), 2027, 2056);
                var summary = new ResultsSummaryView(new ApplicationController());
                summary.load(plan, projection, non);
                new javafx.scene.Scene(summary, 1400, 900); summary.applyCss(); summary.layout();
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                String before = mapper.writeValueAsString(plan);
                Path folder = Path.of("target", "projection-pdf-preview"); Files.createDirectories(folder);
                var csv = new ProjectionCsvExporter();
                csv.export(projection, non, folder.resolve("before.csv"));
                var selector = (ComboBox<ProjectionChartMetric>) summary.lookup("#projection-chart-metric");
                selector.setValue(ProjectionChartMetric.INVESTABLE_ASSETS);
                var a = summary.prepareProjectionPdfReport();
                assertTrue(a.charts().years().stream().allMatch(ProjectionChartModel.Point::compositionComplete));
                assertEquals(2, a.charts().claims().size());
                assertFalse(a.charts().rothPeriods().isEmpty()); assertFalse(a.charts().rmdPeriods().isEmpty());
                assertTrue(a.charts().rothPeriods().getFirst().lastYear() >= a.charts().rmdPeriods().getFirst().firstYear());
                assertEquals(2027, a.charts().years().getFirst().year()); assertEquals(2056, a.charts().years().getLast().year());
                var direct = ProjectionChartModel.from(projection.getYears(), non,
                        com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.from(plan.getHousehold()), plan.getAccountPortfolio().getAccounts());
                assertEquals(direct, a.charts());
                assertTrue(a.averageEffectiveTaxRate().signum() > 0);
                assertEquals(projection.getYears().getLast().getAfterTaxEstateValue().add(non.getLast().getTotalValue()), a.afterTaxEstateHeirValue());
                var exporter = new ProjectionPdfExporter();
                exporter.export(plan, projection, non, a, folder.resolve("retirement-projection.pdf"));
                selector.setValue(ProjectionChartMetric.SOCIAL_SECURITY);
                var b = summary.prepareProjectionPdfReport();
                assertEquals(a, b);
                exporter.export(plan, projection, non, b, folder.resolve("other-selection.pdf"));
                csv.export(projection, non, folder.resolve("after.csv"));
                assertArrayEquals(Files.readAllBytes(folder.resolve("before.csv")), Files.readAllBytes(folder.resolve("after.csv")));
                assertEquals(before, mapper.writeValueAsString(plan));
                try (var doc = Loader.loadPDF(folder.resolve("retirement-projection.pdf").toFile());
                     var second = Loader.loadPDF(folder.resolve("other-selection.pdf").toFile())) {
                    String text = new PDFTextStripper().getText(doc);
                    assertEquals(text, new PDFTextStripper().getText(second));
                    for (String heading : List.of("RETIREMENT PLAN PROJECTION REPORT", "EXECUTIVE RESULTS SUMMARY", "AVERAGE EFFECTIVE TAX RATE",
                            "PROJECTION SUMMARY", "KEY ASSUMPTIONS", "Social Security Claiming Age", "Roth Strategy", "Filing Status")) assertTrue(text.contains(heading), heading);
                    for (var metric : ProjectionChartMetric.values()) assertTrue(text.contains(metric.toString()), metric.toString());
                    assertTrue(text.contains("Taxable / Cash")); assertTrue(text.contains("Total Investable Assets (orange)"));
                    assertTrue(text.contains("Sam claims at 62")); assertTrue(text.contains("Alex claims at 70"));
                    assertTrue(text.contains("Roth Conversions")); assertTrue(text.contains("RMDs"));
                    assertFalse(text.contains("Detailed asset composition is unavailable"));
                    assertEquals(13, text.split("PROJECTION CHARTS", -1).length - 1);
                    var renderer = new PDFRenderer(doc);
                    for (int page = 0; page < doc.getNumberOfPages(); page++) {
                        assertEquals(792, doc.getPage(page).getMediaBox().getWidth());
                        var extract = new PDFTextStripper(); extract.setStartPage(page + 1); extract.setEndPage(page + 1);
                        assertTrue(extract.getText(doc).length() > 80, "No blank page " + page);
                        var resources = doc.getPage(page).getResources();
                        for (var name : resources.getXObjectNames()) assertFalse(resources.getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject);
                        javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 110), "png", folder.resolve(String.format("page-%02d.png", page + 1)).toFile());
                    }
                    // Contact sheets facilitate inspection of every page, not only sample pages.
                    for (int start = 0; start < doc.getNumberOfPages(); start += 6) {
                        var sheet = new java.awt.image.BufferedImage(1584, 3 * 612, java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var graphics = sheet.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                        for (int index = start; index < Math.min(start + 6, doc.getNumberOfPages()); index++) {
                            var image = renderer.renderImageWithDPI(index, 72);
                            graphics.drawImage(image, ((index - start) % 2) * 792, ((index - start) / 2) * 612, null);
                        }
                        graphics.dispose(); javax.imageio.ImageIO.write(sheet, "png", folder.resolve("contact-" + start + ".png").toFile());
                    }
                }
            } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
        });
    }
    @Test void materialCompositionFailureKeepsTotalAndExistingBaselineValues() throws Exception {
        var plan = ProjectionChartReconciliationTest.roundingPlan();
        var year = com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder.aProjectionYear()
                .withCalendarYear(2027).withEndingInvestableAssets(100).build();
        var projection = new Projection(); projection.addYear(year);
        var charts = ProjectionChartModel.from(projection.getYears(), List.of(), null, plan.getAccountPortfolio().getAccounts());
        var value = new BigDecimal("1234.56");
        var comparison = new com.daviddunn.retirementplanner.domain.baseline.ProjectionComparison(2027,
                value, value, value, value, value, value, value, value, value, value, value, value, value, value);
        var report = new ProjectionPdfReport("Fallback and Baseline fixture", charts, BigDecimal.ZERO, value, comparison);
        Path file = Path.of("target", "projection-pdf-preview", "fallback-baseline.pdf");
        Files.createDirectories(file.getParent());
        new ProjectionPdfExporter().export(plan, projection, List.of(), report, file);
        try (var doc = Loader.loadPDF(file.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Detailed asset composition is unavailable for this projection."));
            assertTrue(text.contains("BASELINE COMPARISON - 2027"));
            assertTrue(text.contains("$1,234.56"));
            assertTrue(text.contains("Investable After-Tax Estate"));
            assertEquals(13, text.split("PROJECTION CHARTS", -1).length - 1);
            javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png",
                    file.resolveSibling("baseline-summary.png").toFile());
        }
    }}