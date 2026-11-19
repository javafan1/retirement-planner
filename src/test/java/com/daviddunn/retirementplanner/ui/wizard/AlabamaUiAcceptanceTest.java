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

class AlabamaUiAcceptanceTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test
    void compactClassificationControlRetainsExplicitLegalChoice() throws Exception {
        fx(() -> {
            var pension = new com.daviddunn.retirementplanner.domain.income.Pension("Verified cash-balance DB",
                    AccountOwnership.PRIMARY,java.time.LocalDate.of(2026,1,1),null,new BigDecimal("1000"),BigDecimal.ZERO);
            pension.setPlanClassification(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.DEFINED_BENEFIT_414J);
            var dialog = new com.daviddunn.retirementplanner.ui.dialogs.PensionDialog(pension);
            ComboBox<?> input = (ComboBox<?>) dialog.getDialogPane().lookup("#pension-plan-classification");
            assertNotNull(input);
            assertEquals(pension.getPlanClassification(),input.getValue());
            assertTrue(input.getTooltip().getText().contains("cash-balance"));
            assertEquals(pension.getPlanClassification(),dialog.getResultConverter().call(ButtonType.OK).getPlanClassification());
            var blank = new com.daviddunn.retirementplanner.ui.dialogs.PensionDialog(null);
            assertEquals(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.UNKNOWN,
                    ((ComboBox<?>)blank.getDialogPane().lookup("#pension-plan-classification")).getValue());
        });
    }

    @Test
    void guidedAssumptionsCanReturnToThePensionWithoutApplyingDraftState() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                CompleteWizardFixtures.enterThroughReview(pane, false);
                ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
                @SuppressWarnings("unchecked")
                var residence = (ComboBox<StateTaxJurisdiction>) pane.lookup("#wizard-assumption-state-of-residence");
                residence.setValue(StateTaxJurisdiction.AL);
                var notice = pane.lookup("#assumptions-alabama-pension-notice");
                assertTrue(notice.isVisible());
                ((Button) notice.lookup(".review-alabama-pensions")).fire();
                var pensions = (TableView<?>) pane.lookup("#wizard-pensions");
                assertNotNull(pensions);
                var pension = (com.daviddunn.retirementplanner.domain.income.Pension)
                        pensions.getSelectionModel().getSelectedItem();
                assertEquals("Primary pension", pension.getName());
                assertEquals(com.daviddunn.retirementplanner.domain.income.PensionPlanClassification.UNKNOWN,
                        pension.getPlanClassification());
                assertEquals(StateTaxJurisdiction.AL, residence.getValue());
                NewPlanDraft draft = field(wizard, "draft");
                assertEquals(StateTaxJurisdiction.MI,
                        draft.getPlan().getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            } finally {
                wizard.close();
            }
        });
    }

    @Test
    void selectorAndNormalResultsExposeFrozenAlabamaScope() throws Exception {
        var plan = AlabamaProjectionAcceptanceTest.plan(false);
        var projection = new ProjectionEngine().project(plan);
        fx(() -> {
            var editor = new AssumptionsView();
            editor.load(plan);
            ComboBox<StateTaxJurisdiction> input = field(editor, "stateJurisdictionComboBox");
            assertEquals(StateTaxJurisdiction.AL, input.getValue());
            assertEquals(StateTaxJurisdiction.alphabeticalChoices(), input.getItems());
            assertEquals(21, input.getItems().size());
            assertTrue(input.getTooltip().getText().contains("Alabama"));

            var details = new ProjectionYearDetailsPane(projection.getYearAt(0), null, BigDecimal.ZERO, null);
            new Scene(details, 1120, 850);
            details.applyCss(); details.layout();
            var tax = details.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .filter(label -> label.getText().equals("Alabama State Income Tax")).findFirst().orElseThrow();
            assertTrue(tax.getTooltip().isWrapText());
            assertTrue(tax.getTooltip().getText().contains("Government, military"));
            for (var note : com.daviddunn.retirementplanner.domain.tax.state.AlabamaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(input.getTooltip().getText().contains(note));
                assertTrue(tax.getTooltip().getText().contains(note));
            }

            // Results are deliberately loaded after the active plan changes state.
            plan.setPlanningAssumptions(plan.getPlanningAssumptions()
                    .withStateTaxAssumptions(StateTaxAssumptions.simplified(StateTaxJurisdiction.FL)));
            var summary = new ResultsSummaryView(new com.daviddunn.retirementplanner.ui.controller.ApplicationController());
            summary.load(plan, projection, List.of());
            Label range = field(summary, "projectionRangeLabel");
            assertTrue(range.getText().contains("Alabama"));
            assertTrue(range.getTooltip().getText().contains("AL-2026-1"));
            assertTrue(range.getTooltip().getText().contains("completed-$500-step"));
            for (var note : com.daviddunn.retirementplanner.domain.tax.state.AlabamaProductionTaxCalculator.planningLimitations(2027, 2029)) {
                assertTrue(range.getTooltip().getText().contains(note));
            }
        });
    }
}
