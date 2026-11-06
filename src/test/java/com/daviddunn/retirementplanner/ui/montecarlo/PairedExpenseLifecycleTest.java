package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PairedExpenseLifecycleTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    private ApplicationController source(com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) throws Exception {
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("isolated-settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("isolated-results"))));
        controller.newPlan(plan); controller.saveAs(directory.resolve("isolated-plan.json"));
        return controller;
    }

    @ParameterizedTest @ValueSource(strings = {"cancel", "revision", "close", "worker", "executor"})
    void failuresAndInvalidatedRunsRetainPreviousComparisonAndRestoreControls(String action) throws Exception {
        var previous = MonteCarloComparisonFixtures.run(); var plan = MonteCarloComparisonFixtures.plan();
        StandaloneExpenseHistoryTest.volatility(plan, ".25");
        var controller = source(plan); var tasks = new ArrayDeque<Runnable>(); var calls = new AtomicInteger();
        var view = fx(() -> {
            var v = new MonteCarloStrategyComparisonView(controller, task -> {
                if (action.equals("executor")) throw new RejectedExecutionException(); tasks.add(task);
            }, (p, u, c) -> { calls.incrementAndGet(); throw new IllegalStateException(); });
            new Scene(v); v.session().restore(previous, true); ((Button) v.lookup("#mcc-run")).fire();
            if (!action.equals("executor")) {
                assertTrue(v.session().busy()); assertTrue(v.lookup("#mcc-run").isDisabled());
                ((Button) v.lookup("#mcc-run")).fire(); assertEquals(1, tasks.size());
            }
            switch (action) {
                case "cancel" -> ((Button) v.lookup("#mcc-cancel")).fire();
                case "revision" -> controller.markModified();
                case "close" -> v.close();
                default -> { }
            }
            return v;
        });
        try {
            if (!tasks.isEmpty()) tasks.remove().run(); waitForFx();
            fx(() -> {
                assertSame(previous, view.session().result()); assertFalse(view.session().busy());
                assertEquals(action.equals("worker") ? 1 : 0, calls.get());
                if (!action.equals("close")) {
                    assertFalse(view.lookup("#mcc-run").isDisabled()); assertTrue(view.lookup("#mcc-cancel").isDisabled());
                    assertFalse(view.lookup("#mcc-progress").isManaged());
                    var text = ((Label) view.lookup("#mcc-status")).getText();
                    assertFalse(text.isBlank()); assertFalse(text.contains("null"));
                    assertEquals(action.equals("worker") || action.equals("executor")
                            ? MonteCarloStrategyComparisonSession.State.FAILED : MonteCarloStrategyComparisonSession.State.CANCELLED,
                            view.session().state());
                } else assertEquals(MonteCarloStrategyComparisonSession.State.CLOSED, view.session().state());
                return null;
            });
        } finally { if (!action.equals("close")) fx(() -> { view.close(); return null; }); stop(controller); }
    }

    @Test void invalidPreflightIsVisibleAndDoesNotCaptureOrExecuteWork() throws Exception {
        var controller = source(MonteCarloComparisonFixtures.plan()); var tasks = new ArrayDeque<Runnable>();
        fx(() -> {
            var v = new MonteCarloStrategyComparisonView(controller, tasks::add, (p, u, c) -> { fail("Invalid input cannot execute"); return null; });
            try {
                new Scene(v); ((TextField) v.lookup("#mcc-return")).setText("NaN"); ((Button) v.lookup("#mcc-run")).fire();
                assertTrue(tasks.isEmpty()); assertFalse(v.session().busy());
                assertTrue(((Label) v.lookup("#mcc-validation")).getText().contains("Expected return"));
                assertTrue(v.lookup("#mcc-validation").isManaged());
                assertTrue(((Label) v.lookup("#mcc-status")).getText().contains("Run not started"));
            } finally { v.close(); }
            return null;
        }); stop(controller);
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = {"COUPLE_MONTE_CARLO_PAIRED_FIXED", "COUPLE_MONTE_CARLO_PAIRED_LONGEVITY",
            "INDIVIDUAL_MONTE_CARLO_PAIRED_FIXED", "INDIVIDUAL_MONTE_CARLO_PAIRED_LONGEVITY"})
    void failedEnabledSavePreservesLegacyArtifactAndRetrySavesWithoutReanalysis(AnalysisKind kind) throws Exception {
        var fail = new AtomicBoolean();
        var repository = AnalysisSnapshotFaults.failVerification(directory.resolve("results"), fail::get);
        var source = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("settings.json")), new AnalysisResultStore(repository));
        fx(() -> {
            var plan = kind.shape() == AnalysisKind.Shape.COUPLE ? MonteCarloMortalityUiFixtures.plan() : SinglePersonMortalityAnalysisTest.plan();
            source.newPlan(plan); PairedExpenseHistoryTest.configureInputs(source, "0", "0"); source.saveAs(directory.resolve("synthetic.json")); return null;
        });
        var active = fx(() -> new View(source, kind, false));
        var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind);
        try {
            fx(() -> { active.configure(kind); active.button("run").fire(); return null; }); active.execute();
            await(active.label("parameters-status"), "SAVED");
            assertEquals(AnalysisResultStore.Durability.SAVED, source.getAnalysisResults().whenSettled(slot).toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
            var path = repository.path(slot.planId(), kind); byte[] oldBytes = Files.readAllBytes(path);
            fx(() -> {
                source.save(); PairedExpenseHistoryTest.configureInputs(source, ".25", ".17"); source.save();
                fail.set(true); active.button("run").fire(); return null;
            }); active.execute();
            await(active.label("saved-status"), "SAVE FAILED");
            assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, source.getAnalysisResults().whenSettled(slot).toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
            assertArrayEquals(oldBytes, Files.readAllBytes(path));
            fx(() -> {
                var view = (MonteCarloStrategyComparisonView) active.node;
                assertEquals(MonteCarloStrategyComparisonSession.State.COMPLETED, view.session().state());
                assertTrue(view.canExportPdf()); assertNotNull(view.session().result().result().expenseExecution());
                assertTrue(active.label("status").getText().contains("Result save failed"));
                fail.set(false); active.button("retry-save").fire(); return null;
            });
            await(active.label("saved-status"), "SAVED");
            assertEquals(AnalysisResultStore.Durability.SAVED, source.getAnalysisResults().whenSettled(slot).toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
            assertEquals(2, active.calls.get()); assertTrue(active.tasks.isEmpty());
            assertEquals(4, repository.load(slot.planId(), kind).orElseThrow().fingerprintVersion());
            assertEquals(1, repository.loadBackup(slot.planId(), kind).orElseThrow().fingerprintVersion());
            fx(() -> { source.save(); return null; });
        } finally { fx(() -> { active.close(); return null; }); stop(source); }
    }
}
