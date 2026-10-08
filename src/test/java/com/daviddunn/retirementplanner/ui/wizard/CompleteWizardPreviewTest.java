package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
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

/** Real JavaFX snapshots with synthetic identities; opt in for desktop acceptance. */
@EnabledIfSystemProperty(named = "complete.wizard.preview", matches = "true")
class CompleteWizardPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void inspectCompleteWizardStatesAndInputHover() throws Exception {
        Path folder = Path.of("target/phase5b-wizard-preview");
        Files.createDirectories(folder);
        var states = List.of("household-single", "household-couple", "accounts-empty", "accounts-single", "accounts-couple",
                "income-single", "income-couple", "expenses-empty", "expenses-populated", "assumptions-defaults",
                "assumptions-validation", "review-single", "review-couple", "review-warning", "review-error",
                "narrow-household", "narrow-accounts", "narrow-assumptions", "narrow-review");
        StringBuilder geometry = new StringBuilder();
        for (String state : states) {
            AtomicReference<NewRetirementPlanWizard> reference = new AtomicReference<>();
            fx(() -> {
                var wizard = new NewRetirementPlanWizard(null);
                reference.set(wizard);
                wizard.show();
                var pane = wizard.getDialogPane();
                boolean populated = state.endsWith("single") || state.endsWith("couple") || state.endsWith("populated")
                        || state.startsWith("narrow-");
                if (populated) CompleteWizardFixtures.enterThroughReview(pane, state.endsWith("couple") || state.startsWith("narrow-"));
                else {
                    fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                    for (int index = 0; index < 5; index++) button(pane, "Next").fire();
                }
                String section = state.startsWith("narrow-") ? state.substring(7) : state.substring(0, state.indexOf('-'));
                if (!section.equals("review")) ((Button) pane.lookup("#wizard-review-edit-" + section)).fire();
                if (state.equals("assumptions-validation")) {
                    ((TextField) pane.lookup("#wizard-assumption-length")).setText("0");
                    button(pane, "Next").fire();
                }
                if (state.equals("review-error")) {
                    NewPlanDraft draft = field(wizard, "draft");
                    draft.getPlan().getHousehold().getPrimaryPerson().setBirthDate(null);
                    ((ReviewWizardStep) ((List<?>) field(wizard, "steps")).get(5)).onEntering();
                }
                if (state.startsWith("narrow-")) {
                    pane.getScene().getWindow().setWidth(490);
                    pane.getScene().getWindow().setHeight(570);
                }
            });
            try {
                Thread.sleep(200);
                fx(() -> {
                    var pane = reference.get().getDialogPane();
                    pane.applyCss(); pane.layout();
                    ScrollPane page = (ScrollPane) pane.lookup("#wizard-page");
                    assertTrue(page.getViewportBounds().getWidth() > 300);
                    assertTrue(page.getViewportBounds().getHeight() > 190);
                    for (String text : List.of("Back", "Next", "Create Plan", "Cancel")) {
                        Button action = button(pane, text);
                        if (!action.isVisible()) continue;
                        var bounds = action.localToScene(action.getBoundsInLocal());
                        assertTrue(bounds.getMaxX() <= pane.getScene().getWidth(), state + " clipped action");
                        assertTrue(bounds.getMaxY() <= pane.getScene().getHeight(), state + " clipped action");
                    }
                    assertTrue(pane.lookupAll(".help-icon").isEmpty());
                    assertTrue(pane.lookupAll(".ikonli-font-icon").isEmpty());
                    if (state.contains("assumptions")) {
                        double left = -1;
                        for (String id : List.of("start", "length", "return", "inflation", "healthcare", "cola", "filing-status")) {
                            Control input = (Control) pane.lookup("#wizard-assumption-" + id);
                            assertNotNull(input.getTooltip());
                            var bounds = page.getContent().sceneToLocal(input.localToScene(input.getLayoutBounds()));
                            assertTrue(bounds.getMaxX() <= page.getViewportBounds().getWidth() + 1, state + " clipped input " + id);
                            if (left < 0) left = bounds.getMinX();
                            assertEquals(left, bounds.getMinX(), 1);
                        }
                    }
                    write(pane.snapshot(null, null), folder.resolve(state + ".png"));
                    geometry.append(state).append(": ").append(pane.getWidth()).append(" x ").append(pane.getHeight())
                            .append("; viewport height ").append(page.getViewportBounds().getHeight())
                            .append("; page height ").append(page.getContent().getBoundsInLocal().getHeight()).append("\n");
                    if (state.equals("review-couple") || state.equals("narrow-review")) {
                        page.setVvalue(1); pane.layout();
                        write(pane.snapshot(null, null), folder.resolve(state + "-bottom.png"));
                    }
                });
                if (state.equals("assumptions-defaults")) {
                    fx(() -> new javafx.scene.robot.Robot().mouseMove(0, 0));
                    Thread.sleep(350);
                    fx(() -> {
                        Control input = (Control) reference.get().getDialogPane().lookup("#wizard-assumption-return");
                        var bounds = input.localToScreen(input.getBoundsInLocal());
                        new javafx.scene.robot.Robot().mouseMove(bounds.getCenterX(), bounds.getCenterY());
                        input.fireEvent(new MouseEvent(MouseEvent.MOUSE_MOVED, 5, 5, bounds.getCenterX(), bounds.getCenterY(),
                                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                                false, false, false, new PickResult(input, 5, 5)));
                    });
                    Thread.sleep(800);
                    fx(() -> {
                        Tooltip tooltip = ((Control) reference.get().getDialogPane().lookup("#wizard-assumption-return")).getTooltip();
                        assertTrue(tooltip.isShowing(), "Direct input hover must retain the existing help");
                        write(tooltip.getScene().getRoot().snapshot(null, null), folder.resolve("investment-return-help.png"));
                        tooltip.hide();
                    });
                }
            }
            finally { fx(() -> reference.get().close()); }
        }
        Files.writeString(folder.resolve("geometry.txt"), geometry.toString());
        for (int group = 0; group < 4; group++) {
            BufferedImage sheet = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
            var graphics = sheet.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 1200, 900);
            for (int cell = 0; cell < 6 && group * 6 + cell < states.size(); cell++) {
                String state = states.get(group * 6 + cell);
                BufferedImage screenshot = javax.imageio.ImageIO.read(folder.resolve(state + ".png").toFile());
                int x = cell % 3 * 400; int y = cell / 3 * 450;
                graphics.setColor(java.awt.Color.BLACK); graphics.drawString(state, x + 8, y + 16);
                graphics.drawImage(screenshot, x, y + 25, 390, 410, null);
            }
            graphics.dispose();
            javax.imageio.ImageIO.write(sheet, "png", folder.resolve("contact-sheet-" + group + ".png").toFile());
        }
    }

    private static void write(WritableImage image, Path path) throws Exception {
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", path.toFile());
    }
}
