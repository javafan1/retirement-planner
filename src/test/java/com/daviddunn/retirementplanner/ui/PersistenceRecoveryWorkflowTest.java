package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceRecoveryWorkflowTest {
    @TempDir Path directory;
    @BeforeAll static void init() throws Exception { NewPlanWizardTestSupport.startFx(); }

    @Test void cancellingSaveAsPreservesPlanPathDirtyAndRevision() throws Exception {
        NewPlanWizardTestSupport.fx(() -> {
            var window = new MainWindow() {
                @Override protected File choosePlanSaveFile() { return null; }
            };
            var controller = controller(window);
            controller.markModified();
            var plan = controller.getCurrentPlan();
            var file = controller.getCurrentFile();
            var revision = controller.getSourcePlanRevision();
            var method = MainWindow.class.getDeclaredMethod("onSaveAs");
            method.setAccessible(true);
            assertEquals(false, method.invoke(window));
            assertSame(plan, controller.getCurrentPlan());
            assertEquals(file, controller.getCurrentFile());
            assertTrue(controller.isModified());
            assertEquals(revision, controller.getSourcePlanRevision());
        });
    }

    @Test void recoveryDialogRequiresAffirmativeReviewAndLeavesPrimaryUntouched() throws Exception {
        NewPlanWizardTestSupport.fx(() -> {
            var window = new MainWindow();
            var controller = controller(window);
            Path file = directory.resolve("plan.json");
            controller.saveAs(file);
            controller.getCurrentPlan().getHousehold().getPrimaryPerson().setFirstName("Changed");
            controller.save();
            Files.writeString(file, "broken");
            AtomicReference<Throwable> failure = new AtomicReference<>();
            var reviewWhenReady = new javafx.animation.AnimationTimer() {
                @Override public void handle(long now) {
                try {
                    DialogPane pane = List.copyOf(Window.getWindows()).stream()
                            .filter(Window::isShowing).map(w -> w.getScene().getRoot())
                            .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                            .filter(p -> p.getButtonTypes().stream().anyMatch(b -> b.getText().equals("Review backup")))
                            .findFirst().orElse(null);
                    if (pane == null) return; // Backup validation now runs on the file worker.
                    stop();
                    var review = pane.getButtonTypes().stream().filter(b -> b.getText().equals("Review backup")).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(review)).fire();
                } catch (Throwable exception) {
                    stop();
                    failure.set(exception);
                    Window.getWindows().stream().filter(Window::isShowing).toList().forEach(Window::hide);
                }
                }
            };
            reviewWhenReady.start();
            var method = MainWindow.class.getDeclaredMethod("offerPlanRecovery", Path.class);
            method.setAccessible(true);
            try { method.invoke(window, file); } finally { reviewWhenReady.stop(); }
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertTrue(controller.isModified());
            assertFalse(controller.hasCurrentFile());
            assertEquals("broken", Files.readString(file));
            assertNotNull(new JsonRetirementPlanRepository().loadBackup(file));
        });
    }

    private static ApplicationController controller(MainWindow window) throws Exception {
        var field = MainWindow.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (ApplicationController) field.get(window);
    }
}
