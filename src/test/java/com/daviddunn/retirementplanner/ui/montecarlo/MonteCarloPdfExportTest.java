package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.analysis.*;
import javafx.scene.control.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloPdfExportTest {
    @TempDir Path directory;
    @BeforeAll static void init() throws Exception { startFx(); }

    static String text(Path pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf.toFile())) {
            assertEquals("Retirement Planner", document.getDocumentInformation().getCreator());
            assertTrue(document.getDocumentInformation().getSubject().contains("no analysis rerun"));
            String text = new PDFTextStripper().getText(document);
            for (int i = 1; i <= document.getNumberOfPages(); i++) assertTrue(text.contains("Page " + i + " of " + document.getNumberOfPages()));
            return text.replaceAll("\\s+", " ");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void singleExportNeverInvokesAnalysisAgainAndDisablesWhenStale(boolean mortality) throws Exception {
        var calls = new AtomicInteger(); var queue = new ArrayDeque<Runnable>();
        var plan = MonteCarloMortalityUiFixtures.plan(); var controller = new Controller(plan);
        var view = fx(() -> {
            var v = new MonteCarloAnalysisView(controller, queue::add,
                    (p, s, r, u, c) -> { calls.incrementAndGet(); return new MonteCarloRunService().run(p, s, r, u, c); },
                    (p, r, u, c) -> { calls.incrementAndGet(); return new MonteCarloRunService().runMortality(p, r, u, c); });
            assertFalse(v.canExportPdf()); assertTrue(v.lookup("#mc-export-pdf").isDisabled());
            assertThrows(IllegalStateException.class, v::preparePdfReport);
            ((ComboBox<Integer>) v.lookup("#mc-simulations")).setValue(3);
            if (mortality) ((ComboBox<MonteCarloMode>) v.lookup("#mc-mode")).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            ((Button) v.lookup("#mc-run")).fire(); assertFalse(v.canExportPdf()); return v;
        });
        try {
            queue.remove().run(); waitForFx();
            fx(() -> {
                assertTrue(view.canExportPdf()); assertFalse(view.lookup("#mc-export-pdf").isDisabled());
                var frozen = view.session().result(); var report = view.preparePdfReport();
                view.exportPdf(directory.resolve("first.pdf")); view.exportPdf(directory.resolve("second.pdf")); view.exportPdf(directory.resolve("first.pdf"));
                assertEquals(1, calls.get()); assertSame(frozen, view.session().result());
                assertTrue(report.context().toString().contains("Seed: 417"));
                assertTrue(report.context().toString().contains("Healthcare inflation (deterministic)"));
                assertTrue(report.context().toString().contains("Projection start: 2027-01-01"));
                ((TextField) view.lookup("#mc-seed")).setText("999");
                assertTrue(view.session().stale()); assertFalse(view.canExportPdf()); assertTrue(view.lookup("#mc-export-pdf").isDisabled());
                assertThrows(IllegalStateException.class, view::preparePdfReport);
                plan.getHousehold().getPrimaryPerson().setBirthDate(java.time.LocalDate.of(1961, 1, 1));
                plan.setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                        new BigDecimal("0.99"), new BigDecimal("0.88"), 5, java.time.LocalDate.of(2035, 1, 1)));
                controller.markModified();
                var after = MonteCarloPdfReportAdapter.from(frozen);
                assertEquals(report.context(), after.context()); assertEquals(report.sections(), after.sections()); assertEquals(report.chart(), after.chart());
                new MonteCarloPdfExporter().export(after, directory.resolve("frozen.pdf")); assertEquals(1, calls.get());
                assertThrows(java.io.IOException.class, () -> new MonteCarloPdfExporter().export(report, directory.resolve("missing/destination.pdf")));
                assertSame(frozen, view.session().result());
                return null;
            });
            String text = text(directory.resolve("first.pdf"));
            for (var expected : List.of("Monte Carlo Retirement Analysis", mortality ? "Longevity-Adjusted" : "Fixed Lifespan", "Seed: 417", "Expected annual return", "Return volatility",
                    "funding probability", "Investable Assets", "Net Worth", "After-Tax Estate", "Modeled", "Income Taxes", "P10", "P25", "P75", "P90", "Sample count", "arithmetic mean annual return")) assertTrue(text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)), expected);
            if (mortality) for (var expected : List.of("Mortality categories", "Conditioning date", "Longevity adjustments", "Survivor Social Security claiming age", "still living", "second-death date", "not discounted")) assertTrue(text.contains(expected), expected);
        } finally { fx(() -> { view.close(); return null; }); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void comparisonExportNeverInvokesPairedAnalysisAgainAndFreezesBaseline(boolean mortality) throws Exception {
        var calls = new AtomicInteger(); var queue = new ArrayDeque<Runnable>();
        var plan = MonteCarloComparisonFixtures.plan(); var controller = new Controller(plan);
        var view = fx(() -> {
            var v = new MonteCarloStrategyComparisonView(controller, queue::add, (p, u, c) -> {
                calls.incrementAndGet(); return new MonteCarloStrategyComparisonRunService().run(p, u, c);
            });
            assertTrue(v.lookup("#mcc-export-pdf").isDisabled());
            ((ComboBox<Integer>) v.lookup("#mcc-count")).setValue(3);
            if (mortality) ((ComboBox<MonteCarloMode>) v.lookup("#mcc-mode")).setValue(MonteCarloMode.LONGEVITY_ADJUSTED);
            ((Button) v.lookup("#mcc-run")).fire(); assertFalse(v.canExportPdf()); return v;
        });
        try {
            queue.remove().run(); waitForFx();
            fx(() -> {
                assertTrue(view.canExportPdf()); var frozen = view.session().result(); var report = view.preparePdfReport();
                view.exportPdf(directory.resolve("first.pdf")); view.exportPdf(directory.resolve("second.pdf")); view.exportPdf(directory.resolve("first.pdf"));
                assertEquals(1, calls.get());
                ((TextField) view.lookup("#mcc-seed")).setText("999");
                assertTrue(view.lookup("#mcc-export-pdf").isDisabled()); assertThrows(IllegalStateException.class, view::preparePdfReport);
                plan.setBaseline(null); controller.markModified();
                var after = MonteCarloPdfReportAdapter.from(frozen);
                assertEquals(report.context(), after.context()); assertEquals(report.sections(), after.sections()); assertEquals(report.chart(), after.chart());
                new MonteCarloPdfExporter().export(after, directory.resolve("frozen.pdf")); assertEquals(1, calls.get());
                assertThrows(UnsupportedOperationException.class, () -> report.sections().clear());
                assertThrows(UnsupportedOperationException.class, () -> report.sections().getFirst().tables().getFirst().rows().getFirst().clear());
                return null;
            });
            String text = text(directory.resolve("first.pdf"));
            for (var expected : List.of("Monte Carlo Strategy Comparison", "Current Plan", "Saved Baseline", "matched simulated worlds", "percentage points", "Both completed",
                    "Current completed / Baseline failed", "Current failed / Baseline completed", "Both failed", "Current - Baseline", "both strategies completed successfully",
                    "Investable Assets Difference Over Time", "percentiles of the paired differences", "NOT percentile(Current)", "paid MORE", "paid LESS", "P(Current >", "P(Current =", "P(Current <", "Mean A - B")) assertTrue(text.contains(expected), expected);
            if (mortality) assertTrue(text.contains("second-death date"));
            for (var banned : List.of("winner", "loser", "recommended", "superior")) assertFalse(text.toLowerCase(Locale.ROOT).contains(banned));
        } finally { fx(() -> { view.close(); return null; }); }
    }

    @Test void failedAndCancelledRunsCannotExport() throws Exception {
        var queue = new ArrayDeque<Runnable>();
        var single = fx(() -> new MonteCarloAnalysisView(new Controller(MonteCarloMortalityUiFixtures.plan()), queue::add,
                (p, s, r, u, c) -> { throw new IllegalStateException("test failure"); }));
        var paired = fx(() -> new MonteCarloStrategyComparisonView(new Controller(MonteCarloComparisonFixtures.plan()), queue::add,
                (p, u, c) -> { throw new IllegalStateException("test failure"); }));
        try {
            fx(() -> { ((Button) single.lookup("#mc-run")).fire(); ((Button) paired.lookup("#mcc-run")).fire(); return null; });
            while (!queue.isEmpty()) queue.remove().run(); waitForFx();
            fx(() -> {
                assertFalse(single.canExportPdf()); assertFalse(paired.canExportPdf());
                assertTrue(single.lookup("#mc-export-pdf").isDisabled()); assertTrue(paired.lookup("#mcc-export-pdf").isDisabled());
                ((Button) single.lookup("#mc-run")).fire(); ((Button) paired.lookup("#mcc-run")).fire();
                single.session().cancel(); paired.session().cancel(); return null;
            });
            while (!queue.isEmpty()) queue.remove().run(); waitForFx();
            fx(() -> {
                assertEquals(MonteCarloSession.State.CANCELLED, single.session().state());
                assertEquals(MonteCarloStrategyComparisonSession.State.CANCELLED, paired.session().state());
                assertThrows(IllegalStateException.class, single::preparePdfReport); assertThrows(IllegalStateException.class, paired::preparePdfReport); return null;
            });
        } finally { fx(() -> { single.close(); paired.close(); return null; }); }
    }

    @Test void emptyPopulationsAndFailuresExportWithoutFabricatedFinancialZeros() throws Exception {
        for (var run : List.of(MonteCarloMortalityUiFixtures.run(0, false), MonteCarloMortalityUiFixtures.run(4, true), MonteCarloUiFixtures.run("0"))) {
            var report = fx(() -> MonteCarloPdfReportAdapter.from(run));
            var path = directory.resolve("edge-" + run.mode() + "-" + run.lastYear() + ".pdf");
            new MonteCarloPdfExporter().export(report, path);
            String text = text(path); assertFalse(text.contains("NaN")); assertFalse(text.contains("Infinity"));
            if (run.mode() == MonteCarloMode.FIXED_LIFESPAN || run.mortalityResult().completedCount() == 0) assertTrue(text.contains("Unavailable"));
        }
    }

    @Test void failedRenderingPreservesExistingDestinationAndRemovesTemporaryFile() throws Exception {
        var good = fx(() -> MonteCarloPdfReportAdapter.from(MonteCarloComparisonFixtures.run()));
        var brokenQuantile = new MonteCarloPercentiles(1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO);
        var badChart = new MonteCarloPdfReport.Chart("Invalid rendering fixture", "", true,
                List.of(new MonteCarloPdfReport.Point(2030, Optional.of(brokenQuantile), Optional.empty())), List.of(), List.of());
        var bad = new MonteCarloPdfReport(good.title(), good.type(), good.filename(), good.exportedAt(), good.context(), badChart, good.sections());
        Path path = directory.resolve("existing.pdf"); Files.writeString(path, "original");
        assertThrows(RuntimeException.class, () -> new MonteCarloPdfExporter().export(bad, path));
        assertEquals("original", Files.readString(path));
        try (var files = Files.list(directory)) { assertEquals(List.of(path), files.toList()); }
    }

    @Test void allFailedComparisonHasMissingConditionalValuesAndExactFailureDenominators() throws Exception {
        var plan = MonteCarloUiFixtures.plan("0", 3);
        plan.setBaseline(com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory.create(plan, "Unfunded fixture"));
        var prepared = fx(() -> MonteCarloStrategyComparisonRunService.capture(plan, new MonteCarloSettings(3, 417, BigDecimal.ZERO, BigDecimal.ZERO),
                MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67"));
        var run = new MonteCarloStrategyComparisonRunService().run(prepared, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(3, run.result().summary().pairedStates().bothFailed());
        var report = fx(() -> MonteCarloPdfReportAdapter.from(run));
        new MonteCarloPdfExporter().export(report, directory.resolve("both-failed.pdf"));
        var text = text(directory.resolve("both-failed.pdf"));
        assertTrue(text.contains("Unavailable")); assertTrue(text.contains("0 of 3"));
        assertTrue(text.contains("Strategy A funding failures")); assertTrue(text.contains("Strategy B funding failures"));
        assertTrue(text.contains("Probability / all requested"));
        var financial = report.sections().stream().filter(s -> s.title().equals("Paired financial differences")).findFirst().orElseThrow().tables().getFirst();
        assertTrue(financial.rows().getFirst().subList(1, 5).stream().allMatch("Unavailable"::equals));
    }
}
