package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.ui.dialogs.ExpenseDialog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpensesWizardStepTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void noExpensesAreLegalAndNoOwnershipIsInvented() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            assertTrue(new ExpensesWizardStep(draft).validateAndApply());
            assertTrue(draft.getPlan().getHousehold().getExpenses().isEmpty());
        });
    }

    @Test void recurringAmountDatesAndHealthcareInflationUseExistingExpense() throws Exception {
        fx(() -> {
            var dialog = new ExpenseDialog(null, true);
            dialog.show();
            ((TextField) field(dialog, "descriptionField")).setText("Healthcare spending");
            ((TextField) field(dialog, "annualAmountField")).setText("12000.50");
            ((ComboBox<GrowthCategory>) field(dialog, "growthCategoryComboBox")).setValue(GrowthCategory.HEALTHCARE);
            ((DatePicker) field(dialog, "effectiveDatePicker")).setValue(LocalDate.of(2030, 1, 1));
            ((DatePicker) field(dialog, "endDatePicker")).setValue(LocalDate.of(2040, 12, 31));
            button(dialog.getDialogPane(), "OK").fire();
            Expense expense = dialog.getResult();
            assertEquals(new BigDecimal("12000.50"), expense.getAnnualAmount());
            assertEquals(GrowthCategory.HEALTHCARE, expense.getGrowthCategory());
            assertEquals(ExpenseType.RECURRING, expense.getExpenseType());
            assertEquals(LocalDate.of(2040, 12, 31), expense.getEndDate());
        });
    }

    @Test void oneTimeRequiresPurchaseDateAndUsesGeneralInflation() throws Exception {
        fx(() -> {
            var dialog = new ExpenseDialog(null, true);
            dialog.show();
            try {
                ((TextField) field(dialog, "descriptionField")).setText("Replacement car");
                ((TextField) field(dialog, "annualAmountField")).setText("40000");
                ((ComboBox<GrowthCategory>) field(dialog, "growthCategoryComboBox")).setValue(GrowthCategory.HEALTHCARE);
                ((ComboBox<ExpenseType>) field(dialog, "expenseTypeComboBox")).setValue(ExpenseType.ONE_TIME);
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(dialog.isShowing());
                assertTrue(((Label) field(dialog, "effectiveDateLabel")).getText().contains("*"));
                assertFalse(((DatePicker) field(dialog, "endDatePicker")).isManaged());
                ((DatePicker) field(dialog, "effectiveDatePicker")).setValue(LocalDate.of(2032, 6, 1));
                button(dialog.getDialogPane(), "OK").fire();
                var expense = dialog.getResult();
                assertEquals(GrowthCategory.GENERAL, expense.getGrowthCategory());
                assertEquals(expense.getStartDate(), expense.getEndDate());
                assertTrue(expense.isOneTimeExpense());
            }
            finally { dialog.close(); }
        });
    }

    @Test void descriptionMalformedAmountAndReversedDatesProduceInlineErrors() throws Exception {
        fx(() -> {
            var dialog = new ExpenseDialog(null, true);
            dialog.show();
            try {
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(error(dialog).contains("Description"));
                ((TextField) field(dialog, "descriptionField")).setText("Spending");
                ((TextField) field(dialog, "annualAmountField")).setText("wrong");
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(error(dialog).contains("Amount"));
                ((TextField) field(dialog, "annualAmountField")).setText("60000");
                ((DatePicker) field(dialog, "effectiveDatePicker")).setValue(LocalDate.of(2030, 1, 1));
                ((DatePicker) field(dialog, "endDatePicker")).setValue(LocalDate.of(2029, 1, 1));
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(dialog.isShowing());
                assertTrue(error(dialog).contains("end date"));
            }
            finally { dialog.close(); }
        });
    }

    @Test void listAddEditRemovePreservesHouseholdExpenseCollection() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var step = new ExpensesWizardStep(draft);
            var stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) step.content(), 680, 430));
            stage.show();
            try {
                answerExpense("60000");
                ((Button) step.content().lookup("#wizard-add-expense")).fire();
                var table = (TableView<?>) step.content().lookup("#wizard-expenses");
                assertEquals(1, table.getItems().size());
                table.getSelectionModel().select(0);
                answerExpense("65000");
                ((Button) step.content().lookup("#wizard-edit-expense")).fire();
                assertEquals(new BigDecimal("65000"), draft.getPlan().getHousehold().getExpenses().getFirst().getAnnualAmount());
                table.getSelectionModel().select(0);
                ((Button) step.content().lookup("#wizard-remove-expense")).fire();
                assertTrue(draft.getPlan().getHousehold().getExpenses().isEmpty());
            }
            finally { stage.close(); }
        });
    }

    private static String error(ExpenseDialog dialog) {
        return ((Label) dialog.getDialogPane().lookup("#wizard-editor-error")).getText();
    }

    private static void answerExpense(String amount) {
        Platform.runLater(() -> javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup("#wizard-input-description") != null).findFirst().ifPresent(pane -> {
                    ((TextField) pane.lookup("#wizard-input-description")).setText("Retirement spending");
                    ((TextField) pane.lookup("#wizard-input-amount")).setText(amount);
                    button(pane, "OK").fire();
                }));
    }
}
