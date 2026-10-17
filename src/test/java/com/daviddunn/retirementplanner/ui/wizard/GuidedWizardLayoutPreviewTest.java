package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Screen;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Logical-window fit probes, not an assertion that Windows DPI or Narrator was tested. */
@EnabledIfSystemProperty(named = "guided.wizard.preview", matches = "true")
class GuidedWizardLayoutPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest
    @CsvSource({"1366-100,980,700", "1366-125,980,560", "1920-100,980,740",
            "1920-150,980,620", "1920-200,900,480"})
    void inspectLogicalWorkArea(String name, double width, double height) throws Exception {
        Path folder = Path.of("target/phase7c3-layout-preview");
        Files.createDirectories(folder);
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard);
            wizard.show();
            wizard.getDialogPane().getScene().getWindow().setWidth(width);
            wizard.getDialogPane().getScene().getWindow().setHeight(height);
        });
        try {
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                capture(pane, folder.resolve(name + "-household.png"));
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                for (int index = 0; index < 5; index++) button(pane, "Next").fire();
            });
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                ((TextField) pane.lookup("#wizard-assumption-return")).setText("invalid");
                button(pane, "Next").fire();
            });
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                capture(pane, folder.resolve(name + "-error.png"));
                var page = (ScrollPane) pane.lookup("#wizard-page");
                page.setVvalue(1);
                pane.layout();
                capture(pane, folder.resolve(name + "-bottom.png"));
                var screen = Screen.getPrimary();
                Files.writeString(folder.resolve(name + "-geometry.txt"),
                        "Logical outer window " + width + " x " + height + "; pane " + pane.getWidth() + " x " + pane.getHeight()
                                + "; viewport " + page.getViewportBounds() + "; actual host output scale "
                                + screen.getOutputScaleX() + " x " + screen.getOutputScaleY()
                                + "; host visual bounds " + screen.getVisualBounds()
                                + ". Named DPI cases simulate logical fit; they do not change OS DPI.\n");
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    private static void capture(DialogPane pane, Path file) throws Exception {
        pane.applyCss(); pane.layout();
        var page = (ScrollPane) pane.lookup("#wizard-page");
        assertTrue(page.getViewportBounds().getHeight() > 80, "Topic must remain scrollable");
        assertEquals(ScrollPane.ScrollBarPolicy.NEVER, page.getHbarPolicy());
        for (String text : List.of("Back", "Next", "Create Plan", "Cancel")) {
            Button action = button(pane, text);
            if (!action.isVisible()) continue;
            var bounds = action.localToScene(action.getBoundsInLocal());
            assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= pane.getScene().getWidth());
            assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= pane.getScene().getHeight());
        }
        for (var control : page.getContent().lookupAll(".text-field")) {
            var bounds = page.getContent().sceneToLocal(control.localToScene(control.getBoundsInLocal()));
            assertTrue(bounds.getMinX() >= 0 && bounds.getMaxX() <= page.getViewportBounds().getWidth() + 1);
        }
        WritableImage image = pane.snapshot(null, null);
        BufferedImage png = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(png, "png", file.toFile());
    }
}
