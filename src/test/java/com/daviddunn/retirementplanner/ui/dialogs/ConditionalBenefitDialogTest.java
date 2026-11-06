package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.benefit.BenefitFixtures;
import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import javafx.scene.control.*;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefitDefinition.*;
import static com.daviddunn.retirementplanner.ui.dialogs.ConditionalBenefitUiTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalBenefitDialogTest {
    @BeforeAll static void initialize() throws Exception { startFx(); }

    @ParameterizedTest
    @CsvSource({"FIXED_DATE,MONTHLY_RECURRING", "AGE,MONTHLY_RECURRING", "DEATH,MONTHLY_RECURRING",
            "FIXED_DATE,ONE_TIME_LUMP_SUM", "AGE,ONE_TIME_LUMP_SUM", "DEATH,ONE_TIME_LUMP_SUM"})
    void createsEverySupportedPaymentAndTrigger(TriggerType trigger, PaymentPattern pattern) throws Exception {
        var plan = plan(true);
        var result = create(plan, trigger, pattern);
        assertEquals(trigger, result.trigger().type());
        assertEquals(pattern, result.paymentPattern());
        assertEquals(TaxCharacter.FULLY_TAXABLE_ORDINARY_NON_PENSION, result.taxCharacter());
        assertEquals(ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH, result.destination());
        assertTrue(plan.getConditionalBenefits().isEmpty(), "A modal returns a definition; only the section commits");
        result.validateFor(plan.getHousehold());
    }

    @ParameterizedTest
    @CsvSource(value = {"name|", "name|   ", "amount|-1", "amount|NaN", "amount|Infinity", "amount|1000000000001",
            "amount|1.123456789", "amount|$2500", "amount|2,500", "escalation|-1", "escalation|101", "escalation|0.0000001"}, delimiter = '|')
    void invalidTextRetainsDraftAndShowsFieldFeedback(String field, String text) throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
                input(dialog, field).setText(text == null ? "" : text);
                save(dialog);
                assertTrue(dialog.isShowing());
                assertNull(dialog.getResult());
                assertEquals(text == null ? "" : text, input(dialog, field).getText());
                assertFalse(((Label) dialog.getDialogPane().lookup("#cb-" + field + "-error")).getText().isBlank());
                assertTrue(((Label) dialog.getDialogPane().lookup("#cb-validation-status")).getText().contains("not saved"));
            } finally { dialog.close(); }
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"-1", "121", "2.5", "abc", "１２"})
    void rejectsInvalidAges(String value) throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.AGE, PaymentPattern.MONTHLY_RECURRING);
                input(dialog, "age").setText(value);
                save(dialog);
                assertTrue(dialog.isShowing());
                assertFalse(((Label) dialog.getDialogPane().lookup("#cb-age-error")).getText().isBlank());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void requiredMarkersAndDynamicDraftsFollowVisibleControlsAndSaveApplicableFieldsOnly() throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.AGE, PaymentPattern.MONTHLY_RECURRING);
                assertRequired(dialog, "age", true);
                assertRequired(dialog, "trigger-person", true);
                assertRequired(dialog, "trigger-date", false);
                date(dialog, "not-before").setValue(LocalDate.of(2031, 2, 15));
                date(dialog, "end").setValue(LocalDate.of(2040, 6, 1));
                input(dialog, "escalation").setText("2.123456");
                choice(dialog, "pattern").setValue(PaymentPattern.ONE_TIME_LUMP_SUM);
                assertRequired(dialog, "escalation", false);
                choice(dialog, "trigger").setValue(TriggerType.DEATH);
                assertRequired(dialog, "age", false);
                assertEquals("70", input(dialog, "age").getText());
                choice(dialog, "recipient").setValue(PersonReference.SPOUSE);
                choice(dialog, "pattern").setValue(PaymentPattern.MONTHLY_RECURRING);
                assertEquals("2.123456", input(dialog, "escalation").getText());
                assertEquals(LocalDate.of(2040, 6, 1), date(dialog, "end").getValue());
                choice(dialog, "pattern").setValue(PaymentPattern.ONE_TIME_LUMP_SUM);
                save(dialog);
                var saved = dialog.getResult();
                assertNotNull(saved);
                assertNull(saved.notBefore()); assertNull(saved.endDate());
                assertEquals(0, saved.annualEscalationRate().signum());
                assertNull(saved.trigger().date()); assertNull(saved.trigger().age());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void categoryConstrainsDeathTriggerWithoutLosingPreviousAgeDraft() throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.AGE, PaymentPattern.MONTHLY_RECURRING);
                choice(dialog, "category").setValue(Category.DEATH_BENEFIT);
                assertEquals(TriggerType.DEATH, choice(dialog, "trigger").getValue());
                assertTrue(choice(dialog, "trigger").isDisabled());
                choice(dialog, "category").setValue(Category.CONDITIONAL_INCOME);
                assertEquals(TriggerType.AGE, choice(dialog, "trigger").getValue());
                assertEquals("70", input(dialog, "age").getText());
                choice(dialog, "category").setValue(Category.DEATH_BENEFIT);
                choice(dialog, "recipient").setValue(PersonReference.SPOUSE);
                save(dialog);
                assertEquals(Category.DEATH_BENEFIT, dialog.getResult().category());
                assertEquals(TriggerType.DEATH, dialog.getResult().trigger().type());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void singlePersonRolesAndDeathRecipientRestrictionsAreExplicit() throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(false), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
                assertEquals(List.of(PersonReference.PRIMARY), choice(dialog, "recipient").getItems());
                assertEquals(List.of(PersonReference.PRIMARY), choice(dialog, "trigger-person").getItems());
                choice(dialog, "recipient").setValue(PersonReference.PRIMARY);
                save(dialog);
                assertTrue(dialog.isShowing());
                assertTrue(((Label) dialog.getDialogPane().lookup("#cb-recipient-error")).getText().contains("different living recipient"));
                choice(dialog, "trigger").setValue(TriggerType.AGE);
                save(dialog);
                assertNotNull(dialog.getResult());
            } finally { dialog.close(); }
            return null;
        });
    }

    @ParameterizedTest
    @CsvSource({"trigger-date,2030-02-30", "trigger-date,999-01-01", "trigger-date,2030/01/01", "coverage-start,bad"})
    void rejectsInvalidDateTextWithoutClearingIt(String field, String value) throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
                date(dialog, field).getEditor().setText(value);
                save(dialog);
                assertTrue(dialog.isShowing());
                assertEquals(value, date(dialog, field).getEditor().getText());
                assertFalse(((Label) dialog.getDialogPane().lookup("#cb-" + field + "-error")).getText().isBlank());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void domainRejectsImpossibleCoverageAndPaymentOrderingAndAcceptsInclusiveJanuaryEvent() throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                configure(dialog, TriggerType.DEATH, PaymentPattern.MONTHLY_RECURRING);
                date(dialog, "coverage-start").setValue(LocalDate.of(2031, 2, 1));
                date(dialog, "coverage-end").setValue(LocalDate.of(2031, 12, 31));
                save(dialog); assertTrue(dialog.isShowing());
                assertTrue(((Label) dialog.getDialogPane().lookup("#cb-coverage-end-error")).getText().contains("January 1"));
                date(dialog, "coverage-start").setValue(LocalDate.of(2031, 1, 1));
                date(dialog, "coverage-end").setValue(LocalDate.of(2031, 1, 1));
                date(dialog, "not-before").setValue(LocalDate.of(2032, 1, 1));
                date(dialog, "end").setValue(LocalDate.of(2031, 1, 1));
                save(dialog); assertTrue(dialog.isShowing());
                date(dialog, "end").setValue(LocalDate.of(2033, 1, 1));
                save(dialog); assertFalse(dialog.isShowing());
                assertEquals(LocalDate.of(2031, 1, 1), dialog.getResult().coverageEnd());
                assertEquals(LocalDate.of(2033, 1, 1), dialog.getResult().endDate());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void editDuplicateCancelAndWindowClosePreserveOriginalAndExactNumbers() throws Exception {
        var plan = plan(true);
        var original = BenefitFixtures.monthly();
        plan.setConditionalBenefits(List.of(original));
        fx(() -> {
            var edit = new ConditionalBenefitDialog(plan, original);
            edit.show();
            input(edit, "name").setText("Renamed benefit");
            save(edit);
            assertEquals(original.id(), edit.getResult().id());
            assertEquals(0, original.grossAmount().compareTo(edit.getResult().grossAmount()));
            assertEquals(0, original.annualEscalationRate().compareTo(edit.getResult().annualEscalationRate()));
            var copy = new ConditionalBenefitDialog(plan, original, true);
            copy.show(); save(copy);
            assertNotEquals(original.id(), copy.getResult().id());
            assertEquals(original.trigger(), copy.getResult().trigger());
            for (boolean close : new boolean[]{false, true}) {
                var cancel = new ConditionalBenefitDialog(plan, original);
                cancel.show();
                input(cancel, "amount").setText("450000");
                if (close) cancel.getDialogPane().getScene().getWindow().fireEvent(new javafx.stage.WindowEvent(
                        cancel.getDialogPane().getScene().getWindow(), javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                else ((Button) cancel.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertFalse(cancel.isShowing());
                assertNull(cancel.getResult());
            }
            assertEquals(List.of(original), plan.getConditionalBenefits());
            return null;
        });
    }

    @ParameterizedTest @CsvSource({"PA", "IA"})
    void unsupportedStatesWarnButStillSaveValidDefinitions(StateTaxJurisdiction state) throws Exception {
        var plan = plan(true); var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(a.getEconomicAssumptions(), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(), a.getProjectionLengthYears(), a.getProjectionStartDate(), StateTaxAssumptions.simplified(state)));
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan, null);
            dialog.show();
            try {
                assertTrue(((Label) dialog.getDialogPane().lookup("#cb-state-warning")).getText().contains("can be saved"));
                configure(dialog, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
                save(dialog);
                assertNotNull(dialog.getResult());
                assertEquals(state, plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction());
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void missingRequiredFieldsAndLabelBoundsRemainDomainValidated() throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null);
            dialog.show();
            try {
                save(dialog); assertTrue(dialog.isShowing());
                configure(dialog, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
                input(dialog, "name").setText("x".repeat(129));
                save(dialog); assertTrue(dialog.isShowing());
                input(dialog, "name").setText("x".repeat(128));
                choice(dialog, "recipient").setValue(null);
                save(dialog); assertTrue(dialog.isShowing());
                choice(dialog, "recipient").setValue(PersonReference.PRIMARY);
                save(dialog); assertEquals(128, dialog.getResult().label().length());
            } finally { dialog.close(); }
            return null;
        });
    }

    private static void save(ConditionalBenefitDialog dialog) {
        ((Button) dialog.getDialogPane().lookupButton(ConditionalBenefitDialog.SAVE)).fire();
    }

    @ParameterizedTest @CsvSource({"0,0", "1000000000000.00000000,100", "0.00000001,2.123456"})
    void validAmountsAndPercentagesKeepTheirExactDecimalValue(String dollars, String percent) throws Exception {
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan(true), null); dialog.show();
            try {
                configure(dialog, TriggerType.FIXED_DATE, PaymentPattern.MONTHLY_RECURRING);
                input(dialog, "amount").setText(dollars); input(dialog, "escalation").setText(percent);
                save(dialog);
                assertNotNull(dialog.getResult());
                assertEquals(new BigDecimal(dollars), dialog.getResult().grossAmount());
                assertEquals(0, new BigDecimal(percent).movePointLeft(2).compareTo(dialog.getResult().annualEscalationRate()));
            } finally { dialog.close(); }
            return null;
        });
    }

    @Test void editingExistingDeathCategoryDoesNotChangeItsCompatibleTriggerWhenCategoryChanges() throws Exception {
        var plan = plan(true);
        var original = create(plan, TriggerType.DEATH, PaymentPattern.ONE_TIME_LUMP_SUM);
        original = BenefitFixtures.change(original, "category", Category.DEATH_BENEFIT);
        var existing = original;
        fx(() -> {
            var dialog = new ConditionalBenefitDialog(plan, existing); dialog.show();
            try {
                choice(dialog, "category").setValue(Category.CONDITIONAL_INCOME);
                assertEquals(TriggerType.DEATH, choice(dialog, "trigger").getValue());
                choice(dialog, "trigger-person").setValue(PersonReference.SPOUSE);
                choice(dialog, "recipient").setValue(PersonReference.PRIMARY);
                save(dialog); assertNotNull(dialog.getResult());
                assertEquals(PersonReference.SPOUSE, dialog.getResult().trigger().person());
                assertEquals(PersonReference.PRIMARY, dialog.getResult().recipient());
            } finally { dialog.close(); }
            return null;
        });
    }

    private static void assertRequired(ConditionalBenefitDialog dialog, String id, boolean visible) {
        var control = dialog.getDialogPane().lookup("#cb-" + id);
        Label label = dialog.getDialogPane().lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(value -> value.getLabelFor() == control).findFirst().orElseThrow();
        assertTrue(RequiredFieldSupport.isAttached(label));
        assertInstanceOf(TextFlow.class, label.getGraphic());
        var marker = ((TextFlow) label.getGraphic()).getChildren().getLast();
        assertEquals(visible, marker.isVisible());
    }
}
