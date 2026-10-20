package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class IndividualCompletionPersistenceTest {
    @TempDir Path dir;
    @BeforeAll static void fxStart() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }
    static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    @Test void savedIndividualWeightedUsesActualEligibleCallbackAndSurvivesViewClose() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        source.newPlan(SinglePersonMortalityAnalysisTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var planBytes = Files.readAllBytes(source.getCurrentFile()); var accepted = new CountDownLatch(1);
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        var jobs = fx(() -> new SocialSecurityAnalyzerJobController(coordinator));
        var view = fx(() -> {
            var v = new SinglePersonIntegratedView(source.getCurrentPlan(), jobs);
            IndividualCompletionPersistence.attach(v.weightedView, source);
            var original = v.weightedView.acceptedCompletion;
            v.weightedView.acceptedCompletion = input -> {
                var capture = original.apply(input);
                return result -> { capture.accept(result); accepted.countDown(); };
            };
            v.weightedView.start(); return v;
        });
        assertTrue(accepted.await(45, TimeUnit.SECONDS));
        WeightedRestorationTest.await(view.weightedView.persistenceNotice, "Current Results");
        fx(() -> { assertTrue(view.weightedView.canExportPdf()); jobs.close(); return null; });
        assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit());
        var snapshot = repo.load(UUID.fromString(source.getCurrentPlan().getPlanId()), AnalysisKind.INDIVIDUAL_WEIGHTED).orElseThrow();
        assertEquals(9, snapshot.payload().individual().entries().size());
        assertEquals(snapshot.payload().individual(), WeightedSnapshots.Individual.from(snapshot.payload().individual().restore()));
        assertArrayEquals(planBytes, Files.readAllBytes(source.getCurrentFile()));
        assertFalse(source.isModified()); coordinator.close(); store.shutdownAfterSuccessfulDrain();
    }
    @Test void unsavedPlanStillRunsAndFirstSaveDoesNotSilentlyBackfillAnOldAnalysis() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        source.newPlan(SinglePersonMortalityAnalysisTest.plan()); var accepted = new CountDownLatch(1);
        var coordinator = new SocialSecurityAnalysisJobCoordinator(); var jobs = fx(() -> new SocialSecurityAnalyzerJobController(coordinator));
        var view = fx(() -> {
            var v = new SinglePersonIntegratedView(source.getCurrentPlan(), jobs);
            IndividualCompletionPersistence.attach(v.weightedView, source);
            assertTrue(v.weightedView.persistenceNotice.getText().contains("Save the retirement plan first"));
            var original = v.weightedView.acceptedCompletion;
            v.weightedView.acceptedCompletion = input -> {
                var capture = original.apply(input);
                return result -> { capture.accept(result); accepted.countDown(); };
            };
            v.weightedView.start(); return v;
        });
        assertTrue(accepted.await(45, TimeUnit.SECONDS));
        WeightedRestorationTest.await(view.weightedView.persistenceNotice, "Current Results");
        fx(() -> {
            assertTrue(view.weightedView.canExportPdf());
            assertTrue(view.weightedView.persistenceNotice.getText().contains("memory only"));
            source.saveAs(dir.resolve("first.json")); jobs.close(); return null;
        });
        assertTrue(repo.load(UUID.fromString(source.getCurrentPlan().getPlanId()), AnalysisKind.INDIVIDUAL_WEIGHTED).isEmpty());
        var slot = new AnalysisResultStore.Slot(UUID.fromString(source.getCurrentPlan().getPlanId()), AnalysisKind.INDIVIDUAL_WEIGHTED);
        assertEquals(AnalysisResultStore.Durability.MEMORY_ONLY, store.status(slot).orElseThrow().durability());
        assertTrue(store.inMemory(slot).isPresent());
        assertTrue(store.drain(Duration.ofSeconds(5)).safeToExit()); coordinator.close(); store.shutdownAfterSuccessfulDrain();
    }
}
