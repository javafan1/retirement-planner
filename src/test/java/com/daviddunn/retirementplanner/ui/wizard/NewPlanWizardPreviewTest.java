package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic identities only; opt-in screenshots and actual JavaFX input-hover verification. */
@EnabledIfSystemProperty(named = "new.plan.wizard.preview", matches = "true")
class NewPlanWizardPreviewTest {

    @BeforeAll
    static void init() throws Exception { startFx(); }

    @Test
    void inspectSixStatesAndInputHover() throws Exception {
        Path folder = Path.of("target/phase5a-wizard-preview");
        Files.createDirectories(folder);
        StringBuilder geometry = new StringBuilder();
        for (String state : List.of("blank-primary", "populated-primary", "validation-error",
                "spouse-added", "populated-couple", "narrow-couple")) {
            AtomicReference<NewRetirementPlanWizard> reference = new AtomicReference<>();
            fx(() -> {
                var wizard = householdWizard();
                reference.set(wizard);
                wizard.show();
                var pane = wizard.getDialogPane();
                if (!state.equals("blank-primary") && !state.equals("validation-error")) {
                    fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                }
                if (state.contains("spouse") || state.contains("couple")) {
                    ((Button) pane.lookup("#wizard-add-spouse")).fire();
                }
                if (state.contains("couple")) {
                    fill(person(pane, "spouse"), "Sam", "Example", LocalDate.of(1966, 7, 8), MortalityCategory.MALE);
                }
                if (state.equals("validation-error")) button(pane, "Create Plan").fire();
                if (state.equals("narrow-couple")) {
                    pane.getScene().getWindow().setWidth(490);
                    pane.getScene().getWindow().setHeight(570);
                }
            });
            try {
                Thread.sleep(200);
                fx(() -> {
                    var pane = reference.get().getDialogPane();
                    pane.applyCss();
                    pane.layout();
                    var page = (ScrollPane) pane.lookup("#wizard-page");
                    Bounds viewport = page.getViewportBounds();
                    assertTrue(viewport.getWidth() > 300);
                    assertTrue(viewport.getHeight() > 200);
                    for (String id : List.of("primary", "spouse")) {
                        var card = person(pane, id);
                        if (card == null) continue;
                        Bounds firstBounds = null;
                        for (String name : List.of("firstNameField", "lastNameField", "birthDatePicker", "mortalityCategory")) {
                            Control input = field(card, name);
                            Bounds bounds = page.getContent().sceneToLocal(input.localToScene(input.getBoundsInLocal()));
                            assertTrue(bounds.getMinX() >= 0);
                            assertTrue(bounds.getMaxX() <= viewport.getWidth() + 1, state + " clipped " + name);
                            assertTrue(bounds.getHeight() >= 24, state + " collapsed " + name);
                            if (firstBounds == null) firstBounds = bounds;
                            assertEquals(firstBounds.getMinX(), bounds.getMinX(), 1, "Input left alignment");
                        }
                    }
                    Bounds create = button(pane, "Create Plan").localToScene(button(pane, "Create Plan").getBoundsInLocal());
                    assertTrue(create.getMaxY() <= pane.getScene().getHeight());
                    assertTrue(create.getMaxX() <= pane.getScene().getWidth());
                    if (!state.equals("narrow-couple")) {
                        assertTrue(page.getContent().getBoundsInLocal().getHeight() <= viewport.getHeight() + 1,
                                state + " should fit without scrolling at normal size");
                    }
                    write(pane.snapshot(null, null), folder.resolve(state + ".png"));
                    geometry.append(state).append(": dialog ").append(pane.getWidth()).append(" x ")
                            .append(pane.getHeight()).append("; viewport ").append(viewport.getWidth())
                            .append(" x ").append(viewport.getHeight()).append("; content ")
                            .append(page.getContent().getBoundsInLocal().getHeight()).append("\n");
                    if (state.equals("validation-error")) {
                        DatePicker date = field(person(pane, "primary"), "birthDatePicker");
                        Node focus = pane.getScene().getFocusOwner();
                        assertTrue(focus == date || focus == date.getEditor(), "Focus should identify the invalid date");
                    }
                });
                if (state.equals("blank-primary")) {
                    fx(() -> {
                        var card = person(reference.get().getDialogPane(), "primary");
                        DatePicker date = field(card, "birthDatePicker");
                        var bounds = date.localToScreen(date.getBoundsInLocal());
                        date.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 5, 5, bounds.getCenterX(), bounds.getCenterY(),
                                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                                false, false, false, new PickResult(date, 5, 5)));
                    });
                    Thread.sleep(800);
                    fx(() -> {
                        DatePicker date = field(person(reference.get().getDialogPane(), "primary"), "birthDatePicker");
                        Tooltip tooltip = date.getTooltip();
                        assertTrue(tooltip.isShowing(), "Input hover must show the tooltip");
                        write(tooltip.getScene().getRoot().snapshot(null, null), folder.resolve("birth-date-help.png"));
                        date.fireEvent(new MouseEvent(MouseEvent.MOUSE_EXITED, 5, 5, 0, 0,
                                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                                false, false, false, new PickResult(date, 5, 5)));
                        tooltip.hide();
                    });
                }
            }
            finally { fx(() -> reference.get().close()); }
        }
        Files.writeString(folder.resolve("geometry.txt"), geometry.toString());
    }

    private static void write(WritableImage image, Path path) throws Exception {
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) {
            for (int x = 0; x < png.getWidth(); x++) {
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }
}
