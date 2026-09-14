package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.ui.MainWindow;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.ui.views.AssumptionsViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RothConversionMainWindowTest {
    @TempDir Path temporaryDirectory;
    @BeforeAll static void startFx() throws Exception { AssumptionsViewTest.startFx(); }

    @Test void invalidApplyAndSaveLeaveModelCacheRevisionAndDiskUntouched() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("invalid.json");
            f.controller.saveAs(file);
            var before = f.controller.getCurrentPlan().getRothConversionRequest();
            var cache = f.controller.getCurrentProjection();
            long revision = f.controller.getSourcePlanRevision();
            String json = Files.readString(file);
            AtomicInteger events = new AtomicInteger();
            f.controller.addSourcePlanRevisionListener(events::incrementAndGet);
            field(f.roth, "conversionYearField").setText("2027");
            field(f.roth, "conversionAmountField").setText("invalid");
            ((Button) control(f.roth, "applyButton")).fire();
            assertEquals(false, invoke(f.window, "onSave"));
            assertSame(before, f.controller.getCurrentPlan().getRothConversionRequest());
            assertSame(cache, f.controller.getCurrentProjection());
            assertEquals(revision, f.controller.getSourcePlanRevision());
            assertEquals(0, events.get());
            assertFalse(f.controller.isModified());
            assertEquals(json, Files.readString(file));
            RothConversionViewTest.dirty(f.roth);
        });
    }

    @Test void applyThenCancelPreservesAppliedUnsavedValueAndOtherDrafts() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("applied.json");
            f.controller.saveAs(file);
            String json = Files.readString(file);
            var cache = f.controller.getCurrentProjection();
            long revision = f.controller.getSourcePlanRevision();
            householdName(f.household).setText("Household draft");
            field(f.assumptions, "inflationRateField").setText("3.75");
            field(f.roth, "conversionYearField").setText("2027");
            assertTrue(f.roth.applyChanges());
            assertEquals(revision + 1, f.controller.getSourcePlanRevision());
            assertNotSame(cache, f.controller.getCurrentProjection());
            assertEquals(json, Files.readString(file));
            field(f.roth, "conversionYearField").setText("invalid");
            f.roth.cancelChanges();
            assertEquals(2027, f.controller.getCurrentPlan().getRothConversionRequest().getStartYear());
            assertTrue(f.controller.isModified());
            assertTrue(f.household.isDirty());
            assertTrue(f.assumptions.isDirty());
            RothConversionViewTest.clean(f.roth);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,false", "false,true", "true,true"})
    void savePersistsAllDraftCombinationsAndRefreshPreservesThem(boolean household, boolean assumptions) throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("combined.json");
            f.controller.saveAs(file);
            long revision = f.controller.getSourcePlanRevision();
            if (household) householdName(f.household).setText("Saved name");
            if (assumptions) field(f.assumptions, "inflationRateField").setText("3.75");
            field(f.roth, "conversionYearField").setText("2027");
            invoke(f.window, "refreshAllViews");
            assertEquals(household, f.household.isDirty());
            assertEquals(assumptions, f.assumptions.isDirty());
            RothConversionViewTest.dirty(f.roth);
            AtomicInteger prompts = answerNextPrompt("Save");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            assertEquals(revision + 1 + (household ? 1 : 0) + (assumptions ? 1 : 0),
                    f.controller.getSourcePlanRevision());
            assertFalse(f.controller.isModified());
            assertFalse(f.household.isDirty());
            assertFalse(f.assumptions.isDirty());
            RothConversionViewTest.clean(f.roth);
            f.controller.open(file);
            invoke(f.window, "loadCurrentPlan");
            assertEquals("2027", field(f.roth, "conversionYearField").getText());
            if (household) assertEquals("Saved name", householdName(f.household).getText());
            if (assumptions) assertEquals("3.75", field(f.assumptions, "inflationRateField").getText());
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Household", "Assumptions", "Roth"})
    void anyInvalidViewBlocksAllWritesAndPersistence(String invalid) throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("blocked.json");
            f.controller.saveAs(file);
            String json = Files.readString(file);
            var roth = f.controller.getCurrentPlan().getRothConversionRequest();
            var assumptions = f.controller.getCurrentPlan().getPlanningAssumptions();
            String name = householdName(f.household).getText();
            householdName(f.household).setText("Pending name");
            field(f.assumptions, "inflationRateField").setText("3.75");
            field(f.roth, "conversionYearField").setText("2027");
            switch (invalid) {
                case "Household" -> {
                    PersonCard card = (PersonCard) ((TitledPane) f.household.getChildren().getFirst()).getContent();
                    ((DatePicker) card.getChildren().get(5)).getEditor().setText("invalid");
                }
                case "Assumptions" -> field(f.assumptions, "inflationRateField").setText("invalid");
                case "Roth" -> field(f.roth, "conversionAmountField").setText("invalid");
            }
            AtomicInteger prompts = answerNextPrompt("Save");
            assertEquals(false, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            assertSame(roth, f.controller.getCurrentPlan().getRothConversionRequest());
            assertSame(assumptions, f.controller.getCurrentPlan().getPlanningAssumptions());
            assertEquals(name, f.controller.getCurrentPlan().getHousehold().getPrimaryPerson().getFirstName());
            assertEquals(json, Files.readString(file));
            assertFalse(f.controller.isModified());
            assertTrue(f.household.isDirty());
            assertTrue(f.assumptions.isDirty());
            RothConversionViewTest.dirty(f.roth);
        });
    }

    @Test void invalidRothBlocksUntitledSaveAsBeforeChooser() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            field(f.roth, "conversionAmountField").setText("invalid");
            assertFalse(f.controller.hasCurrentFile());
            assertEquals(false, invoke(f.window, "onSaveAs"));
            assertEquals(false, invoke(f.window, "onSave"));
            assertFalse(f.controller.isModified());
            RothConversionViewTest.dirty(f.roth);
        });
    }

    @Test void cancelAbortsNewOpenExitAndCloseWithOnePromptEach() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Stage stage = new Stage();
            stage.setScene(f.window.createScene());
            f.window.setStage(stage);
            var plan = f.controller.getCurrentPlan();
            field(f.roth, "conversionAmountField").setText("invalid");
            for (String operation : List.of("onNew", "onOpen", "onExit")) {
                AtomicInteger prompts = answerNextPrompt("Cancel");
                invoke(f.window, operation);
                assertEquals(1, prompts.get());
                assertSame(plan, f.controller.getCurrentPlan());
                RothConversionViewTest.dirty(f.roth);
            }
            AtomicInteger prompts = answerNextPrompt("Cancel");
            WindowEvent event = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(event);
            assertTrue(event.isConsumed());
            assertEquals(1, prompts.get());
        });
    }

    @Test void discardClearsAllDraftsAndNewResetsBaseline() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            field(f.roth, "conversionYearField").setText("2027");
            assertTrue(f.roth.applyChanges());
            field(f.roth, "conversionYearField").setText("invalid");
            householdName(f.household).setText("Pending name");
            field(f.assumptions, "inflationRateField").setText("invalid");
            AtomicInteger prompts = answerNextPrompt("Discard");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            RothConversionViewTest.clean(f.roth);
            assertFalse(f.household.isDirty());
            assertFalse(f.assumptions.isDirty());
            assertTrue(f.controller.isModified());
            assertEquals("2027", field(f.roth, "conversionYearField").getText());
            answerNextPrompt("Discard");
            invoke(f.window, "onNew");
            RothConversionViewTest.clean(f.roth);
            assertNull(f.controller.getCurrentPlan().getRothConversionRequest());
            assertFalse(f.controller.isModified());
        });
    }

    @Test void guardedOpenResetsDraftAfterReplacement() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("replacement.json");
            f.controller.saveAs(file);
            field(f.roth, "conversionAmountField").setText("invalid");
            answerNextPrompt("Discard");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            f.controller.open(file);
            invoke(f.window, "loadCurrentPlan");
            RothConversionViewTest.clean(f.roth);
            assertEquals("1000", field(f.roth, "conversionAmountField").getText());
            assertFalse(f.controller.isModified());
        });
    }

    @Test void applyingAndCancellingReferenceViewsPreservesRothDraftAcrossTabNavigation() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            field(f.roth, "conversionAmountField").setText("invalid");
            householdName(f.household).setText("Applied name");
            assertTrue(f.household.applyChanges());
            field(f.assumptions, "inflationRateField").setText("3.75");
            assertTrue(f.assumptions.applyChanges());
            householdName(f.household).setText("Cancelled name");
            f.household.cancelChanges();
            field(f.assumptions, "inflationRateField").setText("4");
            f.assumptions.cancelChanges();
            SceneNavigation.visitTabs(f.window.createScene().getRoot());
            assertEquals("invalid", field(f.roth, "conversionAmountField").getText());
            RothConversionViewTest.dirty(f.roth);
        });
    }

    private static class SceneNavigation {
        static void visitTabs(javafx.scene.Node node) {
            if (node instanceof TabPane tabs) {
                for (Tab tab : tabs.getTabs()) {
                    tabs.getSelectionModel().select(tab);
                    visitTabs(tab.getContent());
                }
            }
            else if (node instanceof javafx.scene.Parent parent) {
                for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) visitTabs(child);
            }
        }
    }
    private static TextField householdName(HouseholdView view) {
        PersonCard card = (PersonCard) ((TitledPane) view.getChildren().getFirst()).getContent();
        return (TextField) card.getChildren().get(1);
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static AtomicInteger answerNextPrompt(String text) {
        AtomicInteger count = new AtomicInteger();
        Platform.runLater(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window.getScene().getRoot() instanceof DialogPane pane) {
                    pane.getButtonTypes().stream().filter(type -> type.getText().equals(text))
                            .findFirst().ifPresent(type -> {
                                count.incrementAndGet();
                                ((Button) pane.lookupButton(type)).fire();
                            });
                }
            }
        });
        return count;
    }

    private static class WindowFixture {
        final MainWindow window = new MainWindow();
        final ApplicationController controller;
        final AssumptionsView assumptions;
        final HouseholdView household;
        final RothConversionView roth;

        WindowFixture() throws Exception {
            controller = control(window, "controller");
            assumptions = control(window, "assumptionsView");
            household = control(window, "householdView");
            roth = control(window, "rothConversionView");
            controller.newPlan();
            var plan = controller.getCurrentPlan();
            plan.getHousehold().getPrimaryPerson().setMortalityCategory(MortalityCategory.MALE);
            plan.getHousehold().getSpouse().setMortalityCategory(MortalityCategory.FEMALE);
            plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 1, 1));
            plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1962, 1, 1));
            plan.getAccountPortfolio().addAccount(new BrokerageAccount(
                    "Test funding", AccountOwnership.PRIMARY, new BigDecimal("10000000")));
            PlanningAssumptions a = plan.getPlanningAssumptions();
            plan.setPlanningAssumptions(new PlanningAssumptions(
                    a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                    a.getDeathScenarioAssumptions(), 3, LocalDate.of(2026, 1, 1)));
            plan.setRothConversionRequest(new com.daviddunn.retirementplanner.domain.roth.RothConversionRequest(
                    true, 2026, new BigDecimal("1000"),
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule.FIRST_HOUSEHOLD_RMD,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy.FIXED_AMOUNT,
                    com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency.ONE_TIME));
            invoke(window, "loadCurrentPlan");
        }
    }
}
