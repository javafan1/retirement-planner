package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import com.daviddunn.retirementplanner.ui.views.ResultsSummaryView;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasHistoryUiTest {
    @BeforeAll static void start() throws Exception { startFx(); }

    @Test void guidedCreationStoresTheOrdinaryArkansasSelection() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                CompleteWizardFixtures.enterThroughReview(pane, false);
                ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
                @SuppressWarnings("unchecked")
                var state = (ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence");
                assertTrue(state.getItems().contains(StateTaxJurisdiction.AR));
                assertTrue(state.getItems().stream().noneMatch(value -> value.displayName().equals("Colorado")));
                state.setValue(StateTaxJurisdiction.AR);
                button(pane, "Next").fire();
                button(pane, "Create Plan").fire();
                assertEquals(StateTaxJurisdiction.AR,
                        wizard.getResult().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            } finally {
                wizard.close();
            }
        });
    }

    @Test
    void retainedDetailsRenderInBothThemesWithArkansasInThePicker(@TempDir Path folder) throws Exception {
        var plan = surplusPlan("1000000", 1);
        var retained = RetainedProjectionSnapshot.captureArkansas(validationEngine().project(plan)).restoreProjection();
        var historical = retained.getFirstYear();
        var ordinary = new ProjectionEngine().project(plan).getFirstYear();
        var controller = new ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(folder.resolve("settings.json")),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(folder.resolve("analyses"))));
        controller.newPlan(plan);
        try {
        for (var theme : new ApplicationTheme[]{ApplicationTheme.LIGHT, ApplicationTheme.DARK}) {
            fx(() -> {
                try (var manager = ApplicationThemeManager.start(theme)) {
                    var assumptions = new AssumptionsView();
                    assumptions.load(plan);
                    ComboBox<StateTaxJurisdiction> choices = field(assumptions, "stateJurisdictionComboBox");
                    assertEquals(StateTaxJurisdiction.alphabeticalChoices(), choices.getItems());
                    assertTrue(choices.getItems().contains(StateTaxJurisdiction.AR));
                    var details = new ProjectionYearDetailsPane(historical, ordinary, ZERO, ZERO);
                    var scene = new Scene(details, 1120, 850);
                    ApplicationThemeManager.prepare(scene);
                    details.applyCss();
                    details.layout();
                    var labels = details.lookupAll(".label").stream().filter(Label.class::isInstance)
                            .map(Label.class::cast).map(Label::getText).toList();
                    assertTrue(labels.contains("Arkansas Retained History"));
                    assertTrue(labels.contains("Arkansas Retirement Exclusion"));
                    assertTrue(labels.contains("Taxable Funding Income"));
                    assertTrue(labels.contains("AR-2026-P2-1"));
                    assertTrue(labels.contains("AR-FUNDING-P2-1"));
                    assertFalse(labels.contains("Arkansas Roth Target Policy"));
                    assertEquals(1, labels.stream().filter("Retained from Tax-Funding Surplus"::equalsIgnoreCase).count());
                    var reversed = new ProjectionYearDetailsPane(ordinary, historical, ZERO, ZERO);
                    var reversedScene = new Scene(reversed, 1120, 850);
                    ApplicationThemeManager.prepare(reversedScene);
                    reversed.applyCss();
                    reversed.layout();
                    assertTrue(reversed.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                            .anyMatch(label -> label.getText().equals("Arkansas State Income Tax")));
                    var summary = new ResultsSummaryView(controller);
                    summary.load(plan, retained, List.of());
                    var range = field(summary, "projectionRangeLabel");
                    Label projectionRange = (Label) range;
                    assertTrue(projectionRange.getText().contains("Arkansas retained history"));
                    assertTrue(projectionRange.getTooltip().getText().contains("P_STATUTORY_CENTS"));
                    var scale = System.getProperty("glass.win.uiScale");
                    if (scale != null) {
                        render(scene, "annual-detail-" + theme + "-" + scale, scale);
                        var summaryScene = new Scene(summary, 1120, 850);
                        ApplicationThemeManager.prepare(summaryScene);
                        render(summaryScene, "results-summary-" + theme + "-" + scale, scale);
                    }
                }
            });
        }
        } finally {
            assertTrue(controller.getAnalysisParameters().drain(Duration.ofSeconds(10)).complete());
            controller.getAnalysisParameters().shutdown();
            assertTrue(controller.getAnalysisResults().drain(Duration.ofSeconds(10)).safeToExit());
            controller.getAnalysisResults().shutdownAfterSuccessfulDrain();
        }
    }

    private static void render(Scene scene, String name, String scale) throws java.io.IOException {
        var stage = new javafx.stage.Stage();
        stage.setScene(scene);
        stage.show();
        try {
            assertEquals(Double.parseDouble(scale), stage.getOutputScaleX(), .01);
            var snapshot = scene.snapshot(null);
            int width = (int) snapshot.getWidth();
            int height = (int) snapshot.getHeight();
            var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            snapshot.getPixelReader().getPixels(0, 0, width, height, javafx.scene.image.PixelFormat.getIntArgbInstance(),
                    ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData(), 0, width);
            var folder = java.nio.file.Files.createDirectories(Path.of("target/phase6e-p3d1-previews"));
            javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        } finally {
            stage.hide();
        }
    }
}
