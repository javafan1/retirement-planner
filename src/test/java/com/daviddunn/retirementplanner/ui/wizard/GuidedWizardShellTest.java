package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class GuidedWizardShellTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @ParameterizedTest
    @ValueSource(strings = {"household", "accounts", "social-security", "pensions", "spending", "assumptions"})
    void everyReviewLinkUsesIdentityRatherThanPosition(String section) throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            // Deliberately exchange Income and Accounts to detect index-based Review routing.
            var pages = List.<NewPlanWizardStep>of(new HouseholdWizardStep(draft), new SocialSecurityWizardStep(draft), new IncomeWizardStep(draft),
                    new AccountsWizardStep(draft), new ExpensesWizardStep(draft), new AssumptionsWizardStep(draft), new ReviewWizardStep(draft));
            var wizard = new NewRetirementPlanWizard(null, draft, pages);
            try {
                wizard.show();
                for (int index = 0; index < 6; index++) button(wizard.getDialogPane(), "Next").fire();
                ((Button) wizard.getDialogPane().lookup("#wizard-review-edit-" + section)).fire();
                int index = field(wizard, "stepIndex");
                assertEquals(section.equals("spending") ? "EXPENSES" : section.replace('-', '_').toUpperCase(java.util.Locale.ROOT), pages.get(index).key().name());
            }
            finally { wizard.close(); }
        });
    }

    @Test void futureStepsDisabledAndVisitedStepsPreserveRawDateAndPendingName() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                Button accounts = (Button) pane.lookup("#wizard-step-accounts-1");
                assertTrue(accounts.isDisabled());
                accounts.fire();
                assertEquals(0, (int) field(wizard, "stepIndex"));
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                var card = person(pane, "primary");
                button(pane, "Next").fire();
                assertFalse(accounts.isDisabled());
                ((Button) pane.lookup("#wizard-step-household-0")).fire();
                DatePicker date = field(card, "birthDatePicker");
                date.getEditor().setText("unfinished date");
                TextField name = field(card, "firstNameField");
                name.setText("Pending name");
                accounts.fire();
                assertEquals(1, (int) field(wizard, "stepIndex"));
                button(pane, "Back").fire();
                assertSame(card, person(pane, "primary"));
                assertEquals("unfinished date", date.getEditor().getText());
                assertEquals("Pending name", name.getText());
                button(pane, "Next").fire();
                assertEquals(0, (int) field(wizard, "stepIndex"));
            }
            finally { wizard.close(); }
        });
    }

    @Test void jumpingToReviewValidatesPendingEarlierEditsAndRefreshesSummary() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                for (int index = 0; index < 6; index++) button(pane, "Next").fire();
                ((Button) pane.lookup("#wizard-review-edit-household")).fire();
                TextField name = field(person(pane, "primary"), "firstNameField");
                DatePicker date = field(person(pane, "primary"), "birthDatePicker");
                date.getEditor().setText("invalid");
                ((Button) pane.lookup("#wizard-step-review-6")).fire();
                assertEquals(0, (int) field(wizard, "stepIndex"));
                assertEquals("invalid", date.getEditor().getText());
                date.setValue(LocalDate.of(1964, 5, 7));
                name.setText("Updated label");
                ((Button) pane.lookup("#wizard-step-review-6")).fire();
                assertEquals(6, (int) field(wizard, "stepIndex"));
                assertTrue(pane.lookupAll(".label").stream().map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("Updated label")));
            }
            finally { wizard.close(); }
        });
    }

    @Test void horizonErrorSurvivesUnrelatedEditAndNavigationUntilOwnCorrection() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                for (int index = 0; index < 5; index++) button(pane, "Next").fire();
                TextField horizon = (TextField) pane.lookup("#wizard-assumption-length");
                horizon.setText("unfinished");
                button(pane, "Next").fire();
                var invalid = PseudoClass.getPseudoClass("invalid");
                assertTrue(horizon.getPseudoClassStates().contains(invalid));
                ((TextField) pane.lookup("#wizard-assumption-return")).setText("6.5");
                assertTrue(horizon.getPseudoClassStates().contains(invalid));
                assertTrue(pane.lookupAll(".wizard-field-error").stream().map(Label.class::cast)
                        .anyMatch(label -> label.isVisible() && !label.getText().isBlank()));
                button(pane, "Back").fire();
                button(pane, "Next").fire();
                assertSame(horizon, pane.lookup("#wizard-assumption-length"));
                assertEquals("unfinished", horizon.getText());
                horizon.setText("30");
                assertTrue(horizon.getPseudoClassStates().contains(invalid), "Typing alone does not prove a correction valid");
                button(pane, "Next").fire();
                assertFalse(horizon.getPseudoClassStates().contains(invalid));
                assertEquals(6, (int) field(wizard, "stepIndex"));
            }
            finally { wizard.close(); }
        });
    }

    @Test void responsiveSelectorRetainsPageAndFooterOutsideScroller() throws Exception {
        AtomicReference<NewRetirementPlanWizard> reference = new AtomicReference<>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard);
            wizard.show();
            wizard.getDialogPane().resize(490, 570);
        });
        try {
            fx(() -> {
                var pane = reference.get().getDialogPane();
                pane.applyCss(); pane.resize(490, 570); pane.layout(); pane.layout();
                Node card = person(pane, "primary");
                assertFalse(pane.lookup("#wizard-sidebar").isManaged());
                var selector = (ComboBox<?>) pane.lookup("#wizard-compact-navigation");
                assertTrue(selector.isManaged());
                assertEquals(1, selector.getItems().size());
                assertEquals("Previously visited wizard steps", selector.getAccessibleText());
                var scroll = (ScrollPane) pane.lookup("#wizard-page");
                assertFalse(isDescendant(button(pane, "Next"), scroll));
                pane.resize(980, 740); pane.layout(); pane.layout();
                assertTrue(pane.lookup("#wizard-sidebar").isManaged());
                assertFalse(selector.isManaged());
                assertSame(card, person(pane, "primary"));
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void compactSelectorAcceptsKeyboardPreviousStep() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                button(pane, "Next").fire();
                pane.resize(490, 570); pane.applyCss(); pane.layout(); pane.layout();
                var selector = (ComboBox<?>) pane.lookup("#wizard-compact-navigation");
                assertTrue(selector.isManaged());
                selector.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.UP, false, false, false, false));
                assertEquals(0, (int) field(wizard, "stepIndex"));
                assertTrue(button(pane, "Next").isDefaultButton());
                assertTrue(button(pane, "Cancel").isCancelButton());
            }
            finally { wizard.close(); }
        });
    }

    @Test void invalidFieldIsFocusedAndRevealedAfterNext() throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            reference.set(wizard);
            wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            for (int index = 0; index < 5; index++) button(pane, "Next").fire();
            pane.resize(490, 530); pane.applyCss(); pane.layout(); pane.layout();
            ((TextField) pane.lookup("#wizard-assumption-return")).setText("bad rate");
            button(pane, "Next").fire();
        });
        try {
            fx(() -> {
                var pane = reference.get().getDialogPane();
                var input = pane.lookup("#wizard-assumption-return");
                assertSame(input, pane.getScene().getFocusOwner());
                var page = (ScrollPane) pane.lookup("#wizard-page");
                assertTrue(page.getVvalue() > 0);
                page.layout();
                var bounds = page.sceneToLocal(input.localToScene(input.getBoundsInLocal()));
                assertTrue(bounds.getMinY() >= 0);
                assertTrue(bounds.getMaxY() <= page.getHeight());
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void fieldHelpAndIssueAreKeyboardAccessibleAndPresentationOnly() throws Exception {
        fx(() -> {
            TextField input = new TextField("0.0200");
            var row = new WizardFieldRow("Example", WizardFieldRow.Requiredness.OPTIONAL, input,
                    "Decimal fraction", "Explanation, not a default value.");
            assertEquals("0.0200", input.getText());
            assertTrue(input.getAccessibleText().contains("optional"));
            Button help = (Button) row.getChildren().get(3);
            assertTrue(help.isFocusTraversable());
            help.fire();
            assertTrue(row.getChildren().get(4).isManaged());
            input.setTooltip(new Tooltip("Updated owner-aware explanation"));
            assertEquals("Updated owner-aware explanation", ((Label) row.getChildren().get(4)).getText());
            assertEquals("Updated owner-aware explanation", input.getAccessibleHelp());
            row.setIssue("Correct this field");
            new TextField().setText("Unrelated");
            assertTrue(input.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            input.setText("0.0300");
            assertTrue(input.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            row.clearIssue(); // The existing editor explicitly acknowledges successful revalidation.
            assertFalse(input.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            assertEquals("0.0300", input.getText());
        });
    }

    @Test void visitedNavigationPreservesAccountIncomeExpenseObjectsAndOrdering() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                CompleteWizardFixtures.enterThroughReview(pane, true);
                NewPlanDraft draft = field(wizard, "draft");
                var plan = draft.getPlan();
                var accounts = List.copyOf(plan.getAccountPortfolio().getAccounts());
                var income = plan.getHousehold().members().stream().flatMap(person -> person.getIncomeSources().stream()).toList();
                var expenses = List.copyOf(plan.getHousehold().getExpenses());
                for (String section : List.of("household", "accounts", "social-security", "pensions", "spending", "assumptions")) {
                    ((Button) pane.lookup("#wizard-review-edit-" + section)).fire();
                    ((Button) pane.lookup("#wizard-step-review-6")).fire();
                }
                assertIterableEquals(accounts, plan.getAccountPortfolio().getAccounts());
                assertIterableEquals(income, plan.getHousehold().members().stream().flatMap(person -> person.getIncomeSources().stream()).toList());
                assertIterableEquals(expenses, plan.getHousehold().getExpenses());
            }
            finally { wizard.close(); }
        });
    }

    private static boolean isDescendant(Node child, Node ancestor) {
        for (Node node = child; node != null; node = node.getParent()) if (node == ancestor) return true;
        return false;
    }
}
