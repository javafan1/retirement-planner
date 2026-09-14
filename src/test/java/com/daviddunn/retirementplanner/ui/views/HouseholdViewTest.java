package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HouseholdViewTest {
    @TempDir Path temporaryDirectory;

    @BeforeAll
    static void startFx() throws Exception {
        FutureTask<Void> startup = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(startup);
        }
        catch (IllegalStateException alreadyStarted) {
            Platform.runLater(startup);
        }
        startup.get(20, TimeUnit.SECONDS);
    }

    @Test
    void initialAndUnloadedStateAreClean() throws Exception {
        fx(() -> {
            clean(new HouseholdView());
            clean(new Fixture().view);
        });
    }

    @Test
    void mortalityCategoryLoadsEditsCancelsAndPersistsWithOneNotification() throws Exception {
        fx(() -> {
            var f = new Fixture();
            assertEquals(MortalityCategory.MALE, category(f.view, 0).getValue());
            assertEquals(MortalityCategory.FEMALE, category(f.view, 1).getValue());
            assertEquals("Male", category(f.view, 0).getConverter().toString(MortalityCategory.MALE));
            assertEquals("Female", category(f.view, 1).getConverter().toString(MortalityCategory.FEMALE));
            assertTrue(category(f.view, 0).getTooltip().getText().contains("mortality-table category"));
            category(f.view, 0).setValue(MortalityCategory.FEMALE);
            dirty(f.view);
            assertEquals(MortalityCategory.MALE, f.primary().getMortalityCategory());
            f.view.cancelChanges();
            assertEquals(MortalityCategory.MALE, category(f.view, 0).getValue());
            category(f.view, 0).setValue(MortalityCategory.FEMALE);
            category(f.view, 1).setValue(MortalityCategory.MALE);
            assertTrue(f.view.applyChanges());
            clean(f.view);
            assertEquals(1, f.notifications.get());
            assertTrue(f.controller.isModified());
            Path file = temporaryDirectory.resolve("categories.json");
            f.controller.saveAs(file);
            f.controller.open(file);
            f.view.load(f.controller.getCurrentPlan());
            assertEquals(MortalityCategory.FEMALE, category(f.view, 0).getValue());
            assertEquals(MortalityCategory.MALE, category(f.view, 1).getValue());
        });
    }

    @Test
    void missingMortalityCategoryRejectsEntireEditAndLegacyCardShowsRequired() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Unapplied");
            category(f.view, 1).setValue(null);
            assertFalse(f.view.applyChanges());
            assertEquals("Mortality category is required.", status(f.view).getText());
            assertEquals("Primary", f.primary().getFirstName());
            assertEquals(0, f.notifications.get());
            assertFalse(f.controller.isModified());
            var card = new PersonCard();
            card.load(new Person("Legacy", "Person", LocalDate.of(1960, 1, 1)));
            var input = (ComboBox<?>) card.getChildren().get(7);
            assertNull(input.getValue());
            assertEquals("Required", input.getPromptText());
            assertEquals("Mortality category is required.",
                    assertThrows(IllegalArgumentException.class, card::readValidated).getMessage());
            var legacy = new com.daviddunn.retirementplanner.domain.model.RetirementPlan(
                    new com.daviddunn.retirementplanner.domain.model.Household(
                            new Person("Legacy", "Person", LocalDate.of(1960, 1, 1)), f.spouse()),
                    f.controller.getCurrentPlan().getAccountPortfolio(),
                    f.controller.getCurrentPlan().getPlanningAssumptions());
            f.view.load(legacy);
            assertFalse(f.view.save(legacy));
            assertEquals("Mortality category is required.", status(f.view).getText());
        });
    }

    @Test
    void namesAndMultipleFieldsRevertToCleanWithoutModelMutation() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Changed");
            dirty(f.view);
            assertEquals("Primary", f.primary().getFirstName());
            assertFalse(f.controller.isModified());
            last(f.view, 1).setText("Other");
            first(f.view, 0).setText("Primary");
            dirty(f.view);
            last(f.view, 1).setText("Family");
            clean(f.view);
        });
    }

    @Test
    void selectedAndTypedDatesParticipateInDirtyDetection() throws Exception {
        fx(() -> {
            var f = new Fixture();
            var picker = date(f.view, 0);
            picker.setValue(LocalDate.of(1961, 2, 3));
            dirty(f.view);
            picker.setValue(f.primary().getBirthDate());
            clean(f.view);
            picker.getEditor().setText("invalid date");
            dirty(f.view);
            picker.getEditor().setText(picker.getConverter().toString(f.primary().getBirthDate()));
            clean(f.view);
        });
    }

    @Test
    void applyWritesAllSixFieldsAndNotifiesExactlyOnce() throws Exception {
        fx(() -> {
            var f = new Fixture();
            for (int member = 0; member < 2; member++) {
                first(f.view, member).setText("First " + member);
                last(f.view, member).setText("Last " + member);
                var picker = date(f.view, member);
                picker.getEditor().setText(picker.getConverter().toString(LocalDate.of(1965 + member, 3, 4)));
            }
            button(f.view, 0).fire();
            for (int member = 0; member < 2; member++) {
                Person person = member == 0 ? f.primary() : f.spouse();
                assertEquals("First " + member, person.getFirstName());
                assertEquals("Last " + member, person.getLastName());
                assertEquals(LocalDate.of(1965 + member, 3, 4), person.getBirthDate());
            }
            clean(f.view);
            assertTrue(f.controller.isModified());
            assertEquals(1, f.notifications.get());
            assertTrue(f.view.applyChanges());
            assertEquals(1, f.notifications.get());
            f.view.load(f.controller.getCurrentPlan());
            assertEquals("First 0", first(f.view, 0).getText());
        });
    }

    @Test
    void invalidSpouseDateRejectsEntireApplyAndSave() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Changed");
            date(f.view, 1).getEditor().setText("not a date");
            assertFalse(f.view.applyChanges());
            assertFalse(f.view.save(f.controller.getCurrentPlan()));
            assertEquals("Primary", f.primary().getFirstName());
            assertEquals(LocalDate.of(1962, 1, 1), f.spouse().getBirthDate());
            assertFalse(f.controller.isModified());
            assertEquals(0, f.notifications.get());
            dirty(f.view);
            assertFalse(status(f.view).getText().isEmpty());
            button(f.view, 1).fire();
            clean(f.view);
            assertEquals("", status(f.view).getText());
        });
    }

    @Test
    void missingDateRejectedWithoutClearingExistingApplicationDirty() throws Exception {
        fx(() -> {
            var f = new Fixture();
            f.controller.markModified();
            date(f.view, 0).getEditor().clear();
            assertFalse(f.view.applyChanges());
            assertTrue(f.controller.isModified());
            dirty(f.view);
            f.view.cancelChanges();
            clean(f.view);
            assertTrue(f.controller.isModified());
        });
    }

    @Test
    void cancelRestoresEveryFieldWithoutChangingCleanModel() throws Exception {
        fx(() -> {
            var f = new Fixture();
            for (int member = 0; member < 2; member++) {
                first(f.view, member).setText("Changed");
                last(f.view, member).setText("Changed");
                date(f.view, member).getEditor().clear();
            }
            button(f.view, 1).fire();
            assertEquals("Primary", first(f.view, 0).getText());
            assertEquals("Spouse", first(f.view, 1).getText());
            for (int member = 0; member < 2; member++) {
                assertEquals("Family", last(f.view, member).getText());
                assertEquals(LocalDate.of(1960 + member * 2, 1, 1), date(f.view, member).getValue());
            }
            clean(f.view);
            assertFalse(f.controller.isModified());
            assertEquals(0, f.notifications.get());
        });
    }

    @Test
    void applyThenEditThenCancelPreservesAppliedChangeAndDirtyModel() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Applied");
            f.view.applyChanges();
            first(f.view, 0).setText("Discarded");
            f.view.cancelChanges();
            assertEquals("Applied", first(f.view, 0).getText());
            assertEquals("Applied", f.primary().getFirstName());
            clean(f.view);
            assertTrue(f.controller.isModified());
            assertEquals(1, f.notifications.get());
        });
    }

    @Test
    void tabNavigationAndOtherViewRefreshPreservePendingEdits() throws Exception {
        fx(() -> {
            var f = new Fixture();
            var tabs = new TabPane(new Tab("Household", f.view), new Tab("Other"));
            first(f.view, 0).setText("Draft");
            tabs.getSelectionModel().select(1);
            f.controller.markModified();
            f.view.refresh(f.controller.getCurrentPlan());
            tabs.getSelectionModel().select(0);
            assertEquals("Draft", first(f.view, 0).getText());
            dirty(f.view);
            f.view.cancelChanges();
            assertTrue(f.controller.isModified());
        });
    }

    @Test
    void reloadAndNewPlanResetBaselineAndNeverLeakDrafts() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Draft");
            f.view.load(f.controller.getCurrentPlan());
            clean(f.view);
            first(f.view, 0).setText("Another draft");
            f.controller.newPlan();
            f.view.refresh(f.controller.getCurrentPlan());
            clean(f.view);
            assertNotEquals("Another draft", first(f.view, 0).getText());
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void applySaveAndOpenUseExistingControllerPersistenceState() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText("Saved");
            assertTrue(f.view.save(f.controller.getCurrentPlan()));
            assertTrue(f.controller.isModified());
            Path file = temporaryDirectory.resolve("household.json");
            f.controller.saveAs(file);
            assertFalse(f.controller.isModified());
            clean(f.view);
            first(f.view, 0).setText("Unapplied");
            f.controller.open(file);
            f.view.load(f.controller.getCurrentPlan());
            assertEquals("Saved", first(f.view, 0).getText());
            clean(f.view);
            assertFalse(f.controller.isModified());
        });
    }

    @Test
    void meaningfulNameWhitespaceIsPreserved() throws Exception {
        fx(() -> {
            var f = new Fixture();
            first(f.view, 0).setText(" Primary ");
            dirty(f.view);
            assertTrue(f.view.applyChanges());
            assertEquals(" Primary ", f.primary().getFirstName());
            clean(f.view);
        });
    }

    @Test
    void mainWindowSaveValidationAndDepartureGuardProtectDrafts() throws Exception {
        fx(() -> {
            var window = new com.daviddunn.retirementplanner.ui.MainWindow();
            var controllerField = window.getClass().getDeclaredField("controller");
            controllerField.setAccessible(true);
            var controller = (ApplicationController) controllerField.get(window);
            controller.newPlan();
            invoke(window, "loadCurrentPlan");
            var viewField = window.getClass().getDeclaredField("householdView");
            viewField.setAccessible(true);
            var view = (HouseholdView) viewField.get(window);
            assertEquals(true, invoke(window, "confirmPlanDeparture"));
            first(view, 0).setText("Draft");
            assertEquals(false, invoke(window, "saveCurrentPlan"));
            assertFalse(controller.isModified());
            answerNextPrompt("Cancel");
            assertEquals(false, invoke(window, "confirmPlanDeparture"));
            assertEquals("Draft", first(view, 0).getText());
            answerNextPrompt("Discard");
            invoke(window, "onNew");
            clean(view);
            assertNotEquals("Draft", first(view, 0).getText());
            controller.markModified();
            answerNextPrompt("Cancel");
            assertEquals(false, invoke(window, "confirmPlanDeparture"));
            assertTrue(controller.isModified());
        });
    }

    @Test
    void mainWindowApplyCallbackUpdatesTitleAndCancelPreservesIt() throws Exception {
        fx(() -> {
            var window = new com.daviddunn.retirementplanner.ui.MainWindow();
            var controllerField = window.getClass().getDeclaredField("controller");
            controllerField.setAccessible(true);
            var controller = (ApplicationController) controllerField.get(window);
            controller.newPlan();
            invoke(window, "loadCurrentPlan");
            var viewField = window.getClass().getDeclaredField("householdView");
            viewField.setAccessible(true);
            var view = (HouseholdView) viewField.get(window);
            controller.getCurrentPlan().getAccountPortfolio().addAccount(
                new com.daviddunn.retirementplanner.domain.financial.BrokerageAccount(
                    "Test funding", com.daviddunn.retirementplanner.domain.model.AccountOwnership.PRIMARY,
                    new java.math.BigDecimal("10000000")));
            var stage = new javafx.stage.Stage();
            window.setStage(stage);
            date(view, 0).setValue(LocalDate.of(1960, 1, 1));
            date(view, 1).setValue(LocalDate.of(1962, 1, 1));
            category(view, 0).setValue(MortalityCategory.MALE);
            category(view, 1).setValue(MortalityCategory.FEMALE);
            assertTrue(view.applyChanges());
            assertTrue(controller.isModified());
            assertTrue(stage.getTitle().endsWith(" *"));
            first(view, 0).setText("Draft");
            invoke(window, "refreshAllViews");
            assertEquals("Draft", first(view, 0).getText());
            view.cancelChanges();
            assertTrue(controller.isModified());
            first(view, 0).setText("Close draft");
            answerNextPrompt("Cancel");
            var event = new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(event);
            assertTrue(event.isConsumed());
            assertEquals("Close draft", first(view, 0).getText());
            var assumptionsField = window.getClass().getDeclaredField("assumptionsView");
            assumptionsField.setAccessible(true);
            var assumptions = (AssumptionsView) assumptionsField.get(window);
            var inflationField = AssumptionsView.class.getDeclaredField("inflationRateField");
            inflationField.setAccessible(true);
            ((TextField) inflationField.get(assumptions)).setText("3.75");
            assertEquals(true, invoke(window, "saveCurrentPlan"));
            assertEquals(0, new java.math.BigDecimal("0.0375").compareTo(controller.getCurrentPlan()
                    .getPlanningAssumptions().getEconomicAssumptions().getExpectedAnnualInflationRate()));
            clean(view);
        });
    }

    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void answerNextPrompt(String text) {
        Platform.runLater(() -> {
            for (var window : java.util.List.copyOf(javafx.stage.Window.getWindows())) {
                if (window.getScene().getRoot() instanceof DialogPane pane) {
                    pane.getButtonTypes().stream().filter(type -> type.getText().equals(text))
                            .findFirst().ifPresent(type -> ((Button) pane.lookupButton(type)).fire());
                }
            }
        });
    }
    private static PersonCard card(HouseholdView view, int member) {
        return (PersonCard) ((TitledPane) view.getChildren().get(member)).getContent();
    }
    private static TextField first(HouseholdView view, int member) {
        return (TextField) card(view, member).getChildren().get(1);
    }
    private static TextField last(HouseholdView view, int member) {
        return (TextField) card(view, member).getChildren().get(3);
    }
    @SuppressWarnings("unchecked")
    private static ComboBox<MortalityCategory> category(HouseholdView view, int member) {
        return (ComboBox<MortalityCategory>) card(view, member).getChildren().get(7);
    }
    private static DatePicker date(HouseholdView view, int member) {
        return (DatePicker) card(view, member).getChildren().get(5);
    }
    private static Button button(HouseholdView view, int index) {
        return (Button) ((HBox) view.getChildren().get(2)).getChildren().get(index);
    }
    private static Label status(HouseholdView view) {
        return (Label) view.getChildren().get(3);
    }
    private static void clean(HouseholdView view) {
        assertFalse(view.isDirty());
        assertFalse(view.dirtyProperty().get());
        assertTrue(button(view, 0).isDisabled());
        assertTrue(button(view, 1).isDisabled());
    }
    private static void dirty(HouseholdView view) {
        assertTrue(view.isDirty());
        assertFalse(button(view, 0).isDisabled());
        assertFalse(button(view, 1).isDisabled());
    }
    private static void fx(CheckedAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
    private interface CheckedAction { void run() throws Exception; }
    private static class Fixture {
        final ApplicationController controller = new ApplicationController();
        final HouseholdView view = new HouseholdView();
        final AtomicInteger notifications = new AtomicInteger();
        Fixture() {
            primary().setMortalityCategory(MortalityCategory.MALE);
            spouse().setMortalityCategory(MortalityCategory.FEMALE);
            primary().setFirstName("Primary");
            primary().setLastName("Family");
            primary().setBirthDate(LocalDate.of(1960, 1, 1));
            spouse().setFirstName("Spouse");
            spouse().setLastName("Family");
            spouse().setBirthDate(LocalDate.of(1962, 1, 1));
            view.setOnPlanChanged(() -> { notifications.incrementAndGet(); controller.markModified(); });
            view.load(controller.getCurrentPlan());
        }
        Person primary() { return controller.getCurrentPlan().getHousehold().getPrimaryPerson(); }
        Person spouse() { return controller.getCurrentPlan().getHousehold().getSpouse(); }
    }
}
