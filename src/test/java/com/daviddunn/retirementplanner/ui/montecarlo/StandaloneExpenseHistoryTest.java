package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneExpenseHistoryTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    static void volatility(RetirementPlan plan, String value) {
        if (plan.getHousehold().getExpenses().isEmpty()) plan.getHousehold().addExpense(new Expense("Synthetic living costs", new BigDecimal("1000")));
        var old = plan.getHousehold().getExpenses().getFirst();
        plan.getHousehold().replaceExpense(old, new Expense(old.getDescription(), old.getAnnualAmount(), old.getGrowthCategory(),
                old.getStartDate(), old.getEndDate(), old.getExpenseType(), new BigDecimal(value)));
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisKind.class, names = {"COUPLE_MONTE_CARLO_FIXED", "COUPLE_MONTE_CARLO_LONGEVITY",
            "INDIVIDUAL_MONTE_CARLO_FIXED", "INDIVIDUAL_MONTE_CARLO_LONGEVITY"})
    void acceptedEnabledHistorySurvivesSaveAsRestartAndCurrentEditsWithoutSimulation(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "enabled", new JsonRetirementPlanRepository());
        ApplicationController restart = null;
        View initial = null; View restored = null;
        try {
            fx(() -> {
                volatility(source.getCurrentPlan(), ".25"); source.markModified(); source.invalidateProjection();
                assertNotNull(source.getCurrentProjection()); source.save(); return null;
            });
            var projection = source.getAcceptedProjectionSnapshot();
            initial = fx(() -> new View(source, kind, false));
            var active = initial;
            fx(() -> { active.configure(kind); active.button("run").fire(); assertTrue(active.tasks.size() == 1); return null; });
            active.execute(); await(active.label("parameters-status"), "SAVED");
            var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
            assertEquals(AnalysisResultStore.Durability.SAVED,
                    source.getAnalysisResults().whenSettled(slot).toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
            var completed = source.getAnalysisResults().inMemory(slot).orElseThrow();
            var original = completed.presentationSnapshot(new AnalysisSnapshotCodec());
            assertEquals(3, original.fingerprintVersion());
            assertEquals(2, ((MonteCarloSnapshot) original.payload()).version());
            var references = source.getAnalyzerAssociations(projection.id());
            assertEquals(1, references.size(), source.getAnalyzerAssociationNotice(kind));
            assertEquals(original.inputFingerprint(), references.getFirst().inputFingerprint());
            fx(() -> { source.save(); active.close(); return null; }); initial = null;
            var owner = source.getAnalysisSessionId();
            Path copy = directory.resolve("independent.json");
            fx(() -> { source.saveAs(copy); return null; });
            source.getSnapshotCopyCompletion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertNotEquals(owner, source.getAnalysisSessionId());
            var cloned = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projection.id()).getFirst())
                    .toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(original.payload(), cloned.payload());
            assertEquals(original.inputFingerprint(), cloned.inputFingerprint());
            assertEquals(original.runId(), cloned.runId());

            restart = new ApplicationController(new JsonRetirementPlanRepository(),
                    new JsonApplicationSettingsRepository(directory.resolve("restart-settings.json")),
                    new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
            var reopened = restart;
            fx(() -> { reopened.open(copy); return null; });
            reopened.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            restored = fx(() -> { var v = new View(reopened, kind, true); v.mode(kind.name().endsWith("LONGEVITY")); return v; });
            var retained = restored;
            await(retained.label("saved-status"), "Current Results");
            fx(() -> {
                assertEquals(0, retained.calls.get()); assertTrue(retained.tasks.isEmpty());
                assertTrue(retained.node.lookup("#mc-expense-assumptions").isManaged());
                assertTrue(((Label) retained.node.lookup("#mc-expense-details")).getText().contains("expense 1 = 25%"));
                assertTrue(((MonteCarloAnalysisView) retained.node).preparePdfReport().context().stream()
                        .anyMatch(line -> line.contains("expense 1 = 25%")));
                volatility(reopened.getCurrentPlan(), ".17"); reopened.markModified(); return null;
            });
            await(retained.label("saved-status"), "Outdated");
            var report = fx(() -> ((MonteCarloAnalysisView) retained.node).preparePdfReport());
            assertTrue(String.join("\n", report.context()).contains("expense 1 = 25%"));
            assertFalse(String.join("\n", report.context()).contains("expense 1 = 17%"));
            fx(() -> {
                assertTrue(((Label) retained.node.lookup("#mc-expense-details")).getText().contains("expense 1 = 25%"));
                assertFalse(((Label) retained.node.lookup("#mc-expense-details")).getText().contains("expense 1 = 17%"));
                return null;
            });
            assertEquals(0, retained.calls.get()); assertTrue(retained.tasks.isEmpty());
            var folder = Path.of("target/phase21b2/previews"); Files.createDirectories(folder);
            var pdf = folder.resolve(kind + ".pdf");
            new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(report, pdf);
            try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                assertTrue(text.contains("expense 1 = 25%"));
                assertTrue(text.contains("INDEPENDENT_LOGNORMAL_EXPENSE_LEVEL_V1"));
                assertTrue(text.contains("not a maximum expense increase"));
                assertFalse(text.contains("expense 1 = 17%"));
                assertTrue(document.getNumberOfPages() >= 1);
                var image = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0, 90);
                javax.imageio.ImageIO.write(image, "png", folder.resolve(kind + "-page1.png").toFile());
            }
            var originalFile = new JsonRetirementPlanRepository().load(directory.resolve("enabled.json"));
            assertEquals(new BigDecimal(".25"), originalFile.getHousehold().getExpenses().getFirst().getAnnualExpenseVolatility());
        } finally {
            if (restored != null) { var v = restored; fx(() -> { v.close(); return null; }); }
            if (initial != null) { var v = initial; fx(() -> { v.close(); return null; }); }
            if (restart != null) stop(restart);
            stop(source);
        }
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisKind.class, names = {"COUPLE_MONTE_CARLO_FIXED", "COUPLE_MONTE_CARLO_LONGEVITY",
            "INDIVIDUAL_MONTE_CARLO_FIXED", "INDIVIDUAL_MONTE_CARLO_LONGEVITY"})
    void enabledResultAssociationFollowsOriginalProjectionAcrossBothSwapDirections(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "swap-enabled", new JsonRetirementPlanRepository());
        View view = null;
        try {
            fx(() -> {
                volatility(source.getCurrentPlan(), ".25"); source.markModified(); source.invalidateProjection();
                source.getCurrentProjection(); source.saveCurrentAsBaseline("Original 25 percent expense volatility");
                volatility(source.getCurrentPlan(), ".17"); source.markModified(); source.invalidateProjection();
                source.getCurrentProjection(); source.save(); return null;
            });
            var projectionId = source.getAcceptedProjectionSnapshot().id();
            view = fx(() -> new View(source, kind, false));
            var active = view;
            fx(() -> { active.configure(kind); active.button("run").fire(); return null; });
            active.execute(); await(active.label("parameters-status"), "SAVED");
            fx(() -> { source.save(); active.close(); return null; }); view = null;
            var reference = source.getAnalyzerAssociations(projectionId).getFirst();
            var original = source.loadAssociatedAnalyzer(reference).toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(AnalysisResultFreshness.Association.CURRENT_PROJECTION, source.analysisAssociation(original));
            for (int direction = 0; direction < 2; direction++) {
                var prepared = source.prepareProjectionSwap().toCompletableFuture().get(20, TimeUnit.SECONDS);
                fx(() -> { assertTrue(source.commitProjectionSwap(prepared)); source.save(); return null; });
                assertEquals(new BigDecimal(direction == 0 ? ".25" : ".17"),
                        source.getCurrentPlan().getHousehold().getExpenses().getFirst().getAnnualExpenseVolatility());
                var retained = source.loadAssociatedAnalyzer(source.getAnalyzerAssociations(projectionId).getFirst())
                        .toCompletableFuture().get(20, TimeUnit.SECONDS);
                assertEquals(original, retained);
                assertEquals(direction == 0 ? AnalysisResultFreshness.Association.HISTORICAL_PROJECTION
                        : AnalysisResultFreshness.Association.CURRENT_PROJECTION, source.analysisAssociation(retained));
            }
        } finally {
            if (view != null) { var v = view; fx(() -> { v.close(); return null; }); }
            stop(source);
        }
    }
}
