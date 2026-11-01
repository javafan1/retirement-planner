package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real Modena DatePicker popup in a Stage and in the wizard Dialog. */
class DatePickerThemeRenderingTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void calendarArrowsRemainContrastingInStageAndWizardPopup() throws Exception {
        for (ApplicationTheme theme : ApplicationTheme.values()) {
            verifyPopup(theme, false);
            verifyPopup(theme, true);
        }
    }

    private void verifyPopup(ApplicationTheme theme, boolean wizardOwner) throws Exception {
        var pickerRef = new AtomicReference<DatePicker>();
        var stageRef = new AtomicReference<Stage>();
        var wizardRef = new AtomicReference<NewRetirementPlanWizard>();
        var themesRef = new AtomicReference<ApplicationThemeManager>();
        fx(() -> {
            themesRef.set(ApplicationThemeManager.start(theme));
            if (wizardOwner) {
                var wizard = new NewRetirementPlanWizard(null);
                wizardRef.set(wizard);
                wizard.show();
                pickerRef.set((DatePicker) field(person(wizard.getDialogPane(), "primary"), "birthDatePicker"));
            } else {
                var stage = new Stage();
                stageRef.set(stage);
                var picker = new DatePicker();
                picker.setValue(java.time.LocalDate.of(2026, 6, 15));
                stage.setScene(new Scene(new VBox(picker), 360, 180));
                stage.show();
                pickerRef.set(picker);
            }
            pickerRef.get().show();
        });
        pulses();
        fx(() -> {
            var popup = Window.getWindows().stream().filter(Window::isShowing)
                    .map(Window::getScene).filter(java.util.Objects::nonNull)
                    .map(Scene::getRoot)
                    .filter(root -> root.getStyleClass().contains("date-picker-popup")
                            || root.lookup(".date-picker-popup") != null)
                    .findFirst().orElseThrow(() -> new AssertionError("DatePicker calendar popup was not shown."));
            Parent popupContent = popup.getStyleClass().contains("date-picker-popup")
                    ? popup : (Parent) popup.lookup(".date-picker-popup");
            popup.applyCss();
            popup.layout();
            assertTrue(popup.getStyleClass().contains("app-theme"), "Popup root must receive the application theme.");
            String themeSheet = "/css/theme-" + theme.name().toLowerCase(java.util.Locale.ROOT) + ".css";
            assertTrue(popup.getStylesheets().stream().anyMatch(path -> path.endsWith(themeSheet)));

            var left = (javafx.scene.layout.Region) popupContent.lookup(".left-arrow");
            var right = (javafx.scene.layout.Region) popupContent.lookup(".right-arrow");
            assertNotNull(left, "Modena previous-month arrow should remain present.");
            assertNotNull(right, "Modena next-month arrow should remain present.");
            Color leftColor = fill(left);
            Color rightColor = fill(right);
            assertEquals(leftColor, rightColor);
            if (theme == ApplicationTheme.DARK) {
                assertTrue(luminance(leftColor) > 0.8, "Dark mode uses a bright arrow against its dark header.");
            } else {
                assertTrue(luminance(leftColor) < 0.2, "Light mode uses a dark arrow against its light header.");
            }
            save(popupContent, Path.of("target/phase13ic2/previews/calendar-"
                    + (wizardOwner ? "wizard-" : "stage-") + theme.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
            pickerRef.get().hide();
            if (wizardOwner) wizardRef.get().close(); else stageRef.get().hide();
            themesRef.get().close();
        });
    }

    private static Color fill(javafx.scene.layout.Region node) {
        assertNotNull(node.getBackground(), "Arrow shape should have a computed CSS background.");
        var paint = node.getBackground().getFills().getFirst().getFill();
        assertInstanceOf(Color.class, paint);
        return (Color) paint;
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void save(Parent node, Path path) throws Exception {
        var image = node.snapshot(null, null);
        var output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) {
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(output, "png", path.toFile());
    }
}
