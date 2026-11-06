package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.StandaloneExpenseHistoryTest.volatility;
import static org.junit.jupiter.api.Assertions.*;

class PairedExpenseHistoryTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    static String assumptionText(com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport report) {
        // Comparison's existing Frozen analysis details section owns its original plan assumptions.
        return Stream.concat(report.context().stream(), report.sections().stream().flatMap(section -> section.paragraphs().stream()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    static Stream<Arguments> matrix() {
        return Stream.of(AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_FIXED, AnalysisKind.COUPLE_MONTE_CARLO_PAIRED_LONGEVITY,
                AnalysisKind.INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED, AnalysisKind.INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY)
                .flatMap(kind -> Stream.of(new String[]{".25", "0"}, new String[]{"0", ".17"}, new String[]{".25", ".17"})
                        .map(r -> Arguments.of(kind, r[0], r[1])));
    }

    static void configureInputs(ApplicationController source, String a, String b) {
        var baseline = new RetirementPlanScenarioCopyService().copy(source.getCurrentPlan());
        baseline.setBaseline(null); volatility(baseline, b);
        source.getCurrentPlan().setBaseline(ProjectionBaselineFactory.create(baseline, "Original independent baseline"));
        volatility(source.getCurrentPlan(), a);
        source.markModified(); source.invalidateProjection(); source.getCurrentProjection();
    }

    @ParameterizedTest @MethodSource("matrix")
    void acceptedAsymmetricComparisonsSurviveSaveAsRestartAndBothCandidateEditsWithoutSimulation(AnalysisKind kind,
            String aRate, String bRate) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "original", new JsonRetirementPlanRepository());
        ApplicationController restart = null; View initial = null; View restored = null;
        try {
            fx(() -> { configureInputs(source, aRate, bRate); source.save(); return null; });
            var projection = source.getAcceptedProjectionSnapshot();
            initial = fx(() -> new View(source, kind, false)); var active = initial;
            fx(() -> {
                active.configure(kind); active.button("run").fire(); active.button("run").fire();
                assertEquals(1, active.tasks.size()); assertTrue(active.button("run").isDisabled()); return null;
            });
            active.execute(); await(active.label("parameters-status"), "SAVED");
            var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
            assertEquals(AnalysisResultStore.Durability.SAVED, source.getAnalysisResults().whenSettled(slot)
                    .toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
            var original = source.getAnalysisResults().inMemory(slot).orElseThrow().presentationSnapshot(new AnalysisSnapshotCodec());
            assertEquals(4, original.fingerprintVersion()); assertEquals(3, ((MonteCarloSnapshot) original.payload()).version());
            assertEquals(1, source.getAnalyzerAssociations(projection.id()).size(), source.getAnalyzerAssociationNotice(kind));
            fx(() -> { source.save(); active.close(); return null; }); initial = null;
            var owner = source.getAnalysisSessionId(); Path copy = directory.resolve("independent.json");
            fx(() -> { source.saveAs(copy); return null; });
            source.getSnapshotCopyCompletion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertNotEquals(owner, source.getAnalysisSessionId());
            var clone = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projection.id()).getFirst())
                    .toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(original.payload(), clone.payload()); assertEquals(original.runId(), clone.runId());
            assertEquals(original.inputFingerprint(), clone.inputFingerprint());
            restart = new ApplicationController(new JsonRetirementPlanRepository(),
                    new JsonApplicationSettingsRepository(directory.resolve("restart-settings.json")),
                    new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
            var reopened = restart;
            fx(() -> { reopened.open(copy); return null; });
            reopened.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            restored = fx(() -> { var v = new View(reopened, kind, true); v.mode(kind.name().endsWith("LONGEVITY")); return v; });
            var retained = restored; await(retained.label("saved-status"), "Current Results");
            var firstReport = fx(() -> ((MonteCarloStrategyComparisonView) retained.node).preparePdfReport());
            var originalContext = assumptionText(firstReport);
            assertTrue(originalContext.contains("Strategy A — Current Plan: recurring-expense volatility "
                    + (new BigDecimal(aRate).signum() > 0 ? "active" : "disabled")));
            assertTrue(originalContext.contains("Strategy B — Saved Baseline: recurring-expense volatility "
                    + (new BigDecimal(bRate).signum() > 0 ? "active" : "disabled")));
            fx(() -> { volatility(reopened.getCurrentPlan(), ".22"); reopened.markModified(); return null; });
            await(retained.label("saved-status"), "Outdated");
            fx(() -> {
                var changedBaseline = new RetirementPlanScenarioCopyService().copy(reopened.getCurrentPlan());
                changedBaseline.setBaseline(null); volatility(changedBaseline, ".33");
                reopened.getCurrentPlan().setBaseline(ProjectionBaselineFactory.create(changedBaseline, "New baseline"));
                reopened.markModified(); return null;
            });
            await(retained.label("saved-status"), "Outdated");
            var historical = fx(() -> ((MonteCarloStrategyComparisonView) retained.node).preparePdfReport());
            var context = assumptionText(historical);
            assertFalse(context.contains("expense 1 = 22%")); assertFalse(context.contains("expense 1 = 33%"));
            assertEquals(firstReport.chart(), historical.chart()); assertEquals(firstReport.sections(), historical.sections());
            assertEquals(0, retained.calls.get()); assertTrue(retained.tasks.isEmpty());
            assertFalse(retained.label("saved-status").getText().contains("Inputs Need Attention"));
            var root = Path.of("target/phase21b3/previews"); Files.createDirectories(root);
            String name = kind + "-A" + new BigDecimal(aRate).movePointRight(2).intValue() + "-B" + new BigDecimal(bRate).movePointRight(2).intValue();
            var pdf = root.resolve(name + ".pdf");
            new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(historical, pdf);
            try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
                assertTrue(text.contains("not a maximum variation")); assertTrue(text.contains("intentionally unmatched"));
                if (new BigDecimal(aRate).signum() > 0) assertTrue(text.contains("expense 1 = 25%"));
                if (new BigDecimal(bRate).signum() > 0) assertTrue(text.contains("expense 1 = 17%"));
                assertFalse(text.contains("expense 1 = 22%")); assertFalse(text.contains("expense 1 = 33%"));
                var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                for (int page = 0; page < Math.min(2, document.getNumberOfPages()); page++)
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", root.resolve(name + "-page" + (page + 1) + ".png").toFile());
            }
            var originalPlan = new JsonRetirementPlanRepository().load(directory.resolve("original.json"));
            assertEquals(0, new BigDecimal(aRate).compareTo(originalPlan.getHousehold().getExpenses().getFirst().getEffectiveAnnualExpenseVolatility()));
            assertEquals(0, new BigDecimal(bRate).compareTo(originalPlan.getBaseline().getSnapshot().getHousehold().getExpenses().getFirst().getEffectiveAnnualExpenseVolatility()));
        } finally {
            if (restored != null) { var v = restored; fx(() -> { v.close(); return null; }); }
            if (initial != null) { var v = initial; fx(() -> { v.close(); return null; }); }
            if (restart != null) stop(restart); stop(source);
        }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_MONTE_CARLO_PAIRED_FIXED", "COUPLE_MONTE_CARLO_PAIRED_LONGEVITY",
            "INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED", "INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY"})
    void originalComparisonAssociationAndFrozenCandidatesSurviveBothSwapDirections(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "swap", new JsonRetirementPlanRepository()); View view = null;
        try {
            fx(() -> {
                volatility(source.getCurrentPlan(), ".17"); source.markModified(); source.invalidateProjection(); source.getCurrentProjection();
                source.saveCurrentAsBaseline("Original seventeen percent baseline");
                volatility(source.getCurrentPlan(), ".25"); source.markModified(); source.invalidateProjection(); source.getCurrentProjection(); source.save(); return null;
            });
            var projection = source.getAcceptedProjectionSnapshot().id();
            view = fx(() -> new View(source, kind, false)); var active = view;
            fx(() -> { active.configure(kind); active.button("run").fire(); return null; }); active.execute();
            await(active.label("parameters-status"), "SAVED");
            fx(() -> { source.save(); active.close(); return null; }); view = null;
            var original = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projection).getFirst()).toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, source.analysisAssociation(original));
            for (int direction = 0; direction < 2; direction++) {
                var prepared = source.prepareProjectionSwap().toCompletableFuture().get(20, TimeUnit.SECONDS);
                fx(() -> { assertTrue(source.commitProjectionSwap(prepared)); source.save(); return null; });
                var retained = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projection).getFirst()).toCompletableFuture().get(20, TimeUnit.SECONDS);
                assertEquals(original, retained);
                assertEquals(direction == 0 ? AnalysisResultFreshness.Association.HISTORICAL_PROJECTION
                        : AnalysisResultFreshness.Association.CURRENT_PROJECTION, source.analysisAssociation(retained));
                var report = MonteCarloPdfReportAdapter.from(MonteCarloSnapshotPresentationMapper.restoreComparison((MonteCarloSnapshot) retained.payload()));
                var text = assumptionText(report);
                assertTrue(text.contains("expense 1 = 25%")); assertTrue(text.contains("expense 1 = 17%"));
            }
        } finally { if (view != null) { var v = view; fx(() -> { v.close(); return null; }); } stop(source); }
    }
}
