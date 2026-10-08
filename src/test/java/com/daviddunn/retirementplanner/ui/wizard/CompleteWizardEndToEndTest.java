package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluator;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.views.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CompleteWizardEndToEndTest {
    @TempDir Path folder;
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fileNewThroughDialogsCreatesNormalTabsPersistsProjectsAndAnalyzes(boolean couple) throws Exception {
        fx(() -> {
            var window = new MainWindow();
            ApplicationController controller = field(window, "controller");
            var previous = controller.getCurrentPlan();
            createFromMenu(window, couple, false);
            var plan = controller.getCurrentPlan();
            assertNotSame(previous, plan);
            assertEquals(couple ? 2 : 1, plan.getHousehold().members().size());
            assertEquals(couple ? 4 : 3, plan.getAccountPortfolio().getAccounts().size());
            assertEquals(2, plan.getHousehold().getPrimaryPerson().getIncomeSources().size());
            if (couple) assertEquals(2, plan.getHousehold().getSpouse().getIncomeSources().size());
            assertEquals(2, plan.getHousehold().getExpenses().size());
            assertEquals(new BigDecimal("0.065"), plan.getPlanningAssumptions().getExpectedAnnualInvestmentReturn());
            assertEquals(couple ? com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY
                    : com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE, plan.getPlanningAssumptions().getTaxAssumptions().getFilingStatus());
            assertFalse(controller.hasCurrentFile());
            assertFalse(controller.isModified());
            try (var files = Files.list(folder)) {
                assertEquals(0, files.count());
            }
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var expected = mapper.valueToTree(plan);
            AccountsView accounts = field(window, "accountsView");
            assertEquals(couple ? 4 : 3, ((TableView<?>) field(accounts, "table")).getItems().size());
            IncomeSourcesView income = field(window, "incomeSourcesView");
            assertEquals(couple ? 4 : 2, income.getTable().getItems().size());
            ExpensesView expenses = field(window, "expensesView");
            assertEquals(2, ((TableView<?>) field(expenses, "table")).getItems().size());
            AssumptionsView assumptions = field(window, "assumptionsView");
            assertEquals("6.5", ((TextField) field(assumptions, "investmentReturnField")).getText());
            HouseholdView household = field(window, "householdView");
            com.daviddunn.retirementplanner.ui.components.PersonCard card = field(household, "primaryPersonCard");
            assertEquals("Alex", ((TextField) field(card, "firstNameField")).getText());
            assertEquals(MortalityCategory.FEMALE, ((ComboBox<?>) field(card, "mortalityCategory")).getValue());
            assertEquals(couple, ((CheckBox) field(household, "includeSpouse")).isSelected());
            ResultsView results = field(window, "resultsView");
            assertEquals(30, results.getTable().getItems().size());
            assertEquals(30, controller.getCurrentProjection().getYears().size());
            assertEquals(Boolean.TRUE, invoke(window, "saveCurrentPlan"));
            Path file = folder.resolve(couple ? "couple.json" : "single.json");
            // Use the real Save As controller path without automating the native file chooser.
            controller.saveAs(file);
            assertEquals(Boolean.TRUE, invoke(window, "onSave"));
            controller.open(file);
            invoke(window, "loadCurrentPlan");
            assertEquals(expected, mapper.valueToTree(controller.getCurrentPlan()));
            assertEquals(30, results.getTable().getItems().size());
            assertEquals(couple, controller.getCurrentPlan().getHousehold().hasSpouse());
            var integrated = new IntegratedSocialSecurityStrategyEvaluator().evaluateCurrentStrategy(controller.getCurrentPlan());
            assertEquals(30, integrated.projection().getYears().size());
            assertTrue(integrated.metrics().lifetimeHouseholdSocialSecurity().signum() > 0);
            var settings = MonteCarloSettings.forPlan(controller.getCurrentPlan(), 3, 417, BigDecimal.ZERO);
            var mc = new MonteCarloAnalyzer().analyze(controller.getCurrentPlan(), settings);
            assertEquals(3, mc.outcomes().size());
            assertEquals(expected, mapper.valueToTree(controller.getCurrentPlan()), "Projection/analyzers must preserve persisted plan values");
        });
    }

    @Test void substantialDraftCancelPreservesExistingDirtyPlanFileRevisionAndUnappliedEdits() throws Exception {
        fx(() -> {
            var window = new MainWindow();
            ApplicationController controller = field(window, "controller");
            var previous = controller.getCurrentPlan();
            Path file = folder.resolve("original.json");
            controller.saveAs(file);
            controller.markModified();
            long revision = controller.getSourcePlanRevision();
            String saved = Files.readString(file);
            AssumptionsView assumptions = field(window, "assumptionsView");
            ((TextField) field(assumptions, "inflationRateField")).setText("pending original draft");
            createFromMenu(window, true, true);
            assertSame(previous, controller.getCurrentPlan());
            assertTrue(controller.isModified());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals(file, controller.getCurrentFile());
            assertEquals(saved, Files.readString(file));
            assertEquals("pending original draft", ((TextField) field(assumptions, "inflationRateField")).getText());
            assertTrue(assumptions.isDirty());
        });
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void fileNewCancelFromEveryStepPreservesActiveDirtyPlan(int target) throws Exception {
        fx(() -> {
            var window = new MainWindow();
            ApplicationController controller = field(window, "controller");
            controller.markModified();
            var previous = controller.getCurrentPlan();
            long revision = controller.getSourcePlanRevision();
            var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            var before = mapper.valueToTree(previous);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() -> {
                DialogPane pane = activeWizardPane();
                try {
                    fill(person(pane, "primary"), "Draft", "Only", java.time.LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                    for (int index = 0; index < target; index++) {
                        assertSame(previous, controller.getCurrentPlan());
                        assertTrue(controller.isModified());
                        button(pane, "Next").fire();
                    }
                    assertEquals(before, mapper.valueToTree(previous));
                }
                catch (Throwable exception) { failure.set(exception); }
                finally { button(pane, "Cancel").fire(); }
            });
            BorderPane root = (BorderPane) window.createScene().getRoot();
            ((MenuBar) root.getTop()).getMenus().getFirst().getItems().stream().filter(item -> item.getText().equals("New"))
                    .findFirst().orElseThrow().fire();
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertSame(previous, controller.getCurrentPlan());
            assertTrue(controller.isModified());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals(before, mapper.valueToTree(controller.getCurrentPlan()));
        });
    }

    @Test void backwardSpouseRemovalAfterCompleteEntryShowsDependenciesAndPreservesEverything() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                CompleteWizardFixtures.enterThroughReview(pane, true);
                NewPlanDraft draft = field(wizard, "draft");
                var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
                var before = mapper.valueToTree(draft.getPlan());
                ((Button) pane.lookup("#wizard-review-edit-household")).fire();
                ((Button) pane.lookup("#wizard-remove-spouse")).fire();
                String error = ((Label) pane.lookup("#wizard-validation")).getText();
                assertTrue(error.contains("Spouse IRA"));
                assertTrue(error.contains("Investments"));
                assertTrue(error.contains("Social Security"));
                assertTrue(error.contains("pension"));
                assertNotNull(person(pane, "spouse"));
                assertEquals(before, mapper.valueToTree(draft.getPlan()));
            }
            finally { wizard.close(); }
        });
    }

    private static void createFromMenu(MainWindow window, boolean couple, boolean cancel) throws Exception {
        ApplicationController controller = field(window, "controller");
        var previous = controller.getCurrentPlan();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            DialogPane pane = activeWizardPane();
            try {
                CompleteWizardFixtures.enterThroughReview(pane, couple);
                assertSame(previous, controller.getCurrentPlan());
                button(pane, cancel ? "Cancel" : "Create Plan").fire();
            }
            catch (Throwable exception) {
                failure.set(exception);
                button(pane, "Cancel").fire();
            }
        });
        BorderPane root = (BorderPane) window.createScene().getRoot();
        ((MenuBar) root.getTop()).getMenus().getFirst().getItems().stream().filter(item -> item.getText().equals("New"))
                .findFirst().orElseThrow().fire();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        var method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
