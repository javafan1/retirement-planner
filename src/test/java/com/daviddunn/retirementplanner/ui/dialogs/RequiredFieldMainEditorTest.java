package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.domain.roth.EmployerRothConversionAvailability;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RequiredFieldMainEditorTest {
    @BeforeAll static void startFx() throws Exception {
        FutureTask<Void> start = new FutureTask<>(() -> { Platform.setImplicitExit(false); return null; });
        try { Platform.startup(start); } catch (IllegalStateException started) { Platform.runLater(start); }
        start.get(20, TimeUnit.SECONDS);
    }

    @Test void accountRequirementsAreTypeSensitiveAndNamesRemainUnmarked() throws Exception {
        fx(() -> {
            var dialog = new AccountDialog(null, new Household(
                    new Person("Primary", "Person", LocalDate.of(1960, 1, 1)),
                    new Person("Spouse", "Person", LocalDate.of(1962, 1, 1))));
            assertTrue(marked(label(dialog, "Type:")));
            assertTrue(marked(label(dialog, "Owner:")));
            assertTrue(marked(label(dialog, "Current Balance:")));
            assertTrue(marked(label(dialog, "Name:")));
            Label dob = label(dialog, "Original Owner DOB:");
            assertFalse(marked(dob));
            @SuppressWarnings("unchecked") ComboBox<AccountType> type = (ComboBox<AccountType>) label(dialog, "Type:").getLabelFor();
            type.setValue(AccountType.INHERITED_TRADITIONAL_IRA);
            assertTrue(marked(dob));
            assertTrue(marked(label(dialog, "Original Owner Date of Death:")));
            assertTrue(marked(label(dialog, "Beneficiary Relationship:")));
            type.setValue(AccountType.TRADITIONAL_401K);
            var participantFacts = (javafx.scene.control.TitledPane) dialog.getDialogPane().lookup("#retirement-participant-facts");
            assertFalse(marked(label(participantFacts.getContent(), "Separated from employer:")));
            participantFacts.setExpanded(true);
            assertTrue(marked(label(participantFacts.getContent(), "Separated from employer:")));
            assertTrue(marked(label(participantFacts.getContent(), "Plan retirement conditions:")));
            assertFalse(marked(label(participantFacts.getContent(), "Separation date (optional):")));
            assertFalse(marked(label(participantFacts.getContent(), "Conditions met date (optional):")));
            Label availability = label(dialog, "Availability status:");
            assertTrue(marked(availability));
            assertEquals("Availability status:", availability.getText());
            Label firstYear = label(dialog, "First eligible calendar year (optional)");
            assertFalse(marked(firstYear));
            dialog.getDialogPane().getButtonTypes().clear();
        });
    }

    @Test void pensionSocialSecurityExpenseAndAssetEditorsMarkOnlyValidatedInputs() throws Exception {
        fx(() -> {
            var household = new Household(new Person("Primary", "Person", LocalDate.of(1960, 1, 1)),
                    new Person("Spouse", "Person", LocalDate.of(1962, 1, 1)));
            var pension = new PensionDialog(null, household);
            var pensionFacts = (javafx.scene.control.TitledPane) pension.getDialogPane().lookup("#retirement-participant-facts");
            assertFalse(marked(label(pensionFacts.getContent(), "Separated from employer:")));
            pensionFacts.setExpanded(true);
            assertTrue(marked(label(pension, "Owner:")));
            assertTrue(marked(label(pension, "Start Date:")));
            assertTrue(marked(label(pension, "Monthly Benefit:")));
            assertTrue(marked(label(pension, "Annual COLA Rate:")));
            assertTrue(marked(label(pension, "Name:")));
            assertFalse(marked(label(pension, "End Date:")));
            assertFalse(marked(label(pension, "Survivor Monthly Benefit:")));
            assertTrue(marked(label(pensionFacts.getContent(), "Separated from employer:")));
            assertTrue(marked(label(pensionFacts.getContent(), "Plan retirement conditions:")));

            var socialSecurity = new SocialSecurityDialog(null, 2026, household);
            assertTrue(marked(label(socialSecurity, "Owner:")));
            assertTrue(marked(label(socialSecurity, "Gross FRA Monthly Benefit ($/month):")));
            assertTrue(marked(label(socialSecurity, "Claiming Age:")));
            assertFalse(marked(label(socialSecurity, "Benefit Start Date:")));
            assertFalse(marked(label(socialSecurity, "Name:")));

            var expense = new ExpenseDialog(null);
            assertTrue(marked(label(expense, "Description:")));
            assertTrue(marked(label(expense, "Expense Type:")));
            assertTrue(marked(label(expense, "Annual Amount:")));
            assertFalse(marked(label(expense, "Effective Date:")));
            assertFalse(marked(label(expense, "Annual Expense Volatility (%):")));
            @SuppressWarnings("unchecked") ComboBox<com.daviddunn.retirementplanner.domain.financial.ExpenseType> expenseType =
                    (ComboBox<com.daviddunn.retirementplanner.domain.financial.ExpenseType>) label(expense, "Expense Type:").getLabelFor();
            expenseType.setValue(com.daviddunn.retirementplanner.domain.financial.ExpenseType.ONE_TIME);
            assertTrue(marked(label(expense, "Purchase Date:")));
            assertEquals("Annual Expense Volatility (%):", label(expense,
                    "Annual Expense Volatility (%):").getText());

            var asset = new NonInvestableAssetDialog(null);
            assertTrue(marked(label(asset, "Asset Name:")));
            assertTrue(marked(label(asset, "Current Value:")));
            assertTrue(marked(label(asset, "Growth Rate (%):")));
        });
    }

    private static Label label(Dialog<?> dialog, String text) {
        return find(dialog.getDialogPane(), text);
    }

    private static Label label(Node node, String text) {
        if (node instanceof Parent parent) return find(parent, text);
        throw new AssertionError("Expected a parent for label lookup: " + text);
    }

    private static Label find(Parent root, String text) {
        if (root instanceof Label label && text.equals(label.getText())) return label;
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof Parent parent) {
                try { return find(parent, text); } catch (AssertionError ignored) { }
            }
        }
        throw new AssertionError("Missing label: " + text);
    }

    private static boolean marked(Label label) {
        if (!(label.getGraphic() instanceof javafx.scene.text.TextFlow flow)) return false;
        Text text = flow.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
                .filter(candidate -> candidate.getStyleClass().contains("required-field-indicator"))
                .findFirst().orElse(null);
        if (text == null) return false;
        assertEquals("*", text.getText());
        return text.isVisible();
    }

    private static void fx(Checked action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
