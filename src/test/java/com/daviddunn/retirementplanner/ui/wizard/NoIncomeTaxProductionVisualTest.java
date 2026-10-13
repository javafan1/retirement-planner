package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import com.daviddunn.retirementplanner.ui.views.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "state.tax.preview", matches = "true")
class NoIncomeTaxProductionVisualTest {
    @BeforeAll static void init() throws Exception { startFx(); }
    @Test void inspectSelectorsTexasAndNewHampshireResultsAndExports() throws Exception {
        var folder = Path.of("target/phase6d4-preview"); Files.createDirectories(folder);
        var wizard = new AtomicReference<NewRetirementPlanWizard>();
        var stage = new AtomicReference<Stage>();
        fx(() -> {
            wizard.set(new NewRetirementPlanWizard(null)); wizard.get().show();
            CompleteWizardFixtures.enterThroughReview(wizard.get().getDialogPane(), true);
            ((Button) wizard.get().getDialogPane().lookup("#wizard-review-edit-assumptions")).fire();
            var input = (ComboBox<StateTaxJurisdiction>) wizard.get().getDialogPane().lookup("#wizard-assumption-state-of-residence");
            assertEquals(List.of("AK", "FL", "IL", "MI", "NV", "NH", "PA", "SD", "TN", "TX", "WY"),
                    input.getItems().stream().map(Enum::name).toList());
            input.setValue(StateTaxJurisdiction.TX); input.show();
        });
        Thread.sleep(200);
        fx(() -> {
            write(wizard.get().getDialogPane().snapshot(null, null), folder.resolve("wizard-TX.png"));
            var input = (ComboBox<?>) wizard.get().getDialogPane().lookup("#wizard-assumption-state-of-residence");
            snapshotPopup(folder.resolve("wizard-state-choices.png")); input.hide();
            button(wizard.get().getDialogPane(), "Next").fire(); button(wizard.get().getDialogPane(), "Create Plan").fire();
        });
        var plan = wizard.get().getResult();
        try {
            fx(() -> {
                var editor = new AssumptionsView(); editor.load(plan);
                ComboBox<StateTaxJurisdiction> input = field(editor, "stateJurisdictionComboBox");
                assertEquals(StateTaxJurisdiction.TX, input.getValue()); assertNotNull(input.getTooltip());
                assertTrue(editor.lookupAll(".help-icon").isEmpty());
                stage.set(new Stage()); var scroll = new ScrollPane(editor); scroll.setFitToWidth(true);
                stage.get().setScene(new Scene(scroll, 1080, 800)); stage.get().show(); input.show();
            });
            Thread.sleep(200);
            fx(() -> {
                write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("assumptions-TX.png"));
                snapshotPopup(folder.resolve("assumptions-state-choices.png"));
                ComboBox<?> input = field(((ScrollPane) stage.get().getScene().getRoot()).getContent(), "stateJurisdictionComboBox"); input.hide();
            });
            for (var state : List.of(StateTaxJurisdiction.TX, StateTaxJurisdiction.NH)) {
                plan.setPlanningAssumptions(plan.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
                var projection = new ProjectionEngine().project(plan);
                fx(() -> stage.get().setScene(new Scene(new ProjectionYearDetailsPane(projection.getYearAt(0), null, BigDecimal.ZERO, null), 1120, 850)));
                Thread.sleep(200);
                fx(() -> {
                    var pane = (ProjectionYearDetailsPane) stage.get().getScene().getRoot();
                    var tax = pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .filter(label -> label.getText().equals(state.displayName() + " State Income Tax")).findFirst().orElseThrow();
                    assertNotNull(tax.getTooltip());
                    var scroll = (ScrollPane) pane.getCenter(); var content = scroll.getContent();
                    var bounds = content.sceneToLocal(tax.localToScene(tax.getBoundsInLocal()));
                    scroll.setVvalue(Math.min(1, Math.max(0, (bounds.getMinY() - 300)
                            / (content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight()))));
                });
                Thread.sleep(200);
                fx(() -> {
                    write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("results-" + state.name() + ".png"));
                    var summary = new ResultsSummaryView(new com.daviddunn.retirementplanner.ui.controller.ApplicationController());
                    summary.load(plan, projection, List.of()); new Scene(summary, 1400, 900); summary.applyCss(); summary.layout();
                    var pdf = folder.resolve(state.name() + ".pdf");
                    new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(), summary.prepareProjectionPdfReport(), pdf);
                    try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                        var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                        assertTrue(text.contains(state.displayName() + " State Income Tax")); assertFalse(text.contains("Florida State Income Tax"));
                        var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                        for (int page = 0; page < document.getNumberOfPages(); page++) {
                            var stripper = new org.apache.pdfbox.text.PDFTextStripper(); stripper.setStartPage(page + 1); stripper.setEndPage(page + 1);
                            if (stripper.getText(document).contains("STATE INCOME TAX - FULL-YEAR RESIDENCY")) {
                                javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", folder.resolve("report-" + state.name() + ".png").toFile()); break;
                            }
                        }
                    }
                    new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), folder.resolve(state.name() + ".csv"));
                });
            }
        } finally { fx(() -> { if (stage.get() != null) stage.get().close(); wizard.get().close(); }); }
    }
    static void snapshotPopup(Path file) throws Exception {
        var popup = javafx.stage.Window.getWindows().stream().filter(window -> window instanceof javafx.stage.PopupWindow && window.isShowing())
                .findFirst().orElseThrow();
        write(popup.getScene().getRoot().snapshot(null, null), file);
    }
    static void write(WritableImage image, Path file) throws Exception {
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(output, "png", file.toFile());
    }
}
