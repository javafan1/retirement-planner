package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies computed wizard navigation styling and snapshots each real selected step. */
class WizardNavigationThemeRenderingTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest
    @CsvSource({"LIGHT,1180,820", "DARK,1180,820", "LIGHT,900,650", "DARK,900,650"})
    void allSevenStepsKeepReadableSelectionAndDraftValues(ApplicationTheme theme, int width, int height) throws Exception {
        fx(() -> {
            try (var themes = ApplicationThemeManager.start(theme)) {
                var owner = new Stage();
                owner.setScene(new Scene(new VBox(), width, height));
                owner.show();
                var wizard = new NewRetirementPlanWizard(owner);
                wizard.show();
                var wizardWindow = (Window) wizard.getDialogPane().getScene().getWindow();
                wizardWindow.setWidth(Math.max(460, width - 32));
                wizardWindow.setHeight(Math.max(380, height - 32));
                CompleteWizardFixtures.enterThroughReview(wizard.getDialogPane(), false);
                DialogPane pane = wizard.getDialogPane();
                List<Button> steps = ((VBox) pane.lookup("#wizard-sidebar")).getChildren().stream()
                        .filter(Button.class::isInstance).map(Button.class::cast).toList();
                assertEquals(7, steps.size());
                assertTrue(steps.stream().allMatch(step -> !step.isDisabled()));

                String folder = "target/phase13ic2/previews/wizard-" + theme.name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + width + "x" + height;
                for (int index = 0; index < steps.size(); index++) {
                    Button selected = steps.get(index);
                    selected.fire();
                    pane.applyCss();
                    pane.layout();
                    assertTrue(selected.getPseudoClassStates().stream().anyMatch(state -> state.getPseudoClassName().equals("current")));
                    Color surface = fill(selected);
                    Color foreground = (Color) selected.getTextFill();
                    if (theme == ApplicationTheme.DARK) {
                        assertTrue(surface.getBlue() > surface.getRed(), "Selected dark navigation uses a blue semantic surface.");
                        assertTrue(contrast(foreground, surface) >= 4.5, "Selected label must meet readable contrast.");
                    } else {
                        assertTrue(contrast(foreground, surface) >= 4.5, "Selected label must meet readable contrast.");
                    }
                    if (index == 5) {
                        assertEquals("6.5", ((javafx.scene.control.TextField) pane.lookup("#wizard-assumption-return")).getText());
                    }
                    save(pane, Path.of(folder, "selected-step-" + (index + 1) + ".png"));
                }
                wizard.close();
                owner.hide();
            }
        });
    }

    private static Color fill(javafx.scene.layout.Region node) {
        assertNotNull(node.getBackground());
        assertInstanceOf(Color.class, node.getBackground().getFills().getFirst().getFill());
        return (Color) node.getBackground().getFills().getFirst().getFill();
    }

    private static double contrast(Color foreground, Color background) {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void save(DialogPane pane, Path path) throws Exception {
        var image = pane.snapshot(null, null);
        var output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(output, "png", path.toFile());
    }
}
