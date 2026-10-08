package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.BrokerageAccount;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.MortalityCategory;
import com.daviddunn.retirementplanner.ui.views.AssumptionsView;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CompleteWizardTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void sixStepsHaveCorrectOrderProgressButtonsAndValuesSurviveNavigation() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                String[] steps = {"Household", "Accounts", "Income", "Expenses", "Assumptions", "Review"};
                for (int index = 0; index < steps.length; index++) {
                    assertEquals(index == 5, button(pane, "Create Plan").isVisible());
                    assertEquals(index < 5, button(pane, "Next").isVisible());
                    assertEquals(index == 0, button(pane, "Back").isDisabled());
                    assertEquals((index + 1.0) / 6, ((ProgressBar) pane.lookup("#wizard-progress")).getProgress());
                    Label label = field(wizard, "stepLabel");
                    assertTrue(label.getText().endsWith(steps[index]));
                    if (index < 5) button(pane, "Next").fire();
                }
                ((Button) pane.lookup("#wizard-review-edit-household")).fire();
                assertEquals("Alex", ((TextField) field(person(pane, "primary"), "firstNameField")).getText());
                button(pane, "Next").fire();
                button(pane, "Back").fire();
                assertEquals("Example", ((TextField) field(person(pane, "primary"), "lastNameField")).getText());
                assertNull(wizard.getResult());
            }
            finally { wizard.close(); }
        });
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void cancelFromEveryStepProducesNoCompletedPlan(int target) throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "Draft", "Only", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            for (int index = 0; index < target; index++) button(pane, "Next").fire();
            button(pane, "Cancel").fire();
            assertNull(wizard.getResult());
            assertFalse(wizard.isShowing());
        });
    }

    @Test void reviewWarningsDoNotPreventLegalEmptyPlanCreation() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            var pane = wizard.getDialogPane();
            fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
            for (int index = 0; index < 5; index++) button(pane, "Next").fire();
            var warnings = (Label) pane.lookup("#wizard-review-warnings");
            assertTrue(warnings.getText().contains("No accounts"));
            assertTrue(warnings.getText().contains("No retirement spending"));
            assertFalse(warnings.getText().contains("No pension"));
            button(pane, "Create Plan").fire();
            assertNotNull(wizard.getResult());
            assertEquals(1, wizard.getResult().getHousehold().members().size());
        });
    }

    @Test void reviewReflectsBackwardChangesAndRevalidatesEntireDraftBeforeCreate() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                var pane = wizard.getDialogPane();
                fill(person(pane, "primary"), "Alex", "Example", LocalDate.of(1964, 5, 6), MortalityCategory.FEMALE);
                for (int index = 0; index < 5; index++) button(pane, "Next").fire();
                ((Button) pane.lookup("#wizard-review-edit-assumptions")).fire();
                ((TextField) pane.lookup("#wizard-assumption-return")).setText("6.5");
                ((TextField) pane.lookup("#wizard-assumption-length")).setText("25");
                button(pane, "Next").fire();
                assertTrue(NewRetirementPlanWizardTest.descendants(pane).filter(Label.class::isInstance).map(Label.class::cast)
                        .anyMatch(label -> label.getText().contains("6.50%") && label.getText().contains("25 years")));
                NewPlanDraft draft = field(wizard, "draft");
                draft.getPlan().getAccountPortfolio().addAccount(new BrokerageAccount("Dangling spouse", AccountOwnership.SPOUSE, BigDecimal.TEN));
                button(pane, "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertNull(wizard.getResult());
                Label label = field(wizard, "stepLabel");
                assertTrue(label.getText().endsWith("Accounts"));
            }
            finally { wizard.close(); }
        });
    }

    @Test void reviewErrorsAreSeparateFromWarningsAndBlockValidation() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            draft.getPlan().getAccountPortfolio().addAccount(new BrokerageAccount("Invalid owner", AccountOwnership.SPOUSE, BigDecimal.ONE));
            var review = new ReviewWizardStep(draft);
            review.onEntering();
            assertFalse(review.validateAndApply());
            Label error = field(review, "error");
            assertTrue(error.getText().contains("Cannot create"));
            assertTrue(error.getText().contains("Invalid owner"));
        });
    }

    @Test void createCannotBeTriggeredBeforeReviewEvenProgrammatically() throws Exception {
        fx(() -> {
            var wizard = new NewRetirementPlanWizard(null);
            wizard.show();
            try {
                button(wizard.getDialogPane(), "Create Plan").fire();
                assertTrue(wizard.isShowing());
                assertNull(wizard.getResult());
            }
            finally { wizard.close(); }
        });
    }

    @Test void reviewRequestsAuthoritativeOpeningRmdHistoryWithoutInventingBalances() throws Exception {
        fx(() -> {
            var draft = IncomeWizardStepTest.populatedHousehold(false);
            draft.getPlan().getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1950, 5, 6));
            draft.getPlan().setPlanningAssumptions(new com.daviddunn.retirementplanner.domain.model.PlanningAssumptions(
                    new BigDecimal("0.08"), new BigDecimal("0.03"), 40, LocalDate.of(2026, 1, 1)));
            var ira = new com.daviddunn.retirementplanner.domain.financial.TraditionalIRA(
                    "Retirement IRA", AccountOwnership.PRIMARY, new BigDecimal("800000"));
            draft.getPlan().getAccountPortfolio().addAccount(ira);
            var review = new ReviewWizardStep(draft);
            review.onEntering();
            assertTrue(((Label) review.content().lookup("#wizard-review-warnings")).getText().contains("Retirement IRA"));
            assertNotNull(review.content().lookup("#wizard-opening-rmd"));
            assertNull(ira.getOpeningRmdAccountData());
            assertTrue(review.validateAndApply(), "Historical facts are a readiness reminder, not an invented creation rule");
            new com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService().save(draft.getPlan(), java.util.Map.of(ira,
                    new com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService.OpeningRmdInput(
                            new BigDecimal("790000"), BigDecimal.ZERO)));
            review.onEntering();
            assertFalse(((Label) review.content().lookup("#wizard-review-warnings")).getText().contains("Opening RMD"));
            assertEquals(new BigDecimal("790000"), ira.getOpeningRmdAccountData().getPriorDecember31Balance());
            assertEquals(new BigDecimal("800000"), ira.getCurrentBalance());
        });
    }
}
