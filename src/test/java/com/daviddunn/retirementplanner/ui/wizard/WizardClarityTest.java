package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class WizardClarityTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    static NewPlanDraft draft(boolean couple) {
        var draft = IncomeWizardStepTest.populatedHousehold(couple);
        draft.getPlan().setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.08"), new BigDecimal("0.03"),
                40, LocalDate.of(2026, 1, 1)));
        return draft;
    }

    static SocialSecurityIncome source(AccountOwnership owner, int year) {
        return new SocialSecurityIncome("Stored estimate", owner,
                owner == AccountOwnership.PRIMARY ? LocalDate.of(2031, 5, 6) : LocalDate.of(2033, 7, 8),
                null, new BigDecimal("3000.1200"), 67, BigDecimal.ZERO, year);
    }

    static AssumptionsView editor(NewRetirementPlanWizard wizard) throws Exception {
        List<NewPlanWizardStep> steps = field(wizard, "steps");
        return field(steps.get(5), "editor");
    }

    @Test void basicAdvancedGroupingUsesSameControlsAndFactoryValues() throws Exception {
        fx(() -> {
            var draft = draft(false);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            try {
                wizard.show();
                for (int index = 0; index < 5; index++) button(wizard.getDialogPane(), "Next").fire();
                var editor = editor(wizard);
                TitledPane advanced = field(editor, "guidedAdvanced");
                assertFalse(advanced.isExpanded());
                for (String name : List.of("projectionStartDatePicker", "projectionLengthField", "investmentReturnField",
                        "inflationRateField", "filingStatusComboBox", "stateJurisdictionComboBox")) {
                    Node input = field(editor, name);
                    assertFalse(descendant(input, advanced));
                }
                for (String name : List.of("healthcareInflationField", "socialSecurityColaField")) {
                    Node input = field(editor, name);
                    assertTrue(descendant(input, advanced.getContent()));
                }
                assertEquals("8", ((TextField) field(editor, "investmentReturnField")).getText());
                assertEquals("40", ((TextField) field(editor, "projectionLengthField")).getText());
                assertEquals(com.daviddunn.retirementplanner.domain.rules.FilingStatus.MARRIED_FILING_JOINTLY,
                        ((ComboBox<?>) field(editor, "filingStatusComboBox")).getValue());
                assertEquals(StateTaxJurisdiction.MI, ((ComboBox<?>) field(editor, "stateJurisdictionComboBox")).getValue());
                wizard.getDialogPane().applyCss(); wizard.getDialogPane().layout();
                assertTrue(advanced.isFocusTraversable());
            }
            finally { wizard.close(); }
        });
    }

    @Test void advancedSupportsKeyboardExpansionAfterWindowReceivesFocus() throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var draft = draft(false);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            reference.set(wizard);
            wizard.show();
            for (int index = 0; index < 5; index++) button(wizard.getDialogPane(), "Next").fire();
        });
        try {
            pulses();
            fx(() -> ((TitledPane) field(editor(reference.get()), "guidedAdvanced")).requestFocus());
            pulses();
            fx(() -> {
                TitledPane advanced = field(editor(reference.get()), "guidedAdvanced");
                assertTrue(advanced.isFocused());
                advanced.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.SPACE, false, false, false, false));
                assertTrue(advanced.isExpanded());
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @ParameterizedTest @ValueSource(strings = {"4.2500", "bad rate", "", "-"})
    void collapsePreservesPendingTextAndDraftWithoutApplyingOrClearingErrors(String raw) throws Exception {
        fx(() -> {
            var draft = draft(false);
            var step = new AssumptionsWizardStep(draft);
            AssumptionsView editor = field(step, "editor");
            TitledPane advanced = field(editor, "guidedAdvanced");
            TextField healthcare = field(editor, "healthcareInflationField");
            var assumptions = draft.getPlan().getPlanningAssumptions();
            healthcare.setText(raw);
            for (int index = 0; index < 3; index++) { advanced.setExpanded(true); advanced.setExpanded(false); }
            assertSame(healthcare, field(editor, "healthcareInflationField"));
            assertEquals(raw, healthcare.getText());
            assertSame(assumptions, draft.getPlan().getPlanningAssumptions());
            Label summary = (Label) editor.lookup("#wizard-advanced-summary");
            assertTrue(summary.getText().contains(raw.isBlank() ? "Needs attention" : raw));
            if (!raw.equals("4.2500")) {
                assertFalse(step.validateAndApply());
                assertTrue(advanced.isExpanded());
                advanced.setExpanded(false);
                ((TextField) field(editor, "investmentReturnField")).setText("7");
                assertTrue(healthcare.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
                assertEquals(raw, healthcare.getText());
                healthcare.setText("4.2500");
                assertTrue(step.validateAndApply());
                assertFalse(healthcare.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"healthcareInflationField", "socialSecurityColaField"})
    void nextExpandsHiddenAdvancedErrorAndRevealsFocusedField(String name) throws Exception {
        var reference = new AtomicReference<NewRetirementPlanWizard>();
        fx(() -> {
            var draft = draft(false);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            reference.set(wizard); wizard.show();
            for (int index = 0; index < 5; index++) button(wizard.getDialogPane(), "Next").fire();
            TextField input = field(editor(wizard), name);
            input.setText("malformed");
            button(wizard.getDialogPane(), "Next").fire();
        });
        try {
            pulses();
            fx(() -> {
                var wizard = reference.get();
                TextField input = field(editor(wizard), name);
                assertTrue(((TitledPane) field(editor(wizard), "guidedAdvanced")).isExpanded());
                assertEquals(5, (int) field(wizard, "stepIndex"));
                assertEquals("malformed", input.getText());
                assertSame(input, wizard.getDialogPane().getScene().getFocusOwner());
                ScrollPane page = (ScrollPane) wizard.getDialogPane().lookup("#wizard-page");
                var bounds = page.sceneToLocal(input.localToScene(input.getBoundsInLocal()));
                assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= page.getHeight());
            });
        }
        finally { fx(() -> reference.get().close()); }
    }

    @Test void reviewReconciliationCannotSkipCollapsedInvalidAdvanced() throws Exception {
        fx(() -> {
            var draft = draft(false);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            try {
                wizard.show();
                for (int index = 0; index < 6; index++) button(wizard.getDialogPane(), "Next").fire();
                ((Button) wizard.getDialogPane().lookup("#wizard-review-edit-assumptions")).fire();
                var editor = editor(wizard);
                ((TextField) field(editor, "socialSecurityColaField")).setText("invalid COLA");
                ((TitledPane) field(editor, "guidedAdvanced")).setExpanded(false);
                ((Button) wizard.getDialogPane().lookup("#wizard-step-review-6")).fire();
                assertEquals(5, (int) field(wizard, "stepIndex"));
                assertTrue(((TitledPane) field(editor, "guidedAdvanced")).isExpanded());
                ((TextField) field(editor, "socialSecurityColaField")).setText("2.2500");
                ((TextField) field(editor, "healthcareInflationField")).setText("4.5");
                ((Button) wizard.getDialogPane().lookup("#wizard-step-review-6")).fire();
                assertEquals(6, (int) field(wizard, "stepIndex"));
                String review = text(wizard.getDialogPane());
                assertTrue(review.contains("4.50%") && review.contains("2.25%"));
            }
            finally { wizard.close(); }
        });
    }

    @ParameterizedTest @ValueSource(ints = {2024, 2026, 2028})
    void valuationNoticeUsesActualYearsAndIsNonblockingWithoutRebasing(int year) throws Exception {
        fx(() -> {
            var draft = draft(false);
            var source = source(AccountOwnership.PRIMARY, year);
            draft.getPlan().getHousehold().getPrimaryPerson().addIncomeSource(source);
            var step = new SocialSecurityWizardStep(draft);
            var review = new ReviewWizardStep(draft);
            for (int visit = 0; visit < 3; visit++) { step.onEntering(); review.onEntering(); }
            assertEquals(year == 2026 ? 0 : 1, step.content().lookupAll(".wizard-review-warning").size());
            assertEquals(year == 2026 ? 0 : 1, review.content().lookupAll(".wizard-valuation-notice").size());
            if (year != 2026) {
                String warning = ((Label) step.content().lookup("#wizard-valuation-warning-primary")).getText();
                assertTrue(warning.contains("Primary") && warning.contains(String.valueOf(year)) && warning.contains("2026"));
            }
            assertTrue(step.validateAndApply());
            assertTrue(review.validateAndApply());
            assertSame(source, draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().getFirst());
            assertEquals(new BigDecimal("3000.1200"), source.getFullRetirementMonthlyBenefit());
            assertEquals(year, source.getBenefitValuationYear());
            assertEquals(LocalDate.of(2031, 5, 6), source.getStartDate());
        });
    }

    @Test void coupleWarningsIdentifyPeopleAndOnlyAppliedProjectionDatesAffectComparison() throws Exception {
        fx(() -> {
            var draft = draft(true);
            var primary = source(AccountOwnership.PRIMARY, 2024);
            var spouse = source(AccountOwnership.SPOUSE, 2028);
            draft.getPlan().getHousehold().getPrimaryPerson().addIncomeSource(primary);
            draft.getPlan().getHousehold().getSpouse().addIncomeSource(spouse);
            var step = new SocialSecurityWizardStep(draft);
            var assumptions = new AssumptionsWizardStep(draft);
            AssumptionsView editor = field(assumptions, "editor");
            DatePicker start = field(editor, "projectionStartDatePicker");
            start.getEditor().setText("not a projection date");
            assertFalse(assumptions.validateAndApply()); step.onEntering();
            assertTrue(((Label) step.content().lookup("#wizard-valuation-warning-spouse")).getText().contains("Sam Example"));
            assertTrue(text(step.content()).contains("projection begins in 2026"));
            start.setValue(LocalDate.of(2028, 1, 1));
            assertTrue(assumptions.validateAndApply()); step.onEntering();
            assertNull(step.content().lookup("#wizard-valuation-warning-spouse"));
            assertTrue(((Label) step.content().lookup("#wizard-valuation-warning-primary")).getText().contains("projection begins in 2028"));
            assertSame(primary, draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().getFirst());
            assertSame(spouse, draft.getPlan().getHousehold().getSpouse().getIncomeSources().getFirst());
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void spendingDetailsReadExactAnnualOrPurchaseValuesWithoutChangingOrder(boolean oneTime) throws Exception {
        fx(() -> {
            var draft = draft(false);
            var date = LocalDate.of(2030, 5, 6);
            var expense = new Expense("Entered spending", new BigDecimal("1234.5600"),
                    oneTime ? GrowthCategory.GENERAL : GrowthCategory.HEALTHCARE, date,
                    oneTime ? date : LocalDate.of(2040, 12, 31), oneTime ? ExpenseType.ONE_TIME : ExpenseType.RECURRING);
            var last = new Expense("Last", BigDecimal.TEN);
            draft.getPlan().getHousehold().addExpense(expense); draft.getPlan().getHousehold().addExpense(last);
            var step = new ExpensesWizardStep(draft);
            var table = (TableView<?>) step.content().lookup("#wizard-expenses");
            table.getSelectionModel().select(0);
            String details = ((Label) step.content().lookup("#wizard-spending-details")).getText();
            assertTrue(details.contains("1234.5600") && details.contains("2030-05-06") && details.contains("opening-year"));
            assertTrue(details.contains(oneTime ? "one-time purchase" : "/year, recurring"));
            assertTrue(details.contains(oneTime ? "General" : "Healthcare"));
            var review = new ReviewWizardStep(draft); review.onEntering();
            assertTrue(text(review.content()).contains(details));
            step.onEntering();
            assertEquals(List.of(expense, last), draft.getPlan().getHousehold().getExpenses());
            assertSame(expense, draft.getPlan().getHousehold().getExpenses().getFirst());
            assertTrue(step.validateAndApply());
        });
    }

    static String text(Node node) {
        return node.lookupAll(".label").stream().map(Label.class::cast).map(Label::getText).collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test void existingEditorYearActionAndRemovalRefreshWarningsWithoutImplicitRebasing() throws Exception {
        fx(() -> {
            var draft = draft(false);
            var source = source(AccountOwnership.PRIMARY, 2024);
            draft.getPlan().getHousehold().getPrimaryPerson().addIncomeSource(source);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                button(pane, "Next").fire(); button(pane, "Next").fire();
                assertNotNull(pane.lookup("#wizard-valuation-warning-primary"));
                CompleteWizardFixtures.edit(pane, "wizard-edit-social-security-primary", "wizard-input-fra-monthly-benefit", dialog -> {
                    assertEquals("3000.1200", ((TextField) dialog.lookup("#wizard-input-fra-monthly-benefit")).getText());
                    // Explicit existing action; the warning itself never performs this change.
                    dialog.lookupAll(".check-box").stream().map(CheckBox.class::cast)
                            .filter(box -> box.getText().equals("Use Today's-Dollar Convention")).findFirst().orElseThrow().setSelected(true);
                });
                assertNull(pane.lookup("#wizard-valuation-warning-primary"));
                var updated = (SocialSecurityIncome) draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().getFirst();
                assertEquals(2026, updated.getBenefitValuationYear());
                assertEquals(source.getFullRetirementMonthlyBenefit(), updated.getFullRetirementMonthlyBenefit());
                assertEquals(source.getStartDate(), updated.getStartDate());
                ((Button) pane.lookup("#wizard-remove-social-security-primary")).fire();
                assertNull(pane.lookup("#wizard-valuation-warning-primary"));
                assertTrue(draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().isEmpty());
                CompleteWizardFixtures.socialSecurity(pane, "1234.5600");
                assertNull(pane.lookup("#wizard-valuation-warning-primary"));
            }
            finally { wizard.close(); }
        });
    }

    @Test void mismatchWarningDoesNotPreventActualCreateAndKeepsSourceFacts() throws Exception {
        fx(() -> {
            var draft = draft(false);
            var source = source(AccountOwnership.PRIMARY, 2024);
            draft.getPlan().getHousehold().getPrimaryPerson().addIncomeSource(source);
            var wizard = new NewRetirementPlanWizard(null, draft, SevenStepWizardTest.pages(draft));
            wizard.show();
            for (int index = 0; index < 6; index++) button(wizard.getDialogPane(), "Next").fire();
            assertEquals(1, wizard.getDialogPane().lookupAll(".wizard-valuation-notice").size());
            button(wizard.getDialogPane(), "Create Plan").fire();
            assertNotNull(wizard.getResult());
            assertSame(source, wizard.getResult().getHousehold().getPrimaryPerson().getIncomeSources().getFirst());
        });
    }

    @Test void spendingEditorCancelPreservesExactOriginalDatesCategoryAndAmount() throws Exception {
        fx(() -> {
            var original = new Expense("Preserve me", new BigDecimal("1234.5600"), GrowthCategory.HEALTHCARE,
                    LocalDate.of(2030, 2, 3), LocalDate.of(2040, 4, 5), ExpenseType.RECURRING);
            var dialog = new com.daviddunn.retirementplanner.ui.dialogs.ExpenseDialog(original, true);
            dialog.show();
            ((TextField) field(dialog, "annualAmountField")).setText("99.9999");
            button(dialog.getDialogPane(), "Cancel").fire();
            assertNull(dialog.getResult());
            assertEquals(new BigDecimal("1234.5600"), original.getAnnualAmount());
            assertEquals(GrowthCategory.HEALTHCARE, original.getGrowthCategory());
            assertEquals(LocalDate.of(2030, 2, 3), original.getStartDate());
            assertEquals(LocalDate.of(2040, 4, 5), original.getEndDate());
        });
    }

    private static boolean descendant(Node child, Node parent) {
        for (Node node = child; node != null; node = node.getParent()) if (node == parent) return true;
        return false;
    }
}
