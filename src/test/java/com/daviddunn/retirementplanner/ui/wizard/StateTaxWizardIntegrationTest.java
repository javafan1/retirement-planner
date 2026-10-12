package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.views.ResultsView;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StateTaxWizardIntegrationTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fileNewActivatesFloridaThenSaveReloadKeepsZeroTax(boolean couple, @TempDir Path directory) throws Exception {
        fx(() -> {
            var window = new com.daviddunn.retirementplanner.ui.MainWindow();
            var root = (javafx.scene.layout.BorderPane) window.createScene().getRoot();
            com.daviddunn.retirementplanner.ui.controller.ApplicationController controller = field(window, "controller");
            var old = controller.getCurrentPlan();
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            javafx.application.Platform.runLater(() -> {
                var pane = activeWizardPane();
                try {
                    CompleteWizardFixtures.enterThroughReview(pane, couple);
                    ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
                    ((ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence")).setValue(StateTaxJurisdiction.FL);
                    button(pane, "Next").fire();
                    assertSame(old, controller.getCurrentPlan());
                    button(pane, "Create Plan").fire();
                }
                catch (Throwable error) { failure.set(error); button(pane, "Cancel").fire(); }
            });
            ((MenuBar) root.getTop()).getMenus().getFirst().getItems().stream()
                    .filter(item -> item.getText().equals("New")).findFirst().orElseThrow().fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertNotSame(old, controller.getCurrentPlan());
            assertFalse(controller.hasCurrentFile());
            assertEquals(StateTaxJurisdiction.FL, controller.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            AssumptionsView editor = field(window, "assumptionsView");
            assertEquals(StateTaxJurisdiction.FL, ((ComboBox<StateTaxJurisdiction>) field(editor, "stateJurisdictionComboBox")).getValue());
            Path file = directory.resolve("florida.json");
            controller.saveAs(file);
            controller.open(file);
            assertEquals(couple, controller.getCurrentPlan().getHousehold().hasSpouse());
            for (var year : controller.getCurrentProjection().getYears()) assertEquals(0, year.getStateIncomeTax().signum());
        });
    }

    @ParameterizedTest
    @CsvSource({"false,MI", "false,FL", "true,MI", "true,FL"})
    void wizardSaveReloadAndProjectionUseSelectedState(boolean couple, StateTaxJurisdiction state,
            @TempDir Path directory) throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                CompleteWizardFixtures.enterThroughReview(pane, couple);
                ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
                ComboBox<StateTaxJurisdiction> input = (ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence");
                assertEquals(List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.FL), input.getItems());
                assertNotNull(input.getTooltip());
                input.setValue(null);
                button(pane, "Next").fire();
                assertTrue(button(pane, "Next").isVisible());
                assertTrue(pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(l -> l.getText().contains("State of Residence:")));
                input.setValue(state);
                button(pane, "Next").fire();
                assertTrue(pane.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(l -> l.getText().contains("State of residence: " + state)));
                button(pane, "Create Plan").fire();
                var plan = wizard.getResult();
                assertEquals(state, plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
                var repository = new JsonRetirementPlanRepository();
                Path file = directory.resolve("plan.json");
                repository.save(plan, file);
                var loaded = repository.load(file);
                assertEquals(couple, loaded.getHousehold().hasSpouse());
                var projection = new ProjectionEngine().project(loaded);
                assertFalse(projection.isEmpty());
                for (var year : projection.getYears()) {
                    assertEquals(state, year.getStateTaxJurisdiction());
                    if (state == StateTaxJurisdiction.FL) assertEquals(0, year.getStateIncomeTax().signum());
                }
                var editor = new AssumptionsView();
                editor.load(loaded);
                ComboBox<StateTaxJurisdiction> tab = field(editor, "stateJurisdictionComboBox");
                assertEquals(state, tab.getValue());
                var results = new ResultsView();
                results.load(projection, List.of());
                assertTrue(results.getTable().getColumns().stream().anyMatch(c -> "State Income Tax".equals(c.getText())));
            }
            finally { wizard.close(); }
        });
    }

    @Test
    void tabEditsAreAtomicDirtyAndCancelRestoresState() throws Exception {
        fx(() -> {
            var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController();
            var view = new AssumptionsView();
            view.load(controller.getCurrentPlan());
            AtomicInteger callbacks = new AtomicInteger();
            view.setOnPlanChanged(() -> { callbacks.incrementAndGet(); controller.markModified(); });
            ComboBox<StateTaxJurisdiction> input = field(view, "stateJurisdictionComboBox");
            input.setValue(StateTaxJurisdiction.FL);
            assertTrue(view.isDirty());
            assertEquals(StateTaxJurisdiction.MI, controller.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            view.cancelChanges();
            assertEquals(StateTaxJurisdiction.MI, input.getValue());
            assertEquals(0, callbacks.get());
            input.setValue(StateTaxJurisdiction.FL);
            assertTrue(view.applyChanges());
            assertEquals(1, callbacks.get());
            assertTrue(controller.isModified());
            assertEquals(StateTaxJurisdiction.FL, controller.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            ((TextField) field(view, "inflationRateField")).setText("3");
            assertTrue(view.applyChanges());
            assertEquals(StateTaxJurisdiction.FL, controller.getCurrentPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        });
    }

    @Test
    void substantialFloridaDraftCancelLeavesActivePlanAndDirtyStateExactlyUnchanged() throws Exception {
        fx(() -> {
            var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController();
            controller.markModified();
            var current = controller.getCurrentPlan();
            var revision = controller.getSourcePlanRevision();
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            CompleteWizardFixtures.enterThroughReview(pane, true);
            ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
            ((ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence")).setValue(StateTaxJurisdiction.FL);
            button(pane, "Next").fire();
            button(pane, "Cancel").fire();
            assertNull(wizard.getResult());
            assertSame(current, controller.getCurrentPlan());
            assertTrue(controller.isModified());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals(StateTaxJurisdiction.MI, current.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
        });
    }
}
