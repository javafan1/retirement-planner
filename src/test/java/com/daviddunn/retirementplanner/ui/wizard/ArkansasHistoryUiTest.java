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

    @Test
    void retainedDetailsRenderInBothThemesWhileArkansasIsAbsentFromThePicker(@TempDir Path folder) throws Exception {
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
                    assertFalse(choices.getItems().contains(StateTaxJurisdiction.AR));
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
                    assertDoesNotThrow(() -> new ProjectionYearDetailsPane(ordinary, historical, ZERO, ZERO));
                    var summary = new ResultsSummaryView(controller);
                    summary.load(plan, retained, List.of());
                    var range = field(summary, "projectionRangeLabel");
                    Label projectionRange = (Label) range;
                    assertTrue(projectionRange.getText().contains("Arkansas retained history"));
                    assertTrue(projectionRange.getTooltip().getText().contains("P_STATUTORY_CENTS"));
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
}
