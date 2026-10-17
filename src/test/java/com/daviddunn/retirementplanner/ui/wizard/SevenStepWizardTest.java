package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
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

class SevenStepWizardTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    static List<NewPlanWizardStep> pages(NewPlanDraft draft) {
        return List.of(new HouseholdWizardStep(draft), new AccountsWizardStep(draft), new SocialSecurityWizardStep(draft),
                new IncomeWizardStep(draft), new ExpensesWizardStep(draft), new AssumptionsWizardStep(draft), new ReviewWizardStep(draft));
    }

    @Test void productionRegistersExactlySevenKeysAndSixAccessibleReviewLinks() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            try {
                List<NewPlanWizardStep> steps = field(wizard, "steps");
                assertEquals(List.of(NewPlanWizardStep.StepKey.HOUSEHOLD, NewPlanWizardStep.StepKey.ACCOUNTS,
                        NewPlanWizardStep.StepKey.SOCIAL_SECURITY, NewPlanWizardStep.StepKey.PENSIONS,
                        NewPlanWizardStep.StepKey.EXPENSES, NewPlanWizardStep.StepKey.ASSUMPTIONS,
                        NewPlanWizardStep.StepKey.REVIEW), steps.stream().map(NewPlanWizardStep::key).toList());
                wizard.show();
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "", "", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                for (int index = 0; index < 6; index++) button(pane, "Next").fire();
                for (String section : List.of("household", "accounts", "social-security", "pensions", "spending", "assumptions")) {
                    Button edit = (Button) pane.lookup("#wizard-review-edit-" + section);
                    assertNotNull(edit);
                    assertTrue(edit.getAccessibleText().startsWith("Edit "));
                }
                assertNull(pane.lookup("#wizard-review-edit-income"));
                assertEquals(6, pane.lookupAll(".wizard-review-section").size());
                assertEquals(4, pane.lookupAll(".label").stream().map(Label.class::cast)
                        .filter(label -> label.getText().equals("None entered (optional)")).count(),
                        "All four optional account/income/spending sections remain nonblocking");
                button(pane, "Create Plan").fire();
                assertNotNull(wizard.getResult());
            }
            finally { wizard.close(); }
        });
    }

    @Test void removalCancelDoesNotMutateDraftOrLoseRawSpouseTextEvenBeforeConfirmation() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(true);
            var spouse = draft.getPlan().getHousehold().getSpouse();
            var status = draft.getPlan().getPlanningAssumptions().getTaxAssumptions().getFilingStatus();
            var wizard = new NewRetirementPlanWizard(null, draft, pages(draft));
            try {
                wizard.show();
                var card = person(wizard.getDialogPane(), "spouse");
                DatePicker date = field(card, "birthDatePicker");
                date.getEditor().setText("unfinished birth date");
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Platform.runLater(() -> {
                    var dialog = confirmation();
                    try {
                        assertSame(spouse, draft.getPlan().getHousehold().getSpouse());
                        assertSame(card, person(wizard.getDialogPane(), "spouse"));
                        assertEquals("unfinished birth date", date.getEditor().getText());
                    }
                    catch (Throwable exception) { failure.set(exception); }
                    finally { button(dialog, "Cancel").fire(); }
                });
                ((Button) wizard.getDialogPane().lookup("#wizard-remove-spouse")).fire();
                if (failure.get() != null) throw new AssertionError(failure.get());
                assertSame(spouse, draft.getPlan().getHousehold().getSpouse());
                assertSame(card, person(wizard.getDialogPane(), "spouse"));
                assertEquals("unfinished birth date", date.getEditor().getText());
                assertEquals(status, draft.getPlan().getPlanningAssumptions().getTaxAssumptions().getFilingStatus());
                assertEquals(0, (int) field(wizard, "stepIndex"));
            }
            finally { wizard.close(); }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"joint-account", "spouse-account", "ss", "pension", "survivor"})
    void confirmedBlockedRemovalPreservesEveryRecordAndRawCard(String kind) throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(true);
            var household = draft.getPlan().getHousehold();
            if (kind.endsWith("account")) draft.getPlan().getAccountPortfolio().addAccount(new BrokerageAccount(
                    "Blocking asset", kind.startsWith("joint") ? AccountOwnership.JOINT : AccountOwnership.SPOUSE, new BigDecimal("123.4500")));
            else if (kind.equals("ss")) household.getSpouse().addIncomeSource(ss(AccountOwnership.SPOUSE));
            else if (kind.equals("pension")) household.getSpouse().addIncomeSource(pension(AccountOwnership.SPOUSE));
            else household.getPrimaryPerson().addIncomeSource(new Pension("Survivor", AccountOwnership.PRIMARY,
                    LocalDate.of(2030, 1, 1), null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE));
            var before = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .valueToTree(draft.getPlan());
            var wizard = new NewRetirementPlanWizard(null, draft, pages(draft));
            try {
                wizard.show();
                var card = person(wizard.getDialogPane(), "spouse");
                ((TextField) field(card, "firstNameField")).setText("Pending spouse label");
                answerSpouseRemoval(true);
                ((Button) wizard.getDialogPane().lookup("#wizard-remove-spouse")).fire();
                assertSame(household, draft.getPlan().getHousehold());
                assertSame(card, person(wizard.getDialogPane(), "spouse"));
                assertEquals("Pending spouse label", ((TextField) field(card, "firstNameField")).getText());
                assertEquals(before, new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .valueToTree(draft.getPlan()));
                assertTrue(((Label) wizard.getDialogPane().lookup("#wizard-validation")).getText().contains("Accounts, Social Security and Pensions"));
            }
            finally { wizard.close(); }
        });
    }

    @Test void pensionAndSocialSecurityEditsPreserveOtherSourcesOrderAndPrecision() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(true);
            var primary = draft.getPlan().getHousehold().getPrimaryPerson();
            var first = pension(AccountOwnership.PRIMARY);
            var benefit = ss(AccountOwnership.PRIMARY);
            var last = new Pension("Last pension", AccountOwnership.PRIMARY, LocalDate.of(2031, 1, 1), null,
                    new BigDecimal("432.1000"), new BigDecimal("0.0200"));
            primary.addIncomeSource(first); primary.addIncomeSource(benefit); primary.addIncomeSource(last);
            var wizard = new NewRetirementPlanWizard(null, draft, pages(draft));
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                button(pane, "Next").fire(); button(pane, "Next").fire();
                assertNull(pane.lookup("#wizard-add-social-security-primary"));
                assertNotNull(pane.lookup("#wizard-add-social-security-spouse"));
                CompleteWizardFixtures.edit(pane, "wizard-edit-social-security-primary", "wizard-input-fra-monthly-benefit", editor ->
                        ((TextField) editor.lookup("#wizard-input-fra-monthly-benefit")).setText("3210.4500"));
                assertSame(first, primary.getIncomeSources().getFirst());
                assertSame(last, primary.getIncomeSources().getLast());
                var updated = (SocialSecurityIncome) primary.getIncomeSources().get(1);
                assertEquals(new BigDecimal("3210.4500"), updated.getFullRetirementMonthlyBenefit());
                assertEquals(2024, updated.getBenefitValuationYear());
                button(pane, "Next").fire();
                TableView<?> pensions = (TableView<?>) pane.lookup("#wizard-pensions");
                assertEquals(List.of(first, last), pensions.getItems());
                pensions.getSelectionModel().select(0);
                CompleteWizardFixtures.edit(pane, "wizard-edit-income", "wizard-input-monthly-benefit", editor -> {
                    ((TextField) editor.lookup("#wizard-input-monthly-benefit")).setText("1111.2300");
                    ((TextField) editor.lookup("#wizard-input-annual-cola-rate")).setText("0.0200");
                });
                assertSame(updated, primary.getIncomeSources().get(1));
                assertSame(last, primary.getIncomeSources().getLast());
                var changed = (Pension) primary.getIncomeSources().getFirst();
                assertEquals(new BigDecimal("1111.2300"), changed.getMonthlyBenefit());
                assertEquals(new BigDecimal("0.0200"), changed.getAnnualColaRate());
                pensions.getSelectionModel().select(0);
                assertTrue(((Label) pane.lookup("#wizard-pension-details")).getText().contains("1111.2300"));
                ((Button) pane.lookup("#wizard-remove-income")).fire();
                assertEquals(List.of(updated, last), primary.getIncomeSources());
                button(pane, "Back").fire();
                ((Button) pane.lookup("#wizard-remove-social-security-primary")).fire();
                assertEquals(List.of(last), primary.getIncomeSources());
                assertNotNull(pane.lookup("#wizard-add-social-security-primary"));
            }
            finally { wizard.close(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void perPersonCardsDisplayStoredYearAndActualOwnerWithoutMutatingSources(boolean couple) throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(couple);
            var household = draft.getPlan().getHousehold();
            var primary = ss(AccountOwnership.PRIMARY);
            household.getPrimaryPerson().addIncomeSource(primary);
            if (couple) household.getSpouse().addIncomeSource(ss(AccountOwnership.SPOUSE));
            var step = new SocialSecurityWizardStep(draft);
            for (int visit = 0; visit < 4; visit++) step.onEntering();
            assertEquals(couple ? 2 : 1, step.content().lookupAll(".wizard-review-section").size());
            assertEquals(couple, step.content().lookup("#wizard-social-security-spouse") != null);
            assertTrue(step.content().lookupAll(".label").stream().map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("Benefit valuation year: 2024") && label.getText().contains("3000.1200")));
            assertSame(primary, household.getPrimaryPerson().getIncomeSources().getFirst());
            assertEquals(1, household.getPrimaryPerson().getIncomeSources().size());
        });
    }

    static DialogPane confirmation() {
        return List.copyOf(Window.getWindows()).stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> "wizard-remove-spouse-confirmation".equals(pane.getId())).findFirst().orElseThrow();
    }

    @Test void sameTypeOwnerAccountEditPreservesOpeningRmdHistoryAndPortfolioPosition() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            var plan = draft.getPlan();
            plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1950, 5, 6));
            plan.setPlanningAssumptions(new PlanningAssumptions(new BigDecimal("0.08"), new BigDecimal("0.03"),
                    40, LocalDate.of(2026, 1, 1)));
            var ira = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA("IRA", AccountOwnership.PRIMARY,
                    new BigDecimal("500000"));
            var cash = new BrokerageAccount("Last account", AccountOwnership.PRIMARY, new BigDecimal("123.4500"));
            plan.getAccountPortfolio().addAccount(ira); plan.getAccountPortfolio().addAccount(cash);
            new com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService().save(plan, java.util.Map.of(ira,
                    new com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService.OpeningRmdInput(
                            new BigDecimal("490000.1200"), new BigDecimal("1234.5600"))));
            var history = ira.getOpeningRmdAccountData();
            var wizard = new NewRetirementPlanWizard(null, draft, pages(draft));
            try {
                wizard.show();
                var pane = wizard.getDialogPane();
                button(pane, "Next").fire();
                ((TableView<?>) pane.lookup("#wizard-accounts")).getSelectionModel().select(0);
                CompleteWizardFixtures.edit(pane, "wizard-edit-account", "wizard-input-current-balance", editor ->
                        ((TextField) editor.lookup("#wizard-input-current-balance")).setText("600000.4500"));
                var edited = plan.getAccountPortfolio().getAccounts().getFirst();
                assertEquals(new BigDecimal("600000.4500"), edited.getCurrentBalance());
                assertSame(history, edited.getOpeningRmdAccountData());
                assertSame(cash, plan.getAccountPortfolio().getAccounts().getLast());
                assertEquals(2, plan.getAccountPortfolio().getAccounts().size());
            }
            finally { wizard.close(); }
        });
    }

    static SocialSecurityIncome ss(AccountOwnership owner) {
        return new SocialSecurityIncome("Stored estimate", owner, LocalDate.of(owner == AccountOwnership.PRIMARY ? 2031 : 2033,
                owner == AccountOwnership.PRIMARY ? 5 : 7, owner == AccountOwnership.PRIMARY ? 6 : 8), null,
                new BigDecimal("3000.1200"), 67, BigDecimal.ZERO, 2024);
    }

    static Pension pension(AccountOwnership owner) {
        return new Pension("Pension", owner, LocalDate.of(2030, 1, 1), null, new BigDecimal("1000.2500"), new BigDecimal("0.0200"));
    }
}
