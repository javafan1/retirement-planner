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

class AssumptionsMainWindowTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void startFx() throws Exception {
        AssumptionsViewTest.startFx();
    }

    @Test
    void invalidApplyAndSaveDoNotChangeModelCacheRevisionTitleOrDisk() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("invalid.json");
            f.controller.saveAs(file);
            Stage stage = new Stage();
            f.window.setStage(stage);
            var before = f.controller.getCurrentPlan().getPlanningAssumptions();
            var projection = f.controller.getCurrentProjection();
            long revision = f.controller.getSourcePlanRevision();
            String json = Files.readString(file);
            String title = stage.getTitle();
            field(f.assumptions, "investmentReturnField").setText("8");
            field(f.assumptions, "estimatedHeirTaxRateField").setText("invalid");
            assertFalse(f.assumptions.applyChanges());
            assertEquals(false, invoke(f.window, "onSave"));
            assertSame(before, f.controller.getCurrentPlan().getPlanningAssumptions());
            assertSame(projection, f.controller.getCurrentProjection());
            assertEquals(revision, f.controller.getSourcePlanRevision());
            assertFalse(f.controller.isModified());
            assertEquals(title, stage.getTitle());
            assertEquals(json, Files.readString(file));
            dirty(f.assumptions);
        });
    }

    @Test
    void successfulApplyRefreshesCacheAndRevisesExactlyOnceAndCancelKeepsTitleDirty() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Stage stage = new Stage();
            f.window.setStage(stage);
            var projection = f.controller.getCurrentProjection();
            long revision = f.controller.getSourcePlanRevision();
            AtomicInteger events = new AtomicInteger();
            f.controller.addSourcePlanRevisionListener(events::incrementAndGet);
            field(f.assumptions, "inflationRateField").setText("3.75");
            assertFalse(f.controller.isModified());
            assertTrue(f.assumptions.applyChanges());
            assertEquals(1, events.get());
            assertEquals(revision + 1, f.controller.getSourcePlanRevision());
            assertNotSame(projection, f.controller.getCurrentProjection());
            assertTrue(stage.getTitle().endsWith(" *"));
            field(f.assumptions, "inflationRateField").setText("4");
            f.assumptions.cancelChanges();
            assertEquals("3.75", field(f.assumptions, "inflationRateField").getText());
            assertTrue(f.controller.isModified());
            assertTrue(stage.getTitle().endsWith(" *"));
            assertEquals(1, events.get());
            clean(f.assumptions);
        });
    }

    @Test
    void bothViewsCanHaveDraftsAndCancellingEitherLeavesTheOtherDirty() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            householdName(f.household).setText("Household draft");
            field(f.assumptions, "inflationRateField").setText("3.75");
            invoke(f.window, "refreshAllViews");
            dirty(f.assumptions);
            assertTrue(f.household.isDirty());
            f.assumptions.cancelChanges();
            assertTrue(f.household.isDirty());
            field(f.assumptions, "inflationRateField").setText("4.25");
            f.household.cancelChanges();
            dirty(f.assumptions);
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void globalSaveAppliesBothViewsOnceAndPersistsAndClearsApplicationDirty() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("both.json");
            f.controller.saveAs(file);
            long revision = f.controller.getSourcePlanRevision();
            householdName(f.household).setText("Saved name");
            field(f.assumptions, "inflationRateField").setText("3.75");
            assertEquals(true, invoke(f.window, "onSave"));
            clean(f.assumptions);
            assertFalse(f.household.isDirty());
            assertFalse(f.controller.isModified());
            assertEquals(revision + 2, f.controller.getSourcePlanRevision());
            f.controller.open(file);
            invoke(f.window, "loadCurrentPlan");
            assertEquals("Saved name", householdName(f.household).getText());
            assertEquals("3.75", field(f.assumptions, "inflationRateField").getText());
        });
    }

    @Test
    void invalidAssumptionsBlocksApplyingPendingHouseholdAndGuardSave() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("guard.json");
            f.controller.saveAs(file);
            String json = Files.readString(file);
            String name = f.controller.getCurrentPlan().getHousehold().getPrimaryPerson().getFirstName();
            householdName(f.household).setText("Pending name");
            field(f.assumptions, "estimatedHeirTaxRateField").setText("invalid");
            AtomicInteger prompts = answerNextPrompt("Save");
            assertEquals(false, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            assertEquals(name, f.controller.getCurrentPlan().getHousehold().getPrimaryPerson().getFirstName());
            assertEquals(json, Files.readString(file));
            assertFalse(f.controller.isModified());
            assertTrue(f.household.isDirty());
            dirty(f.assumptions);
        });
    }

    @Test
    void invalidHouseholdBlocksApplyingValidAssumptions() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            PersonCard card = (PersonCard) ((TitledPane) f.household.getChildren().getFirst()).getContent();
            ((DatePicker) card.getChildren().get(5)).getEditor().setText("invalid");
            field(f.assumptions, "inflationRateField").setText("3.75");
            var before = f.controller.getCurrentPlan().getPlanningAssumptions();
            assertEquals(false, invoke(f.window, "saveCurrentPlan"));
            assertSame(before, f.controller.getCurrentPlan().getPlanningAssumptions());
            dirty(f.assumptions);
            assertTrue(f.household.isDirty());
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void guardSaveAppliesAndPersistsValidAssumptionsWithOnePrompt() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("saved-departure.json");
            f.controller.saveAs(file);
            field(f.assumptions, "inflationRateField").setText("3.75");
            AtomicInteger prompts = answerNextPrompt("Save");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            assertFalse(f.controller.isModified());
            clean(f.assumptions);
            f.controller.open(file);
            invoke(f.window, "loadCurrentPlan");
            assertEquals("3.75", field(f.assumptions, "inflationRateField").getText());
        });
    }

    @Test
    void invalidUntitledAssumptionsBlocksSaveAsBeforeNativeFileChooser() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            field(f.assumptions, "inflationRateField").setText("invalid");
            assertFalse(f.controller.hasCurrentFile());
            assertEquals(false, invoke(f.window, "onSaveAs"));
            AtomicInteger prompts = answerNextPrompt("Save");
            assertEquals(false, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            dirty(f.assumptions);
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void guardDiscardClearsDraftsWithoutClearingPreviouslyAppliedDirtyState() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            field(f.assumptions, "inflationRateField").setText("3.75");
            assertTrue(f.assumptions.applyChanges());
            field(f.assumptions, "inflationRateField").setText("invalid");
            householdName(f.household).setText("Draft name");
            AtomicInteger prompts = answerNextPrompt("Discard");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            assertEquals(1, prompts.get());
            clean(f.assumptions);
            assertFalse(f.household.isDirty());
            assertTrue(f.controller.isModified());
            assertEquals("3.75", field(f.assumptions, "inflationRateField").getText());
            assertEquals("", status(f.assumptions).getText());
        });
    }

    @Test
    void cancelAbortsNewOpenExitAndWindowCloseWithPendingAssumptionsOnly() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Stage stage = new Stage();
            stage.setScene(f.window.createScene());
            f.window.setStage(stage);
            var plan = f.controller.getCurrentPlan();
            field(f.assumptions, "inflationRateField").setText("invalid");
            for (String operation : List.of("onNew", "onOpen", "onExit")) {
                AtomicInteger prompts = answerNextPrompt("Cancel");
                invoke(f.window, operation);
                assertEquals(1, prompts.get());
                assertSame(plan, f.controller.getCurrentPlan());
                assertEquals("invalid", field(f.assumptions, "inflationRateField").getText());
                dirty(f.assumptions);
            }
            AtomicInteger prompts = answerNextPrompt("Cancel");
            WindowEvent event = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(event);
            assertTrue(event.isConsumed());
            assertEquals(1, prompts.get());
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void discardThenNewResetsBothBaselines() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            var before = f.controller.getCurrentPlan();
            field(f.assumptions, "inflationRateField").setText("invalid");
            householdName(f.household).setText("Draft");
            AtomicInteger prompts = answerNextPrompt("Discard");
            invoke(f.window, "onNew");
            assertEquals(1, prompts.get());
            assertNotSame(before, f.controller.getCurrentPlan());
            clean(f.assumptions);
            assertFalse(f.household.isDirty());
            assertFalse(f.controller.isModified());
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
        });
    }

    @Test
    void guardedOpenReplacementLoadsNewAssumptionsBaseline() throws Exception {
        fx(() -> {
            WindowFixture f = new WindowFixture();
            Path file = temporaryDirectory.resolve("replacement.json");
            f.controller.saveAs(file);
            field(f.assumptions, "inflationRateField").setText("invalid");
            answerNextPrompt("Discard");
            assertEquals(true, invoke(f.window, "confirmPlanDeparture"));
            // Exercise the post-file-chooser Open path without automating a native dialog.
            f.controller.open(file);
            invoke(f.window, "loadCurrentPlan");
            clean(f.assumptions);
            assertFalse(f.controller.isModified());
            assertNotEquals("invalid", field(f.assumptions, "inflationRateField").getText());
        });
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

        WindowFixture() throws Exception {
            controller = control(window, "controller");
            assumptions = control(window, "assumptionsView");
            household = control(window, "householdView");
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
            invoke(window, "loadCurrentPlan");
        }
    }
}
