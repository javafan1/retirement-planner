package com.daviddunn.retirementplanner.ui.controls;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequiredFieldSupportTest {
    @BeforeAll static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException started) { Platform.runLater(ready::countDown); }
        assertTrue(ready.await(20, TimeUnit.SECONDS));
    }

    @Test void staticRequiredMarkerKeepsLabelTextAssociationAndCanBeDisposed() throws Exception {
        fx(() -> {
            Label label = new Label("Birth Date");
            TextField field = new TextField();
            field.setId("birth-date");
            var marker = RequiredFieldSupport.attach(label, field);
            assertEquals("Birth Date", label.getText());
            assertSame(field, label.getLabelFor());
            assertEquals("birth-date", field.getId());
            assertEquals("*", marker.indicatorNode().getText());
            assertTrue(marker.indicatorNode().isVisible());
            assertTrue(marker.indicatorNode().getStyleClass().contains("required-field-indicator"));
            assertFalse(marker.indicatorNode().isFocusTraversable());
            assertEquals("Required.", field.getAccessibleHelp());
            assertSame(marker, RequiredFieldSupport.attach(label, field));
            marker.close();
            assertNull(label.getGraphic());
            assertNull(label.getLabelFor());
            assertNull(field.getAccessibleHelp());
            return null;
        });
    }

    @Test void dynamicRequirementComposesAndRestoresExistingAccessibilityHelp() throws Exception {
        fx(() -> {
            Label label = new Label("Investment Return");
            TextField field = new TextField();
            field.setAccessibleHelp("Enter the annual assumption.");
            SimpleBooleanProperty required = new SimpleBooleanProperty(false);
            var marker = RequiredFieldSupport.attach(label, field, required);
            assertFalse(marker.indicatorNode().isVisible());
            assertEquals("Enter the annual assumption.", field.getAccessibleHelp());

            required.set(true);
            assertTrue(marker.indicatorNode().isVisible());
            assertEquals("Enter the annual assumption. Required.", field.getAccessibleHelp());
            required.set(false);
            assertFalse(marker.indicatorNode().isVisible());
            assertEquals("Enter the annual assumption.", field.getAccessibleHelp());
            required.set(true);
            field.setAccessibleHelp("Updated help.");
            assertEquals("Updated help. Required.", field.getAccessibleHelp());
            marker.close();
            assertEquals("Updated help.", field.getAccessibleHelp());

            Label alreadyRequiredLabel = new Label("Required by description");
            TextField alreadyRequired = new TextField();
            alreadyRequired.setAccessibleHelp("This value is required.");
            var alreadyDescribed = RequiredFieldSupport.attach(alreadyRequiredLabel, alreadyRequired);
            assertEquals("This value is required.", alreadyRequired.getAccessibleHelp());
            alreadyDescribed.close();
            return null;
        });
    }

    @Test void hiddenOrUnmanagedFieldHasNoOrphanIndicatorAndDisabledRequiredRemainsMarked() throws Exception {
        fx(() -> {
            Label label = new Label("Conditional Field");
            TextField field = new TextField();
            SimpleBooleanProperty required = new SimpleBooleanProperty(true);
            var marker = RequiredFieldSupport.attach(label, field, required);
            field.setDisable(true);
            assertTrue(marker.indicatorNode().isVisible(), "Disabled does not determine requiredness.");
            label.setVisible(false);
            assertFalse(marker.indicatorNode().isVisible());
            label.setVisible(true);
            field.setManaged(false);
            assertFalse(marker.indicatorNode().isVisible());
            field.setManaged(true);
            assertTrue(marker.indicatorNode().isVisible());
            marker.close();
            return null;
        });
    }

    @Test void existingGraphicIsRejectedWithoutBeingDiscardedAndLegendIsAccessible() throws Exception {
        fx(() -> {
            TextField alreadyAssociated = new TextField();
            Label associatedLabel = new Label("Already linked");
            associatedLabel.setLabelFor(alreadyAssociated);
            var existingAssociation = RequiredFieldSupport.attach(associatedLabel, alreadyAssociated);
            existingAssociation.close();
            assertSame(alreadyAssociated, associatedLabel.getLabelFor(), "A pre-existing label association is retained.");

            Label label = new Label("Status");
            Text oldGraphic = new Text("i");
            label.setGraphic(oldGraphic);
            assertThrows(IllegalArgumentException.class,
                    () -> RequiredFieldSupport.attach(label, new TextField()));
            assertSame(oldGraphic, label.getGraphic());
            Label legend = RequiredFieldSupport.createLegend();
            assertEquals("* Required field", legend.getText());
            assertEquals("Required field", legend.getAccessibleText());
            assertTrue(legend.getStyleClass().contains("required-field-legend"));
            assertFalse(legend.isFocusTraversable());
            return null;
        });
    }

    @Test void themeChangesRestyleRequiredIndicatorAndRenderCompactFixture() throws Exception {
        var stageRef = new AtomicReference<Stage>();
        var themeRef = new AtomicReference<ApplicationThemeManager>();
        var indicatorRef = new AtomicReference<Text>();
        fx(() -> {
            Stage stage = new Stage(); stageRef.set(stage);
            Label requiredLabel = new Label("Always required");
            TextField requiredField = new TextField();
            RequiredFieldSupport.Attachment required = RequiredFieldSupport.attach(requiredLabel, requiredField);
            Label optionalLabel = new Label("Optional");
            TextField optionalField = new TextField();
            RequiredFieldSupport.attach(optionalLabel, optionalField, new SimpleBooleanProperty(false));
            Label conditionalLabel = new Label("Conditionally required");
            TextField conditionalField = new TextField();
            SimpleBooleanProperty condition = new SimpleBooleanProperty(true);
            RequiredFieldSupport.attach(conditionalLabel, conditionalField, condition);
            conditionalField.setDisable(true);
            Label tooltipLabel = new Label("Tooltip preserved");
            TextField tooltipField = new TextField();
            tooltipField.setTooltip(new javafx.scene.control.Tooltip("Existing tooltip"));
            RequiredFieldSupport.attach(tooltipLabel, tooltipField);
            Label describedLabel = new Label("Accessible description");
            TextField describedField = new TextField(); describedField.setAccessibleText("Existing name");
            describedField.setAccessibleHelp("Existing description");
            RequiredFieldSupport.attach(describedLabel, describedField);
            VBox content = new VBox(8, requiredLabel, requiredField, optionalLabel, optionalField,
                    conditionalLabel, conditionalField, tooltipLabel, tooltipField,
                    describedLabel, describedField, RequiredFieldSupport.createLegend());
            stage.setScene(new Scene(content, 310, 390));
            themeRef.set(ApplicationThemeManager.start(ApplicationTheme.LIGHT));
            themeRef.get().apply(ApplicationTheme.LIGHT);
            stage.show();
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            assertEquals("Existing tooltip", tooltipField.getTooltip().getText());
            assertEquals("Existing name", describedField.getAccessibleText());
            assertTrue(describedField.getAccessibleHelp().contains("Existing description"));
            indicatorRef.set(required.indicatorNode());
            return null;
        });
        fx(() -> {
            assertEquals(Color.web("#a12622"), indicatorRef.get().getFill());
            save(stageRef.get(), Path.of("target/phase16b1/previews/required-indicators-light.png"));
            themeRef.get().apply(ApplicationTheme.DARK);
            stageRef.get().getScene().getRoot().applyCss(); stageRef.get().getScene().getRoot().layout();
            assertEquals(Color.web("#ffaba5"), indicatorRef.get().getFill());
            save(stageRef.get(), Path.of("target/phase16b1/previews/required-indicators-dark.png"));
            stageRef.get().close(); themeRef.get().close();
            return null;
        });
    }

    private static void fx(ThrowingSupplier<?> action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.get(); return; }
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.get(); } catch (Throwable ex) { failure.set(ex); } finally { done.countDown(); } });
        assertTrue(done.await(20, TimeUnit.SECONDS), "FX action timed out.");
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static void save(Stage stage, Path path) throws Exception {
        var image = stage.getScene().snapshot(null);
        var output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(path.getParent()); ImageIO.write(output, "png", path.toFile());
    }

    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}
