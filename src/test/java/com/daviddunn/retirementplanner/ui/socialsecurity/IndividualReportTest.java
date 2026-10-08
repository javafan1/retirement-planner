package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.*;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndividualReportTest {
    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        var task = new java.util.concurrent.FutureTask<T>(action);
        javafx.application.Platform.runLater(task); return task.get(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test void uiCreatedReloadedPlanExportsCompletedAnalysesWithoutRerunsAndRejectsStaleInputs() throws Exception {
        var startup = new java.util.concurrent.FutureTask<Void>(() -> { javafx.application.Platform.setImplicitExit(false); return null; });
        try { javafx.application.Platform.startup(startup); } catch (IllegalStateException started) { javafx.application.Platform.runLater(startup); }
        startup.get(20, java.util.concurrent.TimeUnit.SECONDS);
        var controller = fx(() -> {
            var c = com.daviddunn.retirementplanner.ui.views.SinglePersonHouseholdUiTest.uiPlan();
            var path = java.nio.file.Path.of("target/stage4c-primary-only.json");
            c.saveAs(path); c.newPlan(); c.open(path);
            assertFalse(c.getCurrentPlan().getHousehold().hasSpouse());
            return c;
        });
        try (var coordinator = new com.daviddunn.retirementplanner.app.socialsecurity.SocialSecurityAnalysisJobCoordinator()) {
            var jobs = fx(() -> new SocialSecurityAnalyzerJobController(coordinator));
            try {
                var view = fx(() -> new SinglePersonIntegratedView(controller.getCurrentPlan(), jobs));
                fx(() -> { view.start(); return null; });
                await(jobs);
                fx(() -> {
                    assertTrue(view.canExportPdf(), view.status.getText());
                    long generation = jobs.generation();
                    view.table.getSelectionModel().select(4);
                    var report = view.preparePdfReport();
                    var path = com.daviddunn.retirementplanner.Stage4cPdfChecks.path("ui-individual-deterministic");
                    new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter().export(report, path);
                    com.daviddunn.retirementplanner.Stage4cPdfChecks.inspect(path);
                    assertEquals(generation, jobs.generation());
                    view.deathYear.setText("2060"); assertFalse(view.canExportPdf());
                    assertThrows(IllegalStateException.class, view::preparePdfReport);
                    assertTrue(jobs.generation() >= generation);
                    return null;
                });
                for (var mode : com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis.Mode.values()) {
                    var mortality = mode == com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis.Mode.INTEGRATED ? view.weightedView : view.ssView;
                    fx(() -> { mortality.start(); return null; }); await(jobs);
                    fx(() -> {
                        assertTrue(mortality.canExportPdf(), mortality.status.getText());
                        long generation = jobs.generation();
                        mortality.table.getSelectionModel().select(3);
                        var report = mortality.preparePdfReport();
                        var path = com.daviddunn.retirementplanner.Stage4cPdfChecks.path("ui-individual-" + mode);
                        var exporter = new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter();
                        exporter.export(report, path); exporter.export(report, path.resolveSibling("repeat-" + path.getFileName()));
                        com.daviddunn.retirementplanner.Stage4cPdfChecks.inspect(path);
                        assertEquals(generation, jobs.generation());
                        var frozen = report.assumptions(); mortality.factor.setText("0.8");
                        assertFalse(mortality.canExportPdf()); assertEquals(frozen, report.assumptions());
                        assertThrows(IllegalStateException.class, mortality::preparePdfReport);
                        return null;
                    });
                }
            } finally { fx(() -> { jobs.close(); return null; }); }
        }
    }

    private static void await(SocialSecurityAnalyzerJobController jobs) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        while (fx(jobs::state) != SocialSecurityAnalyzerJobController.State.IDLE && System.nanoTime() < end) Thread.sleep(20);
        assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, fx(jobs::state));
    }
    @Test void completedIndividualReportsRenderAllThreeMetricsAndPreserveCoupleReports() throws Exception {
        var plan = com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest.plan();
        var context = IndividualReportContext.capture(plan);
        var deterministic = com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonIntegratedAnalysis.calculate(
                com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        var comparison = deterministic.breakEvenByClaimingAge().get(70);
        var breakEvenPath = com.daviddunn.retirementplanner.Stage4cPdfChecks.path("single-break-even");
        new com.daviddunn.retirementplanner.app.export.BreakEvenPdfExporter().export(
                new com.daviddunn.retirementplanner.app.export.BreakEvenPdfReport(comparison,
                        com.daviddunn.retirementplanner.domain.breakeven.BreakEvenContext.unavailable(),
                        new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(comparison)), breakEvenPath);
        com.daviddunn.retirementplanner.Stage4cPdfChecks.inspect(breakEvenPath);
        var reports = new LinkedHashMap<String, com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport>();
        reports.put("individual-deterministic", IndividualAnalyzerReportAdapter.deterministic(context, deterministic, 67));
        for (var mode : com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysis.Mode.values()) {
            var result = com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest.run(plan,
                    com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest.mortality(plan, "1"), "0.01", mode);
            var report = IndividualAnalyzerReportAdapter.mortality(context, result, 67);
            var ages = ((Individual) report.visualization()).ages();
            assertTrue(ages.stream().filter(IndividualAge::optimal).allMatch(a -> a.value().equals(
                    com.daviddunn.retirementplanner.ui.util.UIFormatters.money(result.ranked().getFirst().expectedPresentValue()))));
            reports.put("individual-" + mode, report);
        }
        reports.put("couple-deterministic", IntegratedAnalyzerPdfReportTest.deterministicReport());
        reports.put("couple-weighted", IntegratedAnalyzerPdfReportTest.weightedReport());
        var root = java.nio.file.Path.of("target/stage4c-preview"); java.nio.file.Files.createDirectories(root);
        for (var entry : reports.entrySet()) {
            var path = root.resolve(entry.getKey() + ".pdf");
            new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter().export(entry.getValue(), path);
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
                java.nio.file.Files.writeString(path.resolveSibling(path.getFileName() + ".pages"), "" + pdf.getNumberOfPages());
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                if (entry.getKey().startsWith("individual")) {
                    assertFalse(text.toLowerCase().contains("spouse"), text);
                    assertFalse(text.toLowerCase().contains("survivor"), text);
                    assertTrue(text.contains("PRIMARY CLAIMING-AGE ALTERNATIVES"));
                    for (int age = 62; age <= 70; age++) assertTrue(text.contains("" + age));
                    assertTrue(text.contains("Primary claiming age 67"));
                }
                if (Boolean.getBoolean("single.stage4c.preview")) {
                    var renderer = new org.apache.pdfbox.rendering.PDFRenderer(pdf);
                    for (int page = 0; page < pdf.getNumberOfPages(); page++) javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 100),
                            "png", root.resolve(entry.getKey() + "-" + (page + 1) + ".png").toFile());
                }
            }
        }
    }
    @Test void contextDoesNotRetainLiveAccountValues() {
        var plan = com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest.plan();
        var context = IndividualReportContext.capture(plan);
        var before = context.assumptions().toString();
        assertTrue(before.contains("target taxable income"));
        assertTrue(before.contains(com.daviddunn.retirementplanner.ui.util.UIFormatters.money(
                plan.getRothConversionRequest().getCustomTargetTaxableIncome())));
        plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(java.math.BigDecimal.ONE);
        assertEquals(before, context.assumptions().toString());
        assertFalse(before.toLowerCase().contains("spouse"));
        assertFalse(before.toLowerCase().contains("survivor"));
        assertThrows(UnsupportedOperationException.class, () -> context.assumptions().clear());
    }
    @Test void individualCoordinatesAreNineAgesWithoutAnotherPerson() {
        var ages = new ArrayList<IndividualAge>();
        for (int age = 62; age <= 70; age++) ages.add(new IndividualAge(age, "$100", "100%",
                ClaimingHeatMapPalette.ESSENTIALLY_OPTIMAL, true, age == 67, age == 65));
        var model = new Individual("Estate", ages);
        ages.clear();
        assertEquals(9, model.ages().size());
        assertEquals(1, model.ages().stream().filter(IndividualAge::selected).count());
        assertTrue(Arrays.stream(IndividualAge.class.getRecordComponents()).noneMatch(c -> c.getName().contains("spouse")));
        assertThrows(UnsupportedOperationException.class, () -> model.ages().clear());
        assertThrows(IllegalArgumentException.class, () -> new Individual("Estate", List.of()));
    }

    @Test void percentagesAndTiersUseUnroundedValuesAndSelectionIsIndependent() {
        var maximum = Optional.of(new java.math.BigDecimal("1000"));
        var age = IndividualAnalyzerReportAdapter.age(62, Optional.of(new java.math.BigDecimal("989.99")), maximum, 62, 67);
        assertEquals("99.0%", age.percentOfOptimal());
        assertEquals(ClaimingHeatMapPalette.MODERATE, age.tier());
        assertTrue(age.selected()); assertFalse(age.optimal()); assertFalse(age.current());
        assertEquals(ClaimingHeatMapPalette.UNAVAILABLE, IndividualAnalyzerReportAdapter.age(63,
                Optional.empty(), maximum, 62, 67).tier());
    }
}
