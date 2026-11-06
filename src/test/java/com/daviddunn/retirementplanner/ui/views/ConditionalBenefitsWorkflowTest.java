package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.benefit.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitDialog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitsWorkflowTest {
    @TempDir Path directory;
    @BeforeAll static void initialize() throws Exception { startFx(); }

    @Test void sectionCommitsAddEditAndDeleteThroughExistingIncomeCallbackAndCancelDoesNothing() throws Exception {
        fx(() -> {
            var plan = plan(true);
            var view = new IncomeSourcesView();
            var notifications = new AtomicInteger();
            view.setOnPlanChanged(notifications::incrementAndGet);
            view.load(plan);
            var stage = new Stage(); stage.setScene(new Scene(view, 900, 650)); stage.show();
            try {
                view.applyCss(); view.layout();
                var section = (ConditionalBenefitsPane) view.lookup("#conditional-benefits-section");
                var add = (Button) section.lookup("#conditional-benefit-add");
                modal(add, pane -> {
                    assertSame(stage, ((Stage) pane.getScene().getWindow()).getOwner());
                    configurePane(pane);
                    ((Button) pane.lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                });
                assertEquals(1, plan.getConditionalBenefits().size());
                var original = plan.getConditionalBenefits().getFirst();
                assertEquals(1, notifications.get());
                modal((Button) section.lookup("#conditional-benefit-edit"), pane ->
                        ((Button) pane.lookupButton(ConditionalBenefitDialog.SAVE)).fire());
                assertEquals(1, notifications.get(), "Unchanged modal Save is a no-op, including decimal scale");
                modal((Button) section.lookup("#conditional-benefit-edit"), pane -> {
                    ((TextField) pane.lookup("#cb-amount")).setText("450000");
                    ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
                });
                assertEquals(List.of(original), plan.getConditionalBenefits());
                assertEquals(1, notifications.get());
                modal((Button) section.lookup("#conditional-benefit-edit"), pane -> {
                    ((TextField) pane.lookup("#cb-amount")).setText("450000");
                    ((Button) pane.lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                });
                assertEquals(original.id(), plan.getConditionalBenefits().getFirst().id());
                assertEquals(new BigDecimal("450000"), plan.getConditionalBenefits().getFirst().grossAmount());
                modal((Button) section.lookup("#conditional-benefit-duplicate"), pane ->
                        ((Button) pane.lookupButton(ConditionalBenefitDialog.SAVE)).fire());
                assertEquals(2, plan.getConditionalBenefits().size());
                assertNotEquals(plan.getConditionalBenefits().getFirst().id(), plan.getConditionalBenefits().getLast().id());
                modal((Button) section.lookup("#conditional-benefit-remove"), pane ->
                        ((Button) pane.lookupButton(ButtonType.CANCEL)).fire());
                assertEquals(2, plan.getConditionalBenefits().size());
                modal((Button) section.lookup("#conditional-benefit-remove"), pane ->
                        ((Button) pane.lookupButton(ButtonType.OK)).fire());
                assertEquals(1, plan.getConditionalBenefits().size());
                assertEquals(4, notifications.get(), "Only accepted add/edit/duplicate/remove notify");
                assertEquals(2, view.getTable().getItems().size(), "Existing Social Security table preserved");
            } finally { stage.close(); }
            return null;
        });
    }

    @Test void maximumCountDuplicateIdentityAndStaleTransactionsCannotPartiallyCommit() throws Exception {
        fx(() -> {
            var plan = plan(true); var section = new ConditionalBenefitsPane(); section.load(plan);
            var first = BenefitFixtures.monthly(); section.commit(plan, null, first);
            assertThrows(IllegalArgumentException.class, () -> section.commit(plan, null, first));
            assertEquals(List.of(first), plan.getConditionalBenefits());
            var changed = BenefitFixtures.change(first, "grossAmount", new BigDecimal("3000"));
            section.commit(plan, first, changed);
            assertThrows(IllegalStateException.class, () -> section.commit(plan, first, changed));
            var other = plan(true); section.load(other);
            assertThrows(IllegalStateException.class, () -> section.commit(plan, changed, first));
            assertTrue(other.getConditionalBenefits().isEmpty());
            var hundred = IntStream.range(0, 100).mapToObj(index -> BenefitFixtures.change(first, "id", new UUID(1, index))).toList();
            plan.setConditionalBenefits(hundred); section.load(plan);
            assertTrue(((Button) section.lookup("#conditional-benefit-add")).isDisabled());
            assertTrue(((Button) section.lookup("#conditional-benefit-duplicate")).isDisabled());
            assertThrows(IllegalArgumentException.class, () -> section.commit(plan, null, BenefitFixtures.change(first, "id", UUID.randomUUID())));
            assertEquals(hundred, plan.getConditionalBenefits());
            return null;
        });
    }

    @Test void unchangedEditDoesNotNotifyAndDisplaySortingNeverReordersPlanDefinitions() throws Exception {
        fx(() -> {
            var plan = plan(true); var first = BenefitFixtures.monthly();
            var second = BenefitFixtures.change(BenefitFixtures.change(first, "id", UUID.randomUUID()), "label", "A first display row");
            plan.setConditionalBenefits(List.of(first, second));
            var section = new ConditionalBenefitsPane(); section.load(plan);
            var notifications = new AtomicInteger(); section.setOnChanged(notifications::incrementAndGet);
            section.commit(plan, first, BenefitFixtures.change(first, "grossAmount", first.grossAmount().setScale(8)));
            assertEquals(0, notifications.get());
            var table = table(section);
            table.getSortOrder().add(table.getColumns().getFirst()); table.sort();
            assertEquals(List.of(first, second), plan.getConditionalBenefits());
            assertEquals(second.id(), table.getItems().getFirst().id());
            assertEquals(0, notifications.get());
            return null;
        });
    }

    @Test void revisionAndFinancialIdentityDistinguishLabelsFromAmountsAndPreserveAcceptedHistory() throws Exception {
        var controller = controller();
        try {
            var plan = controller.getCurrentPlan();
            var first = create(plan, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
            var section = fx(() -> {
                var pane = new ConditionalBenefitsPane(); pane.load(plan);
                pane.setOnChanged(() -> { controller.markModified(); controller.invalidateProjection(); });
                pane.commit(plan, null, first); return pane;
            });
            controller.getCurrentProjection();
            var original = controller.getAcceptedProjectionSnapshot();
            var fingerprints = new AnalysisFingerprintService();
            String before = fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none());
            long revision = controller.getSourcePlanRevision();
            var renamed = BenefitFixtures.change(first, "label", "New description only");
            fx(() -> { section.commit(plan, first, renamed); return null; });
            assertEquals(revision + 1, controller.getSourcePlanRevision(), "A rename still invalidates in-flight source guards");
            assertEquals(before, fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none()));
            assertEquals(first.label(), original.restoreInputs().getConditionalBenefits().getFirst().label());
            assertEquals(original, controller.getAcceptedProjectionSnapshot());
            var amount = BenefitFixtures.change(renamed, "grossAmount", new BigDecimal("4500"));
            fx(() -> { section.commit(plan, renamed, amount); return null; });
            assertNotEquals(before, fingerprints.fingerprint(AnalysisKind.COUPLE_EXHAUSTIVE, plan, AnalysisFingerprintService.Inputs.none()));
            assertFalse(controller.currentProjectionDeterministicallyApplicable());
            assertEquals(original.annual(), controller.getAcceptedProjectionSnapshot().annual());
        } finally { stop(controller); }
    }

    @Test void uiDefinitionsSurviveSaveLoadSaveAsRestartAndBothProjectionSwapDirections() throws Exception {
        var controller = controller(); ApplicationController reopened = null;
        try {
            var plan = controller.getCurrentPlan();
            var first = create(plan, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
            var section = fx(() -> { var pane = new ConditionalBenefitsPane(); pane.load(plan);
                pane.setOnChanged(() -> { controller.markModified(); controller.invalidateProjection(); });
                pane.commit(plan, null, first); return pane; });
            controller.getCurrentProjection(); var baseline = controller.getAcceptedProjectionSnapshot();
            controller.saveCurrentAsBaseline("Original UI benefit");
            var edited = fx(() -> {
                var dialog = new ConditionalBenefitDialog(plan, first); dialog.show();
                input(dialog, "name").setText("Edited frozen current"); input(dialog, "amount").setText("450000");
                ((Button) dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
                assertNotNull(dialog.getResult()); section.commit(plan, first, dialog.getResult()); return dialog.getResult();
            });
            controller.getCurrentProjection(); var current = controller.getAcceptedProjectionSnapshot();
            controller.saveAs(directory.resolve("original-ui.json")); controller.save();
            controller.saveAs(directory.resolve("copy-ui.json"));
            reopened = controller();
            com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.set(reopened, "projectionEngine", throwing());
            reopened.open(controller.getCurrentFile());
            reopened.getProjectionRestoration().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of(edited), reopened.getCurrentPlan().getConditionalBenefits());
            assertEquals(current.id(), reopened.getAcceptedProjectionSnapshot().id());
            var swap = reopened.prepareProjectionSwap().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(reopened.commitProjectionSwap(swap));
            assertEquals(List.of(first), reopened.getCurrentPlan().getConditionalBenefits());
            assertEquals(baseline.annual(), reopened.getAcceptedProjectionSnapshot().annual());
            reopened.save();
            swap = reopened.prepareProjectionSwap().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(reopened.commitProjectionSwap(swap));
            assertEquals(List.of(edited), reopened.getCurrentPlan().getConditionalBenefits());
            assertEquals(current.annual(), reopened.getAcceptedProjectionSnapshot().annual());
            reopened.save();
            fx(() -> {
                var pane = new ConditionalBenefitsPane(); pane.load(plan);
                var stage = new Stage(); stage.setScene(new Scene(pane, 900, 650)); stage.show();
                table(pane).getSelectionModel().selectFirst();
                try { modal((Button) pane.lookup("#conditional-benefit-remove"), dialog ->
                        ((Button) dialog.lookupButton(ButtonType.OK)).fire()); }
                finally { stage.close(); }
                return null;
            });
            // The reopened controller legitimately replaced the other copy during the swap checks.
            // Keep the original controller's delete transaction isolated from that external writer.
            controller.markModified(); controller.saveAs(directory.resolve("after-delete-ui.json"));
            assertTrue(new JsonRetirementPlanRepository().load(controller.getCurrentFile()).getConditionalBenefits().isEmpty());
            assertEquals(first, baseline.restoreInputs().getConditionalBenefits().getFirst());
            assertEquals(edited, current.restoreInputs().getConditionalBenefits().getFirst());
        } finally { if (reopened != null) stop(reopened); stop(controller); }
    }

    private ApplicationController controller() throws Exception {
        var c = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(directory.resolve("settings-" + UUID.randomUUID())),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analyses-" + UUID.randomUUID()))));
        c.newPlan(plan(true)); return c;
    }

    private static void stop(ApplicationController controller) throws Exception {
        controller.getProjectionRestoration().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS);
        controller.getSnapshotCopyCompletion().toCompletableFuture().get(15, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
        controller.getAnalysisParameters().shutdown();
        assertTrue(controller.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit());
        controller.getAnalysisResults().shutdownAfterSuccessfulDrain();
    }

    private static com.daviddunn.retirementplanner.domain.projection.ProjectionEngine throwing() {
        return new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine() {
            @Override public com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult projectWithOutcome(RetirementPlan plan) {
                throw new AssertionError("Historical restore/swap must not execute");
            }
            @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(RetirementPlan plan) {
                throw new AssertionError("Historical restore/swap must not project");
            }
        };
    }

    @SuppressWarnings("unchecked") private static TableView<ConditionalBenefitDefinition> table(ConditionalBenefitsPane pane) {
        return (TableView<ConditionalBenefitDefinition>) pane.lookup("#conditional-benefits-table");
    }

    @SuppressWarnings("unchecked") private static void configurePane(DialogPane pane) {
        ((TextField) pane.lookup("#cb-name")).setText("UI survivor lump sum");
        ((TextField) pane.lookup("#cb-amount")).setText("400000");
        ((ComboBox<Category>) pane.lookup("#cb-category")).setValue(Category.DEATH_BENEFIT);
        ((ComboBox<PaymentPattern>) pane.lookup("#cb-pattern")).setValue(PaymentPattern.ONE_TIME_LUMP_SUM);
        ((ComboBox<PersonReference>) pane.lookup("#cb-trigger-person")).setValue(PersonReference.PRIMARY);
        ((ComboBox<PersonReference>) pane.lookup("#cb-recipient")).setValue(PersonReference.SPOUSE);
    }

    private static void modal(Button button, Consumer<DialogPane> action) {
        var completion = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            DialogPane pane = null;
            try {
                pane = Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                        .map(scene -> scene.getRoot().lookup(".dialog-pane")).filter(DialogPane.class::isInstance)
                        .map(DialogPane.class::cast).findFirst().orElseThrow();
                action.accept(pane); completion.complete(null);
            } catch (Throwable failure) {
                if (pane != null) pane.getScene().getWindow().hide();
                completion.completeExceptionally(failure);
            }
        });
        button.fire();
        completion.join();
    }
}
