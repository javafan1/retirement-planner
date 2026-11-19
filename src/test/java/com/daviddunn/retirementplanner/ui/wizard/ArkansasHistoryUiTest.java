package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.app.socialsecurity.results.RetainedProjectionSnapshot;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionIntegrationTest.*;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.*;

class ArkansasHistoryUiTest {
    @BeforeAll static void start() throws Exception { startFx(); }

    @Test
    void retainedDetailsRenderInBothThemesWhileArkansasIsAbsentFromThePicker() throws Exception {
        var plan = surplusPlan("1000000", 1);
        var historical = RetainedProjectionSnapshot.captureArkansas(validationEngine().project(plan)).restoreProjection().getFirstYear();
        var ordinary = new ProjectionEngine().project(plan).getFirstYear();
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
                    assertDoesNotThrow(() -> new ProjectionYearDetailsPane(ordinary, historical, ZERO, ZERO));
                }
            });
        }
    }
}
