package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSnapshot;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPlanDocument;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog;
import com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloParameterPersistenceTest.*;
import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitEditorHistoryTest {
    @TempDir Path directory;
    @BeforeAll static void initialize() throws Exception { startFx(); }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class,
            names = {"COUPLE_MONTE_CARLO_FIXED", "COUPLE_MONTE_CARLO_PAIRED_LONGEVITY"})
    void acceptedEditorDefinitionsAndHistoricalAnalyzerPayloadSurviveLiveEditsSaveAsAndRestart(AnalysisKind kind) throws Exception {
        var fixture = new MonteCarloParameterPersistenceTest(); fixture.dir = directory;
        var source = fixture.source(kind, "editor-original", new JsonRetirementPlanRepository());
        AnalysisSnapshot original;
        var plan = source.getCurrentPlan();
        ConditionalBenefitDefinition first = ConditionalBenefitUiTestSupport.create(plan, TriggerType.DEATH, PaymentPattern.MONTHLY_RECURRING);
        var lump = ConditionalBenefitUiTestSupport.create(plan, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
        var view = fx(() -> {
            plan.setConditionalBenefits(List.of(first, lump));
            source.markModified(); source.invalidateProjection(); source.getCurrentProjection(); source.save();
            return new View(source, kind, false);
        });
        try {
            fx(() -> { view.configure(kind); view.button("run").fire(); return null; });
            view.execute(); await(view.label("parameters-status"), "SAVED");
            var completion = source.getAnalysisResults().inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(), kind)).orElseThrow();
            original = completion.presentationSnapshot(new AnalysisSnapshotCodec());
            var payload = (MonteCarloSnapshot) original.payload();
            assertNotNull(payload.benefitExecution());
            assertEquals(List.of(first, lump), MonteCarloPlanDocument.read(payload.benefitExecution().candidates().getFirst().originalInputs()).getConditionalBenefits());
            fx(() -> {
                var editor = new ConditionalBenefitDialog(plan, first); editor.show();
                ConditionalBenefitUiTestSupport.input(editor, "name").setText("Today's renamed amount");
                ConditionalBenefitUiTestSupport.input(editor, "amount").setText("9999.12345678");
                ((Button) editor.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertNotNull(editor.getResult()); assertEquals(first.id(), editor.getResult().id());
                plan.setConditionalBenefits(List.of(editor.getResult()));
                source.markModified(); source.invalidateProjection(); source.saveAs(directory.resolve("editor-copy.json"));
                return null;
            });
            assertEquals(List.of(first, lump), MonteCarloPlanDocument.read(payload.benefitExecution().candidates().getFirst().originalInputs()).getConditionalBenefits());
        } finally { fx(() -> { view.close(); return null; }); stop(source); }

        var restored = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("restart-settings")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("results"))));
        try {
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(restored, "projectionEngine",
                    new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine() {
                        @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(
                                com.daviddunn.retirementplanner.domain.model.RetirementPlan value) {
                            throw new AssertionError("Restart/history must not project");
                        }
                        @Override public com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult projectWithOutcome(
                                com.daviddunn.retirementplanner.domain.model.RetirementPlan value) {
                            throw new AssertionError("Restart/history must not execute");
                        }
                    });
            fx(() -> { restored.open(directory.resolve("editor-copy.json")); return null; });
            restored.getProjectionRestoration().toCompletableFuture().get(20, TimeUnit.SECONDS);
            var association = restored.getAnalyzerAssociations(restored.getAcceptedProjectionSnapshot().id()).getFirst();
            var historical = restored.loadAssociatedAnalyzer(association).toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(original.payload(), historical.payload());
            assertEquals(original.inputFingerprint(), historical.inputFingerprint());
            assertEquals(original.runId(), historical.runId());
            assertEquals("Today's renamed amount", restored.getCurrentPlan().getConditionalBenefits().getFirst().label());
            assertFalse(restored.currentProjectionDeterministicallyApplicable());
            var reopened = fx(() -> new View(restored, kind, true));
            try {
                fx(() -> { reopened.mode(kind.name().endsWith("LONGEVITY")); return null; });
                await(reopened.label("saved-status"), "Outdated");
                assertEquals(0, reopened.calls.get()); assertTrue(reopened.tasks.isEmpty());
            } finally { fx(() -> { reopened.close(); return null; }); }
        } finally { stop(restored); }
    }
}
