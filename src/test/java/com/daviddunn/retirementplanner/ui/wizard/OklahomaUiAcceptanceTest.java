package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.ui.views.*;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import javafx.scene.Scene;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class OklahomaUiAcceptanceTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test
    void selectorAndNormalResultsExposeFrozenOklahomaScope() throws Exception {
        var plan = OklahomaProjectionAcceptanceTest.plan(false);
        var projection = new ProjectionEngine().project(plan);
        fx(() -> {
            var editor = new AssumptionsView();
            editor.load(plan);
            ComboBox<StateTaxJurisdiction> input = field(editor, "stateJurisdictionComboBox");
            assertEquals(StateTaxJurisdiction.OK, input.getValue());
            assertEquals(StateTaxJurisdiction.alphabeticalChoices(), input.getItems());
            assertEquals(19, input.getItems().size());
            assertTrue(input.getTooltip().getText().contains("Oklahoma"));

            var details = new ProjectionYearDetailsPane(projection.getYearAt(0), null, BigDecimal.ZERO, null);
            new Scene(details, 1120, 850);
            details.applyCss(); details.layout();
            var tax = details.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .filter(label -> label.getText().equals("Oklahoma State Income Tax")).findFirst().orElseThrow();
            assertTrue(tax.getTooltip().isWrapText());
            assertTrue(tax.getTooltip().getText().contains("government/CSRS"));
            for (var note : com.daviddunn.retirementplanner.domain.tax.state.OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(input.getTooltip().getText().contains(note));
                assertTrue(tax.getTooltip().getText().contains(note));
            }

            // Results are deliberately loaded after the active plan changes state.
            plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                    .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
            var summary = new ResultsSummaryView(new com.daviddunn.retirementplanner.ui.controller.ApplicationController());
            summary.load(plan, projection, List.of());
            Label range = field(summary, "projectionRangeLabel");
            assertTrue(range.getText().contains("Oklahoma"));
            assertTrue(range.getTooltip().getText().contains("OK-2026-1"));
            assertTrue(range.getTooltip().getText().contains("HB2764/HB4072"));
            for (var note : com.daviddunn.retirementplanner.domain.tax.state.OklahomaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(range.getTooltip().getText().contains(note));
            }
        });
    }
}
