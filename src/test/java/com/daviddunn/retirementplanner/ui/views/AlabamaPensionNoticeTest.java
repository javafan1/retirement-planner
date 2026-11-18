package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.PensionPlanClassification;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.StateTaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.AlabamaProjectionAcceptanceTest;
import com.daviddunn.retirementplanner.domain.tax.state.ProductionStateTaxResult;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableReason;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;

import static com.daviddunn.retirementplanner.ui.views.AssumptionsViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class AlabamaPensionNoticeTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @TempDir Path directory;

    @Test
    void selectionAndNavigationPreserveDraftUntilApplyAndReviewAllCandidates() throws Exception {
        fx(() -> {
            var plan = AlabamaProjectionAcceptanceTest.plan(true);
            plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.michigan()));
            var first = pension("City", AccountOwnership.PRIMARY);
            var second = pension("Employer", AccountOwnership.SPOUSE);
            plan.getHousehold().getPrimaryPerson().addIncomeSource(first);
            plan.getHousehold().getSpouse().addIncomeSource(second);
            var controller = new ApplicationController();
            controller.newPlan(plan);
            var window = new MainWindow(controller);
            window.createScene();
            var assumptions = AssumptionsViewTest.<AssumptionsView>control(window, "assumptionsView");
            var income = AssumptionsViewTest.<IncomeSourcesView>control(window, "incomeSourcesView");
            ComboBox<StateTaxJurisdiction> residence = control(assumptions, "stateJurisdictionComboBox");
            TextField inflation = control(assumptions, "inflationRateField");
            invoke(window, "selectWorkspace", String.class, "assumptions");
            Parent notice = control(assumptions, "alabamaPensionNotice");
            assertFalse(notice.isVisible());

            var original = plan.getPlanningAssumptions();
            var revision = controller.getSourcePlanRevision();
            residence.setValue(StateTaxJurisdiction.AL);
            inflation.setText("3.75");
            assertTrue(notice.isVisible());
            ((Button) notice.lookup(".dismiss-alabama-pensions")).fire();
            inflation.setText("4.25");
            assertFalse(notice.isVisible());
            residence.setValue(StateTaxJurisdiction.MI);
            assertFalse(notice.isVisible());
            residence.setValue(StateTaxJurisdiction.AL);
            assertTrue(notice.isVisible());

            ((Button) notice.lookup(".review-alabama-pensions")).fire();
            assertTrue(income.getParent().isVisible());
            assertSame(first, income.getTable().getSelectionModel().getSelectedItem());
            assertSame(original, plan.getPlanningAssumptions());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals("4.25", inflation.getText());
            assertEquals(PensionPlanClassification.UNKNOWN, first.getPlanClassification());

            editSelected(income, PensionPlanClassification.DEFINED_BENEFIT_414J, ButtonType.CANCEL);
            assertSame(first, income.getTable().getSelectionModel().getSelectedItem());
            assertEquals(PensionPlanClassification.UNKNOWN, first.getPlanClassification());
            invoke(window, "selectWorkspace", String.class, "assumptions");
            var candidates = notice.lookupAll(".menu-button").stream().filter(MenuButton.class::isInstance)
                    .map(MenuButton.class::cast).findFirst().orElseThrow();
            assertEquals(List.of("1. City (PRIMARY)", "2. Employer (SPOUSE)"),
                    candidates.getItems().stream().map(item -> item.getText()).toList());
            candidates.getItems().getLast().fire();
            assertSame(second, income.getTable().getSelectionModel().getSelectedItem());
            editSelected(income, PensionPlanClassification.DEFINED_CONTRIBUTION, ButtonType.OK);
            assertTrue(notice.isVisible());
            assertFalse(candidates.isManaged());
            ((Button) notice.lookup(".review-alabama-pensions")).fire();
            editSelected(income, PensionPlanClassification.DEFINED_BENEFIT_414J, ButtonType.OK);
            assertFalse(notice.isVisible());
            assertEquals("4.25", inflation.getText());

            assumptions.cancelChanges();
            assertSame(original, plan.getPlanningAssumptions());
            assertEquals(StateTaxJurisdiction.MI, residence.getValue());
            residence.setValue(StateTaxJurisdiction.AL);
            assertTrue(assumptions.applyChanges());
            assertFalse(notice.isVisible());
            assertEquals(StateTaxJurisdiction.AL, plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            controller.saveCurrentAsBaseline("Verified Alabama");
            residence.setValue(StateTaxJurisdiction.MI);
            assertTrue(assumptions.applyChanges());
            assertFalse(notice.isVisible());
            assertEquals(StateTaxJurisdiction.AL, plan.getBaseline().getSnapshot().getPlanningAssumptions()
                    .getStateTaxAssumptions().jurisdiction());
        });
    }

    @Test
    void legacyAlabamaOpenPreflightsAndVerifiedSaveAsReopensResults() throws Exception {
        var legacyFile = directory.resolve("legacy.json");
        var verifiedFile = directory.resolve("verified.json");
        var plan = AlabamaProjectionAcceptanceTest.plan(false);
        plan.getHousehold().getPrimaryPerson().addIncomeSource(pension("Legacy pension", AccountOwnership.PRIMARY));
        new JsonRetirementPlanRepository().save(plan, legacyFile);
        assertFalse(Files.readString(legacyFile).contains("\"planClassification\""));
        fx(() -> {
            var controller = new ApplicationController();
            controller.open(legacyFile);
            var window = new MainWindow(controller);
            var root = window.createScene().getRoot();
            invoke(window, "loadCurrentPlan");
            Parent notice = (Parent) root.lookup("#projection-alabama-pension-notice");
            assertTrue(notice.isVisible());
            assertNull(controller.peekCurrentProjection());
            var loaded = controller.getCurrentPlan();
            var pension = (Pension) loaded.getHousehold().getPrimaryPerson().getIncomeSources().getLast();
            assertEquals(PensionPlanClassification.UNKNOWN, pension.getPlanClassification());
            assertFalse(controller.isModified());

            invoke(window, "refreshProjectionViews");
            assertTrue(notice.isVisible());
            assertNull(controller.peekCurrentProjection());
            assertTrue(((Label) control(window, "statusLabel")).getText().contains("State Tax Unavailable"));

            var income = AssumptionsViewTest.<IncomeSourcesView>control(window, "incomeSourcesView");
            ((Button) notice.lookup(".review-alabama-pensions")).fire();
            assertSame(pension, income.getTable().getSelectionModel().getSelectedItem());
            editSelected(income, PensionPlanClassification.DEFINED_CONTRIBUTION, ButtonType.OK);
            assertFalse(notice.isVisible());
            assertNotNull(controller.peekCurrentProjection());
            controller.saveAs(verifiedFile);
            assertTrue(Files.readString(verifiedFile).contains("DEFINED_CONTRIBUTION"));
            assertFalse(Files.readString(legacyFile).contains("DEFINED_CONTRIBUTION"));
            controller.open(verifiedFile);
            controller.getProjectionRestoration().toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
            invoke(window, "loadCurrentPlan");
            assertFalse(notice.isVisible());
            assertNotNull(controller.peekCurrentProjection());
            assertEquals(StateTaxJurisdiction.AL, controller.peekCurrentProjection().getYearAt(0).getStateTaxJurisdiction());
        });
    }

    @Test
    void unexpectedStateTaxFailuresKeepTheOriginalFallbackDialog() throws Exception {
        fx(() -> {
            var controller = new ApplicationController();
            var window = new MainWindow(controller);
            var unavailable = ProductionStateTaxResult.unsupported(StateTaxJurisdiction.AL,
                    StateTaxAssumptions.CalculationMethod.SIMPLIFIED_STATE_ESTIMATE,
                    List.of(StateTaxUnavailableReason.UNSUPPORTED_MODEL_RULE), "Other Alabama admission issue.");
            var answer = new FutureTask<>(() -> {
                var pane = Window.getWindows().stream().filter(Window::isShowing)
                        .filter(candidate -> candidate.getScene().getRoot() instanceof DialogPane)
                        .findFirst().orElseThrow();
                try {
                    assertEquals("State Tax Unavailable", ((javafx.stage.Stage) pane).getTitle());
                    assertTrue(((DialogPane) pane.getScene().getRoot()).getContentText().contains("Other Alabama admission issue."));
                } finally {
                    ((Button) ((DialogPane) pane.getScene().getRoot()).lookupButton(ButtonType.OK)).fire();
                }
                return null;
            });
            Platform.runLater(answer);
            invoke(window, "handleProjectionFailure", Exception.class, new StateTaxUnavailableException(unavailable, 2027));
            answer.get();
        });
    }

    private static Pension pension(String name, AccountOwnership owner) {
        return new Pension(name, owner, LocalDate.of(2027, 1, 1), null, new BigDecimal("3000"), BigDecimal.ZERO);
    }

    private static void editSelected(IncomeSourcesView income, PensionPlanClassification classification,
                                     ButtonType confirm) throws Exception {
        var answer = new FutureTask<>(() -> {
            var pane = Window.getWindows().stream().filter(Window::isShowing)
                    .map(window -> window.getScene().getRoot())
                    .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                    .findFirst().orElseThrow();
            try {
                @SuppressWarnings("unchecked")
                var selection = (ComboBox<PensionPlanClassification>) pane.lookup("#pension-plan-classification");
                assertNotNull(selection);
                selection.setValue(classification);
            } finally {
                ((Button) pane.lookupButton(confirm)).fire();
            }
            return null;
        });
        Platform.runLater(answer);
        ((Button) control(income, "editButton")).fire();
        assertTrue(answer.isDone());
        answer.get();
    }

    private static void invoke(MainWindow window, String name) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(window);
    }

    private static void invoke(MainWindow window, String name, Class<?> type, Object argument) throws Exception {
        var method = MainWindow.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(window, argument);
    }
}
