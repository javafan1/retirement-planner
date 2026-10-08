package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class NewPlanWizardTestSupport {

    private NewPlanWizardTestSupport() { }

    public static void startFx() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return null;
        });
        try {
            Platform.startup(task);
        }
        catch (IllegalStateException alreadyStarted) {
            Platform.runLater(task);
        }
        task.get(20, TimeUnit.SECONDS);
    }

    public static void fx(Action action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    public interface Action {
        void run() throws Exception;
    }

    @SuppressWarnings("unchecked")
    public static <T> T field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    public static PersonCard person(DialogPane pane, String id) {
        return (PersonCard) pane.lookup("#wizard-" + id);
    }

    public static void fill(PersonCard card, String first, String last,
                            LocalDate date, MortalityCategory category) throws Exception {
        ((TextField) field(card, "firstNameField")).setText(first);
        ((TextField) field(card, "lastNameField")).setText(last);
        ((DatePicker) field(card, "birthDatePicker")).setValue(date);
        ((ComboBox<MortalityCategory>) field(card, "mortalityCategory")).setValue(category);
    }

    public static Button button(DialogPane pane, String text) {
        return (Button) pane.lookupButton(pane.getButtonTypes().stream()
                .filter(type -> type.getText().equals(text)).findFirst().orElseThrow());
    }

    public static DialogPane activeWizardPane() {
        return List.copyOf(Window.getWindows()).stream()
                .filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "new-plan-wizard".equals(pane.getId())).findFirst().orElseThrow();
    }

    public static AtomicInteger answerCreate(boolean couple, String departureChoice) {
        AtomicInteger prompts = new AtomicInteger();
        Platform.runLater(() -> {
            DialogPane pane = activeWizardPane();
            try {
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                if (couple) {
                    ((Button) pane.lookup("#wizard-add-spouse")).fire();
                    fill(person(pane, "spouse"), "Sam", "Example", LocalDate.of(1966, 7, 8), MortalityCategory.MALE);
                }
                if (departureChoice != null) {
                    Platform.runLater(() -> List.copyOf(Window.getWindows()).stream()
                            .filter(Window::isShowing).map(window -> window.getScene().getRoot())
                            .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                            .filter(dialog -> !"new-plan-wizard".equals(dialog.getId()))
                            .forEach(dialog -> {
                                prompts.incrementAndGet();
                                button(dialog, departureChoice).fire();
                            }));
                }
                button(pane, "Create Plan").fire();
            }
            catch (Exception exception) {
                button(pane, "Cancel").fire();
                throw new AssertionError(exception);
            }
        });
        return prompts;
    }
}
