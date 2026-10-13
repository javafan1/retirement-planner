package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.tax.state.StateTaxUnavailableException;
import com.daviddunn.retirementplanner.ui.ProjectionFailureMessages;
import com.daviddunn.retirementplanner.ui.views.*;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
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
class PennsylvaniaProductionVisualTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test
    void inspectSelectorResultsReportsAndTypedUnavailablePresentation() throws Exception {
        Path folder = Path.of("target/phase6d3b-preview"); Files.createDirectories(folder);
        var wizard = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            wizard.set(new NewRetirementPlanWizard(null)); wizard.get().show();
            CompleteWizardFixtures.enterIndividualIraAndCashThroughReview(wizard.get().getDialogPane(), false);
            ((Button) wizard.get().getDialogPane().lookup("#wizard-review-edit-assumptions")).fire();
            ((ComboBox<StateTaxJurisdiction>) wizard.get().getDialogPane().lookup("#wizard-assumption-state-of-residence"))
                    .setValue(StateTaxJurisdiction.PA);
        });
        Thread.sleep(200);
        fx(() -> {
            write(wizard.get().getDialogPane().snapshot(null, null), folder.resolve("wizard-PA.png"));
            button(wizard.get().getDialogPane(), "Next").fire(); button(wizard.get().getDialogPane(), "Create Plan").fire();
        });
        var stage = new AtomicReference<Stage>();
        var plan = wizard.get().getResult();
        var projection = new ProjectionEngine().project(plan);
        fx(() -> {
            stage.set(new Stage()); var editor = new AssumptionsView(); editor.load(plan);
            ComboBox<StateTaxJurisdiction> input = field(editor, "stateJurisdictionComboBox");
            assertEquals(StateTaxJurisdiction.PA, input.getValue()); assertNotNull(input.getTooltip());
            assertEquals(StateTaxJurisdiction.alphabeticalChoices(), input.getItems()); assertTrue(editor.lookupAll(".help-icon").isEmpty());
            var scroller = new ScrollPane(editor); scroller.setFitToWidth(true);
            stage.get().setScene(new Scene(scroller, 1080, 800)); stage.get().show();
        });
        try {
            Thread.sleep(200);
            fx(() -> write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("assumptions-PA.png")));
            fx(() -> stage.get().setScene(new Scene(new ProjectionYearDetailsPane(projection.getYears().getFirst(), null,
                    BigDecimal.ZERO, null), 1120, 850)));
            Thread.sleep(200);
            fx(() -> {
                var pane = (ProjectionYearDetailsPane) stage.get().getScene().getRoot();
                var tax = pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .filter(label -> label.getText().equals("Pennsylvania State Income Tax")).findFirst().orElseThrow();
                assertNotNull(tax.getTooltip());
                var scroll = (ScrollPane) pane.getCenter();
                var content = scroll.getContent();
                var bounds = content.sceneToLocal(tax.localToScene(tax.getBoundsInLocal()));
                scroll.setVvalue(Math.min(1, Math.max(0, (bounds.getMinY() - 300)
                        / (content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight()))));
            });
            Thread.sleep(200);
            fx(() -> {
                write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("results-PA.png"));
                var summary = new ResultsSummaryView(new com.daviddunn.retirementplanner.ui.controller.ApplicationController());
                summary.load(plan, projection, List.of()); new Scene(summary, 1400, 900); summary.applyCss(); summary.layout();
                Path pdf = folder.resolve("pennsylvania.pdf");
                new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(),
                        summary.prepareProjectionPdfReport(), pdf);
                try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                    var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                    assertTrue(text.contains("Pennsylvania"));
                    assertTrue(text.contains("State personal income tax only"));
                    var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0, 90), "png", folder.resolve("report-summary.png").toFile());
                    for (int page = 0; page < document.getNumberOfPages(); page++) {
                        var stripper = new org.apache.pdfbox.text.PDFTextStripper(); stripper.setStartPage(page + 1); stripper.setEndPage(page + 1);
                        if (stripper.getText(document).contains("STATE INCOME TAX - FULL-YEAR RESIDENCY")) {
                            javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page, 90), "png", folder.resolve("report-state-tax.png").toFile());
                            break;
                        }
                    }
                }
                new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), folder.resolve("pennsylvania.csv"));
            });
            plan.getHousehold().getPrimaryPerson().addIncomeSource(new Pension("Unknown pension", AccountOwnership.PRIMARY,
                    plan.getPlanningAssumptions().getProjectionStartDate(), null, new BigDecimal("1000"), BigDecimal.ZERO));
            var failure = assertThrows(StateTaxUnavailableException.class, () -> new ProjectionEngine().project(plan));
            var alert = new AtomicReference<Alert>();
            fx(() -> {
                alert.set(new Alert(Alert.AlertType.WARNING)); alert.get().setTitle("State Tax Unavailable");
                alert.get().setHeaderText("State income tax cannot be reliably estimated");
                alert.get().setContentText(ProjectionFailureMessages.stateTaxUnavailableMessage(failure).orElseThrow());
                alert.get().show();
            });
            Thread.sleep(200);
            fx(() -> {
                assertFalse(alert.get().getContentText().contains("$0"));
                write(alert.get().getDialogPane().snapshot(null, null), folder.resolve("state-tax-unavailable-PA.png")); alert.get().close();
            });
        } finally { fx(() -> { stage.get().close(); wizard.get().close(); }); }
    }

    private static void write(WritableImage image, Path file) throws Exception {
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(output, "png", file.toFile());
    }
}
