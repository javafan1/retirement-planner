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
class IowaProductionVisualTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test
    void inspectSelectorResultsReportsAndTypedUnavailablePresentation() throws Exception {
        Path folder = Path.of("target/phase6d5b-preview"); Files.createDirectories(folder);
        var wizard = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            wizard.set(new NewRetirementPlanWizard(null)); wizard.get().show();
            CompleteWizardFixtures.enterIndividualIraAndCashThroughReview(wizard.get().getDialogPane(), false);
            ((Button) wizard.get().getDialogPane().lookup("#wizard-review-edit-assumptions")).fire();
            ((ComboBox<StateTaxJurisdiction>) wizard.get().getDialogPane().lookup("#wizard-assumption-state-of-residence"))
                    .setValue(StateTaxJurisdiction.IA);
        });
        Thread.sleep(200);
        fx(() -> {
            write(wizard.get().getDialogPane().snapshot(null, null), folder.resolve("wizard-IA.png"));
            button(wizard.get().getDialogPane(), "Next").fire(); button(wizard.get().getDialogPane(), "Create Plan").fire();
        });
        var stage = new AtomicReference<Stage>();
        var plan = wizard.get().getResult();
        var projection = new ProjectionEngine().project(plan);
        fx(() -> {
            stage.set(new Stage()); var editor = new AssumptionsView(); editor.load(plan);
            ComboBox<StateTaxJurisdiction> input = field(editor, "stateJurisdictionComboBox");
            assertEquals(StateTaxJurisdiction.IA, input.getValue()); assertNotNull(input.getTooltip());
            assertEquals(StateTaxJurisdiction.alphabeticalChoices(), input.getItems()); assertTrue(editor.lookupAll(".help-icon").isEmpty());
            var scroller = new ScrollPane(editor); scroller.setFitToWidth(true);
            stage.get().setScene(new Scene(scroller, 1080, 800)); stage.get().show();
        });
        try {
            Thread.sleep(200);
            fx(() -> write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("assumptions-IA.png")));
            fx(() -> stage.get().setScene(new Scene(new ProjectionYearDetailsPane(projection.getYears().getFirst(), null,
                    BigDecimal.ZERO, null), 1120, 850)));
            Thread.sleep(200);
            fx(() -> {
                var pane = (ProjectionYearDetailsPane) stage.get().getScene().getRoot();
                var tax = pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .filter(label -> label.getText().equals("Iowa State Income Tax")).findFirst().orElseThrow();
                assertNotNull(tax.getTooltip());
                assertTrue(tax.getTooltip().isWrapText());
                assertTrue(tax.getTooltip().getText().contains("not assumed to be zero"));
                tax.getTooltip().show(tax, 200, 200);
                tax.getTooltip().getScene().getRoot().applyCss();
                tax.getTooltip().getScene().getRoot().layout();
                write(tax.getTooltip().getScene().getRoot().snapshot(null, null), folder.resolve("iowa-limitations.png"));
                tax.getTooltip().hide();
                var scroll = (ScrollPane) pane.getCenter();
                var content = scroll.getContent();
                var bounds = content.sceneToLocal(tax.localToScene(tax.getBoundsInLocal()));
                scroll.setVvalue(Math.min(1, Math.max(0, (bounds.getMinY() - 300)
                        / (content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight()))));
            });
            Thread.sleep(200);
            fx(() -> {
                write(stage.get().getScene().getRoot().snapshot(null, null), folder.resolve("results-IA.png"));
                var summary = new ResultsSummaryView(new com.daviddunn.retirementplanner.ui.controller.ApplicationController());
                summary.load(plan, projection, List.of()); new Scene(summary, 1400, 900); summary.applyCss(); summary.layout();
                write(summary.snapshot(null, null), folder.resolve("results-summary-IA.png"));
                Path pdf = folder.resolve("iowa.pdf");
                new com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter().export(plan, projection, List.of(),
                        summary.prepareProjectionPdfReport(), pdf);
                try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                    var text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                    assertTrue(text.contains("Iowa"));
                    assertTrue(text.contains("State personal income tax only"));
                    assertTrue(text.contains("not assumed to be zero"));
                    assertTrue(text.contains("nominal 2026"));
                    assertTrue(text.contains("expired after 2028"));
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
                new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), folder.resolve("iowa.csv"));
                var csv = Files.readString(folder.resolve("iowa.csv"));
                assertTrue(csv.contains("Iowa Planning Limitations"));
                assertTrue(csv.contains("not assumed to be zero"));
                assertTrue(csv.contains("nominal 2026"));
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
                write(alert.get().getDialogPane().snapshot(null, null), folder.resolve("state-tax-unavailable-IA.png")); alert.get().close();
            });
        } finally { fx(() -> { stage.get().close(); wizard.get().close(); }); }
    }

    private static void write(WritableImage image, Path file) throws Exception {
        var output = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++)
            output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(output, "png", file.toFile());
    }

    @Test
    void positiveTaxBoundaryDisplayUsesRealFundingCalculationNotGeneratedInvestmentIncome() throws Exception {
        var funding = com.daviddunn.retirementplanner.domain.projection.IowaFundingTest.funding(
                new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA", AccountOwnership.PRIMARY,
                        new BigDecimal("1000000")), "100000", false);
        assertEquals(new BigDecimal("3128.20"), funding.getStateTaxCalculation().incomeTax());
        var zero = BigDecimal.ZERO;
        // Display fixture from the assessed-interest annual funding boundary; this is not a generated-yield projection.
        var withdrawal = new BigDecimal("80000").add(funding.getAdditionalWithdrawal());
        var ending = new BigDecimal("1000000").subtract(withdrawal);
        var rules = new com.daviddunn.retirementplanner.persistence.GovernmentRulesRepository().load("/rules/government-rules-2026.json");
        var medicare = new com.daviddunn.retirementplanner.domain.medicare.MedicarePremiumCalculator().calculate(
                funding.getFederalTaxCalculation(), com.daviddunn.retirementplanner.domain.rules.FilingStatus.SINGLE, rules, 1);
        var snapshots = List.of(new ProjectedAccountSnapshot(new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                "IRA", AccountOwnership.PRIMARY, new BigDecimal("1000000")), ending));
        var year = new ProjectionYear(1, 2026, new BigDecimal("1000000"), zero, zero,
                com.daviddunn.retirementplanner.domain.income.HouseholdSocialSecurityResult.primaryOnly(zero),
                new BigDecimal("80000"), new BigDecimal("80000"), withdrawal, zero, zero, zero, zero,
                zero, zero, zero, HouseholdCashSettlement.zero(), ending, snapshots, funding.getFederalTaxCalculation(),
                funding.getStateTaxCalculation(), medicare, funding.getAdditionalWithdrawal(),
                zero, zero, zero, null, zero, ending, 76, funding.getIncomeSources());
        var projection = new Projection(); projection.addYear(year);
        Path folder = Path.of("target/phase6d5b-preview"); Files.createDirectories(folder);
        fx(() -> {
            var stage = new Stage();
            var pane = new ProjectionYearDetailsPane(year, null, zero, null);
            stage.setScene(new Scene(pane, 1120, 850)); stage.show();
            try {
                pane.applyCss(); pane.layout();
                var tax = pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .filter(label -> label.getText().equals("Iowa State Income Tax")).findFirst().orElseThrow();
                assertTrue(pane.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("3,128")));
                var scroll = (ScrollPane) pane.getCenter();
                var content = scroll.getContent();
                var bounds = content.sceneToLocal(tax.localToScene(tax.getBoundsInLocal()));
                scroll.setVvalue(Math.min(1, Math.max(0, (bounds.getMinY() - 300)
                        / (content.getBoundsInLocal().getHeight() - scroll.getViewportBounds().getHeight()))));
                pane.layout();
                write(pane.snapshot(null, null), folder.resolve("positive-assessed-interest-IA.png"));
            } finally { stage.close(); }
        });
        new com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter().export(projection, List.of(), folder.resolve("positive-assessed-interest-iowa.csv"));
        var csv = Files.readString(folder.resolve("positive-assessed-interest-iowa.csv"));
        assertTrue(csv.contains("3128.20")); assertTrue(csv.contains("not assumed to be zero"));
    }
}
