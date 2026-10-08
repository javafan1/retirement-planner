package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AssumptionsWizardStepTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void compactEditorShowsFactoryDefaultsAndInputHelpOnly() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            var step = new AssumptionsWizardStep(draft);
            var stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) step.content(), 680, 500));
            stage.show();
            try {
                assertEquals("8", ((TextField) step.content().lookup("#wizard-assumption-return")).getText());
                assertEquals("3", ((TextField) step.content().lookup("#wizard-assumption-inflation")).getText());
                assertEquals("40", ((TextField) step.content().lookup("#wizard-assumption-length")).getText());
                assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, ((ComboBox<?>) step.content().lookup("#wizard-assumption-filing-status")).getValue());
                for (String id : java.util.List.of("start", "length", "return", "inflation", "healthcare", "cola", "filing-status")) {
                    assertNotNull(((Control) step.content().lookup("#wizard-assumption-" + id)).getTooltip());
                }
                assertTrue(step.content().lookupAll(".help-icon").isEmpty());
                assertTrue(step.validateAndApply());
            }
            finally { stage.close(); }
        });
    }

    @Test void editsApplyToRealPlanAndKeepHiddenTaxAndWithdrawalDefaults() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            var before = draft.getPlan().getPlanningAssumptions();
            var step = new AssumptionsWizardStep(draft);
            AssumptionsView editor = field(step, "editor");
            ((TextField) field(editor, "investmentReturnField")).setText("6.5");
            ((TextField) field(editor, "inflationRateField")).setText("2.5");
            ((TextField) field(editor, "healthcareInflationField")).setText("4");
            ((TextField) field(editor, "socialSecurityColaField")).setText("2");
            ((DatePicker) field(editor, "projectionStartDatePicker")).setValue(LocalDate.of(2030, 1, 1));
            ((TextField) field(editor, "projectionLengthField")).setText("30");
            ((ComboBox<FilingStatus>) field(editor, "filingStatusComboBox")).setValue(FilingStatus.SINGLE);
            assertTrue(step.validateAndApply());
            var after = draft.getPlan().getPlanningAssumptions();
            assertEquals(new BigDecimal("0.065"), after.getEconomicAssumptions().getExpectedAnnualInvestmentReturn());
            assertEquals(new BigDecimal("0.025"), after.getEconomicAssumptions().getGeneralInflationRate());
            assertEquals(new BigDecimal("0.04"), after.getEconomicAssumptions().getHealthcareInflationRate());
            assertEquals(new BigDecimal("0.02"), after.getEconomicAssumptions().getSocialSecurityColaRate());
            assertEquals(FilingStatus.SINGLE, after.getTaxAssumptions().getFilingStatus());
            assertEquals(before.getTaxAssumptions().getEstimatedHeirTaxRateOnTaxDeferredAssets(), after.getTaxAssumptions().getEstimatedHeirTaxRateOnTaxDeferredAssets());
            assertSame(before.getWithdrawalAssumptions(), after.getWithdrawalAssumptions());
        });
    }

    @Test void invalidInputBlocksApplyAndPendingValuesSurviveBackEntry() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            var before = draft.getPlan().getPlanningAssumptions();
            var step = new AssumptionsWizardStep(draft);
            AssumptionsView editor = field(step, "editor");
            TextField length = field(editor, "projectionLengthField");
            length.setText("0");
            assertFalse(step.validateAndApply());
            assertSame(before, draft.getPlan().getPlanningAssumptions());
            assertTrue(length.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
            step.onEntering();
            assertEquals("0", length.getText());
            length.setText("30");
            ((TextField) field(editor, "investmentReturnField")).setText("bad rate");
            assertFalse(step.validateAndApply());
            assertSame(before, draft.getPlan().getPlanningAssumptions());
        });
    }
}
