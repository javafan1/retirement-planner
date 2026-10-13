package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.controls.RetirementParticipantFactsEditor;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RetirementParticipantEditorTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void expandingAccountFactsResizesDialogAndShowsEveryInput() throws Exception {
        var dialog = new AccountDialog[1];
        var collapsedHeight = new double[1];
        try {
            fx(() -> {
                dialog[0] = new AccountDialog(new Traditional401K(
                        "Employer", AccountOwnership.PRIMARY, BigDecimal.TEN));
                dialog[0].show();
            });
            fx(() -> {
                collapsedHeight[0] = dialog[0].getDialogPane().getHeight();
                RetirementParticipantFactsEditor editor = field(dialog[0], "participantFactsEditor");
                editor.setExpanded(true);
            });
            // The expansion listener queues sizing on the next JavaFX turn.
            fx(() -> {
                var pane = dialog[0].getDialogPane();
                pane.applyCss();
                pane.layout();
                assertTrue(pane.getHeight() > collapsedHeight[0] + 100);
                RetirementParticipantFactsEditor editor = field(dialog[0], "participantFactsEditor");
                var content = (javafx.scene.layout.GridPane) editor.getContent();
                for (var input : content.getChildren()) {
                    if (input instanceof ComboBox<?> || input instanceof DatePicker) {
                        var bounds = pane.sceneToLocal(input.localToScene(input.getBoundsInLocal()));
                        assertTrue(bounds.getMinY() >= 0);
                        assertTrue(bounds.getMaxY() <= pane.getHeight());
                        assertTrue(input.getBoundsInParent().getMaxY() <= content.getHeight());
                    }
                }
                var lastInput = (DatePicker) field(editor, "conditionsDate");
                var inputBounds = lastInput.localToScene(lastInput.getBoundsInLocal());
                var ok = pane.lookupButton(ButtonType.OK);
                assertTrue(inputBounds.getMaxY() <= ok.localToScene(ok.getBoundsInLocal()).getMinY());
            });
        } finally {
            fx(() -> { if (dialog[0] != null) dialog[0].close(); });
        }
    }

    @Test void optionalUnknownInputsHaveDirectTooltipsAndDatesFollowStatus() throws Exception {
        fx(() -> {
            var editor = new RetirementParticipantFactsEditor(RetirementParticipantFacts.UNKNOWN);
            assertEquals(RetirementParticipantFacts.UNKNOWN, editor.value());
            var grid = (javafx.scene.layout.GridPane) editor.getContent();
            for (var node : grid.getChildren()) {
                if (node instanceof ComboBox<?> combo) assertNotNull(combo.getTooltip());
                if (node instanceof DatePicker picker) assertNotNull(picker.getTooltip());
            }
            ComboBox<RetirementParticipantFacts.SeparationStatus> separation = field(editor, "separation");
            DatePicker date = field(editor, "separationDate");
            assertTrue(date.isDisabled());
            separation.setValue(RetirementParticipantFacts.SeparationStatus.SEPARATED);
            assertFalse(date.isDisabled());
            date.setValue(LocalDate.of(2030, 1, 1));
            separation.setValue(RetirementParticipantFacts.SeparationStatus.UNKNOWN);
            assertNull(date.getValue());
            assertTrue(date.isDisabled());
        });
    }

    @Test void accountAndPensionApplyCreateNewObjectsAndCancelPreservesOriginal() throws Exception {
        fx(() -> {
            var facts = new RetirementParticipantFacts(RetirementParticipantFacts.SeparationStatus.SEPARATED,
                    LocalDate.of(2030, 1, 1), RetirementParticipantFacts.RetirementConditionsStatus.MET,
                    LocalDate.of(2031, 1, 1));
            var account = new Traditional401K("Employer", AccountOwnership.PRIMARY, BigDecimal.TEN);
            account.setRetirementParticipantFacts(facts);
            var dialog = new AccountDialog(account);
            var result = dialog.getResultConverter().call(ButtonType.OK);
            assertNotSame(account, result);
            assertEquals(facts, result.getRetirementParticipantFacts());
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            assertEquals(facts, account.getRetirementParticipantFacts());
            var pension = new Pension("Pension", AccountOwnership.PRIMARY, LocalDate.of(2026, 1, 1), null,
                    BigDecimal.TEN, BigDecimal.ZERO);
            pension.setRetirementParticipantFacts(facts);
            var pensionDialog = new PensionDialog(pension);
            assertEquals(facts, pensionDialog.getResultConverter().call(ButtonType.OK).getRetirementParticipantFacts());
            assertNull(pensionDialog.getResultConverter().call(ButtonType.CANCEL));
            assertEquals(facts, pension.getRetirementParticipantFacts());
        });
    }

    @Test void guidedDialogsKeepAdvancedFactsOffWizardSurface() throws Exception {
        fx(() -> {
            var household = new Household(new Person("Primary", "", LocalDate.of(1960, 1, 1)));
            var employer = new Traditional401K("Employer", AccountOwnership.PRIMARY, BigDecimal.TEN);
            var accountDialog = new AccountDialog(employer, household, true);
            RetirementParticipantFactsEditor accountEditor = field(accountDialog, "participantFactsEditor");
            assertFalse(accountEditor.isVisible());
            assertFalse(accountEditor.isManaged());
            var pensionDialog = new PensionDialog(null, household, true);
            RetirementParticipantFactsEditor pensionEditor = field(pensionDialog, "participantFactsEditor");
            assertFalse(pensionEditor.isVisible());
            assertFalse(pensionEditor.isManaged());
        });
    }
}
