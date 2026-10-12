package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "state.tax.preview", matches = "true")
class StateTaxVisualPreviewTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test
    void inspectWizardTabsValidationNarrowLayoutAndFloridaZero() throws Exception {
        Path folder = Path.of("target/phase6d1-preview");
        Files.createDirectories(folder);
        var wizard = new java.util.concurrent.atomic.AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            wizard.set(new NewRetirementPlanWizard(null));
            wizard.get().show();
            CompleteWizardFixtures.enterThroughReview(wizard.get().getDialogPane(), false);
            ((Button) wizard.get().getDialogPane().lookup("#wizard-review-edit-assumptions")).fire();
        });
        try {
            for (var state : StateTaxJurisdiction.values()) {
                fx(() -> stateInput(wizard.get()).setValue(state));
                Thread.sleep(200);
                fx(() -> {
                    assertNotNull(stateInput(wizard.get()).getTooltip());
                    write(wizard.get().getDialogPane().snapshot(null, null), folder.resolve("wizard-" + state.name() + ".png"));
                });
            }
            fx(() -> { stateInput(wizard.get()).setValue(null); button(wizard.get().getDialogPane(), "Next").fire(); });
            Thread.sleep(200);
            fx(() -> write(wizard.get().getDialogPane().snapshot(null, null), folder.resolve("wizard-validation.png")));
            fx(() -> {
                stateInput(wizard.get()).setValue(StateTaxJurisdiction.FL);
                wizard.get().getDialogPane().getScene().getWindow().setWidth(510);
                wizard.get().getDialogPane().getScene().getWindow().setHeight(620);
            });
            Thread.sleep(200);
            fx(() -> {
                var pane = wizard.get().getDialogPane();
                var input = stateInput(wizard.get());
                var viewport = ((ScrollPane) pane.lookup("#wizard-page")).getViewportBounds();
                assertTrue(viewport.getWidth() > 350);
                assertTrue(input.localToScene(input.getBoundsInLocal()).getMaxX() < pane.getScene().getWidth());
                assertTrue(pane.lookupAll(".help-icon").isEmpty());
                write(pane.snapshot(null, null), folder.resolve("wizard-narrow-FL.png"));
                button(pane, "Next").fire();
                button(pane, "Create Plan").fire();
            });
            var stage = new java.util.concurrent.atomic.AtomicReference<Stage>();
            var editor = new java.util.concurrent.atomic.AtomicReference<AssumptionsView>();
            var scroller = new java.util.concurrent.atomic.AtomicReference<ScrollPane>();
            var plan = wizard.get().getResult();
            fx(() -> {
                stage.set(new Stage()); editor.set(new AssumptionsView());
                scroller.set(new ScrollPane(editor.get())); scroller.get().setFitToWidth(true);
                stage.get().setScene(new Scene(scroller.get(), 1080, 800)); stage.get().show();
            });
            try {
                for (var state : StateTaxJurisdiction.values()) {
                    fx(() -> {
                        plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
                        editor.get().load(plan);
                    });
                    Thread.sleep(200);
                    fx(() -> write(scroller.get().snapshot(null, null), folder.resolve("assumptions-" + state.name() + ".png")));
                }
                fx(() -> {
                    var year = new ProjectionEngine().project(plan).getYears().getFirst();
                    assertEquals(0, year.getStateIncomeTax().signum());
                    var details = new ProjectionYearDetailsPane(year, null, java.math.BigDecimal.ZERO, null);
                    stage.get().setScene(new Scene(details, 1120, 850));
                });
                Thread.sleep(200);
                fx(() -> {
                    var details = (ProjectionYearDetailsPane) stage.get().getScene().getRoot();
                    Label tax = (Label) details.lookupAll(".label").stream().filter(Label.class::isInstance)
                            .map(Label.class::cast).filter(l -> l.getText().equals("Florida State Income Tax")).findFirst().orElseThrow();
                    var scroll = (ScrollPane) details.getCenter();
                    var content = scroll.getContent();
                    var bounds = content.sceneToLocal(tax.localToScene(tax.getBoundsInLocal()));
                    scroll.setVvalue(Math.min(1, Math.max(0, (bounds.getMinY() - 300)
                            / (content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight()))));
                });
                Thread.sleep(200);
                fx(() -> write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("results-FL-zero.png")));
            }
            finally { fx(() -> stage.get().close()); }
        }
        finally { fx(() -> wizard.get().close()); }
        List<Path> images;
        try (var files = Files.list(folder)) {
            images = files.filter(p -> p.toString().endsWith(".png") && !p.getFileName().toString().equals("contact-sheet.png")).sorted().toList();
        }
        BufferedImage sheet = new BufferedImage(1500, 1000, BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, 1500, 1000);
        for (int index = 0; index < images.size(); index++) {
            var file = images.get(index);
            int x = index % 4 * 375;
            int y = index / 4 * 500;
            g.setColor(java.awt.Color.BLACK); g.drawString(file.getFileName().toString(), x + 5, y + 15);
            g.drawImage(javax.imageio.ImageIO.read(file.toFile()), x, y + 25, 365, 465, null);
        }
        g.dispose();
        javax.imageio.ImageIO.write(sheet, "png", folder.resolve("contact-sheet.png").toFile());
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<StateTaxJurisdiction> stateInput(NewRetirementPlanWizard wizard) {
        return (ComboBox<StateTaxJurisdiction>) wizard.getDialogPane().lookup("#wizard-assumption-state-of-residence");
    }
    private static void write(WritableImage image, Path file) throws Exception {
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(output, "png", file.toFile());
    }
}
