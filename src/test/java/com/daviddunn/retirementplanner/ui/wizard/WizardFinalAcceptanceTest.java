package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.ui.dialogs.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class WizardFinalAcceptanceTest {

    enum Editor {
        ACCOUNT("current-balance"), SOCIAL_SECURITY("fra-monthly-benefit"), PENSION("monthly-benefit"), SPENDING("amount");
        final String amount;
        Editor(String amount) { this.amount = amount; }
    }

    @BeforeAll static void start() throws Exception { startFx(); }

    @ParameterizedTest @EnumSource(Editor.class)
    void malformedItemAmountKeepsErrorFocusAndCanBeCorrected(Editor kind) throws Exception {
        var reference = new AtomicReference<Dialog<?>>();
        fx(() -> {
            Person person = new Person("", "", LocalDate.of(1964, 5, 6));
            person.setMortalityCategory(MortalityCategory.FEMALE);
            var household = new Household(person, null);
            Dialog<?> dialog = switch (kind) {
                case ACCOUNT -> new AccountDialog(null, household, true);
                case SOCIAL_SECURITY -> new SocialSecurityDialog(null, 2026, household, List.of(AccountOwnership.PRIMARY));
                case PENSION -> new PensionDialog(null, household, true);
                case SPENDING -> new ExpenseDialog(null, true);
            };
            reference.set(dialog);
            dialog.show();
            var pane = dialog.getDialogPane();
            if (kind == Editor.PENSION) {
                ((DatePicker) pane.lookup("#wizard-input-start-date")).setValue(LocalDate.of(2027, 1, 1));
                ((TextField) pane.lookup("#wizard-input-annual-cola-rate")).setText("0.0175");
            }
            if (kind == Editor.SPENDING) ((TextField) pane.lookup("#wizard-input-description")).setText("Annual spending");
            if (kind == Editor.SOCIAL_SECURITY) {
                ComboBox<?> ages = (ComboBox<?>) pane.lookup("#wizard-input-claiming-age");
                assertEquals(List.of(62, 63, 64, 65, 66, 67, 68, 69, 70), ages.getItems());
                assertEquals(67, ages.getValue());
            }
            ((TextField) pane.lookup("#wizard-input-" + kind.amount)).setText("malformed amount");
        });
        try {
            pulses();
            fx(() -> button(reference.get().getDialogPane(), "OK").fire());
            pulses();
            fx(() -> {
                var dialog = reference.get();
                var pane = dialog.getDialogPane();
                TextField amount = (TextField) pane.lookup("#wizard-input-" + kind.amount);
                Label error = (Label) pane.lookup("#wizard-editor-error");
                assertTrue(dialog.isShowing());
                assertTrue(error.isVisible() && !error.getText().isBlank());
                assertTrue(amount.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
                assertSame(amount, pane.getScene().getFocusOwner());
                var bounds = amount.localToScene(amount.getBoundsInLocal());
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= pane.getScene().getHeight());
                String message = error.getText();
                ((TextField) pane.lookup(kind == Editor.SPENDING ? "#wizard-input-description" : "#wizard-input-name")).setText("Unrelated label");
                assertEquals(message, error.getText());
                assertNotNull(amount.getAccessibleHelp());
                assertTrue(pane.lookupAll(".label").stream().map(Label.class::cast).anyMatch(label -> label.getLabelFor() == amount && label.getText().contains("*")));
                amount.setText("1234.5600");
                button(pane, "OK").fire();
                assertFalse(dialog.isShowing());
                assertNotNull(dialog.getResult());
                assertEquals("", error.getText());
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @ParameterizedTest @ValueSource(strings = {"start", "length", "return"})
    void basicAssumptionErrorsFocusVisibleControlAndPreserveRawText(String name) throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard); wizard.show();
            fill(person(wizard.getDialogPane(), "primary"), "", "", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            for (int index = 0; index < 5; index++) button(wizard.getDialogPane(), "Next").fire();
            Control input = (Control) wizard.getDialogPane().lookup("#wizard-assumption-" + name);
            if (input instanceof DatePicker picker) picker.getEditor().setText("malformed date");
            else ((TextField) input).setText("malformed number");
            button(wizard.getDialogPane(), "Next").fire();
        });
        try {
            pulses();
            fx(() -> {
                var pane = reference.get().getDialogPane();
                Control input = (Control) pane.lookup("#wizard-assumption-" + name);
                assertEquals(5, (int) field(reference.get(), "stepIndex"));
                assertTrue(input.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
                assertTrue(pane.getScene().getFocusOwner() == input || input instanceof DatePicker picker && pane.getScene().getFocusOwner() == picker.getEditor());
                ScrollPane page = (ScrollPane) pane.lookup("#wizard-page");
                var bounds = page.sceneToLocal(input.localToScene(input.getBoundsInLocal()));
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= page.getHeight());
                button(pane, "Back").fire();
                button(pane, "Next").fire();
                if (input instanceof DatePicker picker) {
                    assertEquals("malformed date", picker.getEditor().getText());
                    picker.setValue(LocalDate.of(2027, 7, 1));
                }
                else {
                    assertEquals("malformed number", ((TextField) input).getText());
                    ((TextField) input).setText(name.equals("length") ? "20" : "6.25");
                }
                button(pane, "Next").fire();
                assertEquals(6, (int) field(reference.get(), "stepIndex"));
                assertFalse(input.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void invalidPensionDateAndInvalidatedAccountOwnerRemainBlocking() throws Exception {
        fx(() -> {
            var primary = new Person("", "", LocalDate.of(1964, 5, 6));
            var spouse = new Person("", "", LocalDate.of(1966, 7, 8));
            var household = new Household(primary, spouse);
            var pension = new PensionDialog(null, household, true);
            pension.show();
            try {
                var pane = pension.getDialogPane();
                ((TextField) pane.lookup("#wizard-input-monthly-benefit")).setText("1234.5600");
                ((TextField) pane.lookup("#wizard-input-annual-cola-rate")).setText("0.0175");
                ((DatePicker) pane.lookup("#wizard-input-start-date")).getEditor().setText("malformed date");
                button(pane, "OK").fire();
                assertTrue(pension.isShowing());
                assertTrue(((Label) pane.lookup("#wizard-editor-error")).getText().contains("Start Date"));
                ((DatePicker) pane.lookup("#wizard-input-start-date")).setValue(LocalDate.of(2027, 3, 1));
                button(pane, "OK").fire();
                assertNotNull(pension.getResult());
                assertEquals("", ((Label) pane.lookup("#wizard-editor-error")).getText());
            }
            finally { pension.close(); }
            var account = new AccountDialog(null, household, true);
            account.show();
            try {
                var pane = account.getDialogPane();
                @SuppressWarnings("unchecked") var type = (ComboBox<AccountType>) pane.lookup("#wizard-input-type");
                @SuppressWarnings("unchecked") var owner = (ComboBox<AccountOwnership>) pane.lookup("#wizard-input-owner");
                type.setValue(AccountType.BROKERAGE); owner.setValue(AccountOwnership.JOINT);
                ((TextField) pane.lookup("#wizard-input-current-balance")).setText("1000.1200");
                type.setValue(AccountType.TRADITIONAL_IRA);
                assertNull(owner.getValue(), "Must require explicit selection, not silently reassign joint ownership");
                button(pane, "OK").fire();
                assertTrue(account.isShowing());
                assertTrue(((Label) pane.lookup("#wizard-editor-error")).getText().contains("Owner"));
                owner.setValue(AccountOwnership.PRIMARY); button(pane, "OK").fire();
                assertNotNull(account.getResult());
            }
            finally { account.close(); }
        });
    }

    @Test void defaultEnterAndEscapeCancelUseExistingDialogBindings() throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard); wizard.show();
            fill(person(wizard.getDialogPane(), "primary"), "", "", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            assertTrue(button(wizard.getDialogPane(), "Next").isDefaultButton());
            assertTrue(button(wizard.getDialogPane(), "Cancel").isCancelButton());
        });
        try {
            pulses();
            fx(() -> reference.get().getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));
            pulses();
            fx(() -> {
                assertEquals(1, (int) field(reference.get(), "stepIndex"));
                reference.get().getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                assertFalse(reference.get().isShowing());
                assertNull(reference.get().getResult());
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void resizeTransitionsPreservePendingInputAndAccessibleNavigation() throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        var input = new AtomicReference<TextField>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard); wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "", "", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            for (int index = 0; index < 5; index++) button(pane, "Next").fire();
            input.set((TextField) pane.lookup("#wizard-assumption-return"));
            input.get().setText("pending uncommitted rate");
        });
        try {
            for (int width : List.of(490, 980, 490, 980)) {
                fx(() -> reference.get().getDialogPane().getScene().getWindow().setWidth(width));
                pulses();
                fx(() -> {
                    var pane = reference.get().getDialogPane();
                    assertSame(input.get(), pane.lookup("#wizard-assumption-return"));
                    assertEquals("pending uncommitted rate", input.get().getText());
                    ComboBox<?> compact = (ComboBox<?>) pane.lookup("#wizard-compact-navigation");
                    assertEquals(width == 490, compact.isVisible());
                    assertTrue(compact.getAccessibleText().contains("visited"));
                    Button current = (Button) pane.lookup("#wizard-step-assumptions-5");
                    assertTrue(current.getAccessibleText().contains("Current"));
                    assertTrue(((Button) pane.lookup("#wizard-step-review-6")).isDisabled());
                    var footer = button(pane, "Next").localToScene(button(pane, "Next").getBoundsInLocal());
                    assertTrue(footer.getMaxY() <= pane.getScene().getHeight());
                });
            }
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void removingSpouseAfterLaterPagesRetainsPrimaryAndIndependentFilingDefault() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Keep", "Me", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                ((Button) pane.lookup("#wizard-add-spouse")).fire();
                fill(person(pane, "spouse"), "Remove", "Me", LocalDate.of(1966, 7, 8), MortalityCategory.MALE);
                for (int index = 0; index < 6; index++) button(pane, "Next").fire();
                ((Button) pane.lookup("#wizard-review-edit-household")).fire();
                answerSpouseRemoval(true);
                ((Button) pane.lookup("#wizard-remove-spouse")).fire();
                assertNull(pane.lookup("#wizard-spouse"));
                for (int index = 0; index < 6; index++) button(pane, "Next").fire();
                button(pane, "Create Plan").fire();
                assertFalse(wizard.getResult().getHousehold().hasSpouse());
                assertEquals("Keep", wizard.getResult().getHousehold().getPrimaryPerson().getFirstName());
                assertEquals(FilingStatus.MARRIED_FILING_JOINTLY, wizard.getResult().getPlanningAssumptions().getTaxAssumptions().getFilingStatus());
            }
            finally { wizard.close(); }
        });
    }
}
