package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.dialogs.PensionDialog;
import com.daviddunn.retirementplanner.ui.dialogs.SocialSecurityDialog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class IncomeWizardStepTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    static NewPlanDraft populatedHousehold(boolean couple) {
        var draft = new NewPlanDraft();
        var primary = draft.getPlan().getHousehold().getPrimaryPerson();
        primary.setBirthDate(LocalDate.of(1964, 5, 6));
        primary.setMortalityCategory(MortalityCategory.FEMALE);
        if (couple) {
            var spouse = new Person("Sam", "Example", LocalDate.of(1966, 7, 8));
            spouse.setMortalityCategory(MortalityCategory.MALE);
            draft.getPlan().setSpouse(spouse);
        }
        return draft;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void socialSecurityUsesActualOwnerFraBasisAndDerivedClaimingDate(boolean spouse) throws Exception {
        fx(() -> {
            var draft = populatedHousehold(spouse);
            var owner = spouse ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY;
            var dialog = new SocialSecurityDialog(null, 2026, draft.getPlan().getHousehold(), List.of(owner));
            dialog.show();
            ((TextField) field(dialog, "fraBenefitField")).setText("2750.25");
            ((ComboBox<Integer>) field(dialog, "claimingAgeCombo")).setValue(70);
            DatePicker date = field(dialog, "startDatePicker");
            assertTrue(date.isDisabled());
            var person = draft.getPlan().getHousehold().peopleByOwner().get(owner);
            assertEquals(person.getBirthDate().plusYears(70), date.getValue());
            button(dialog.getDialogPane(), "OK").fire();
            var income = dialog.getResult();
            assertEquals(new BigDecimal("2750.25"), income.getFullRetirementMonthlyBenefit());
            assertEquals(70, income.getClaimingAge());
            assertEquals(owner, income.getOwnership());
            assertEquals(2026, income.getBenefitValuationYear());
            assertEquals(person.getBirthDate().plusYears(70), income.getStartDate());
        });
    }

    @Test void missingSocialSecurityBenefitIsInlineAndDoesNotCreateIncome() throws Exception {
        fx(() -> {
            var draft = populatedHousehold(false);
            var dialog = new SocialSecurityDialog(null, 2026, draft.getPlan().getHousehold(), List.of(AccountOwnership.PRIMARY));
            dialog.show();
            try {
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(dialog.isShowing());
                assertNull(dialog.getResult());
                assertTrue(((Label) dialog.getDialogPane().lookup("#wizard-editor-error")).getText().contains("FRA Monthly Benefit"));
            }
            finally { dialog.close(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void pensionCollectsDatesAmountColaAndOnlyApplicableSurvivorBenefit(boolean couple) throws Exception {
        fx(() -> {
            var draft = populatedHousehold(couple);
            var dialog = new PensionDialog(null, draft.getPlan().getHousehold(), true);
            dialog.show();
            ((TextField) field(dialog, "nameField")).setText("Employer pension");
            ((DatePicker) field(dialog, "startDatePicker")).setValue(LocalDate.of(2030, 1, 1));
            ((DatePicker) field(dialog, "endDatePicker")).setValue(LocalDate.of(2050, 12, 31));
            ((TextField) field(dialog, "monthlyBenefitField")).setText("2000");
            ((TextField) field(dialog, "colaRateField")).setText("0.02");
            TextField survivor = field(dialog, "survivorMonthlyBenefitField");
            assertEquals(couple, survivor.isVisible());
            if (couple) {
                ((ComboBox<AccountOwnership>) field(dialog, "ownershipCombo")).setValue(AccountOwnership.SPOUSE);
                survivor.setText("1000");
            }
            button(dialog.getDialogPane(), "OK").fire();
            var pension = dialog.getResult();
            assertEquals(couple ? AccountOwnership.SPOUSE : AccountOwnership.PRIMARY, pension.getOwnership());
            assertEquals(new BigDecimal("2000"), pension.getMonthlyBenefit());
            assertEquals(new BigDecimal("0.02"), pension.getAnnualColaRate());
            assertEquals(LocalDate.of(2050, 12, 31), pension.getEndDate());
            if (couple) assertEquals(new BigDecimal("1000"), pension.getSurvivorMonthlyBenefit());
            else assertNull(pension.getSurvivorMonthlyBenefit());
        });
    }

    @Test void backwardBirthDateChangesRederiveSocialSecurityWithoutChangingBenefitBasis() throws Exception {
        fx(() -> {
            var draft = populatedHousehold(false);
            var person = draft.getPlan().getHousehold().getPrimaryPerson();
            var source = new SocialSecurityIncome("SS", AccountOwnership.PRIMARY, person.getBirthDate().plusYears(67),
                    null, new BigDecimal("3000"), 67, BigDecimal.ZERO, 2026);
            person.addIncomeSource(source);
            person.setBirthDate(LocalDate.of(1965, 4, 3));
            draft.refreshSocialSecurityDates();
            var updated = (SocialSecurityIncome) person.getIncomeSources().getFirst();
            assertEquals(LocalDate.of(2032, 4, 3), updated.getStartDate());
            assertEquals(source.getFullRetirementMonthlyBenefit(), updated.getFullRetirementMonthlyBenefit());
            assertEquals(2026, updated.getBenefitValuationYear());
        });
    }

    @Test void addEditRemoveIncomeAndNoDuplicateInitialSocialSecurityRecords() throws Exception {
        fx(() -> {
            var draft = populatedHousehold(false);
            var step = new IncomeWizardStep(draft);
            var stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) step.content(), 680, 450));
            stage.show();
            try {
                answerSocialSecurity("3000");
                ((Button) step.content().lookup("#wizard-add-social-security")).fire();
                assertEquals(1, draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().size());
                assertTrue(step.content().lookup("#wizard-add-social-security").isDisabled());
                TableView<?> table = (TableView<?>) step.content().lookup("#wizard-income");
                table.getSelectionModel().select(0);
                answerSocialSecurity("3100");
                ((Button) step.content().lookup("#wizard-edit-income")).fire();
                assertEquals(new BigDecimal("3100"), ((SocialSecurityIncome) table.getItems().getFirst()).getFullRetirementMonthlyBenefit());
                table.getSelectionModel().select(0);
                ((Button) step.content().lookup("#wizard-remove-income")).fire();
                assertTrue(draft.getPlan().getHousehold().getPrimaryPerson().getIncomeSources().isEmpty());
                assertFalse(step.content().lookup("#wizard-add-social-security").isDisabled());
            }
            finally { stage.close(); }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"ss", "spouse-pension", "primary-survivor-pension"})
    void existingMembershipGuardBlocksAllIncomeDependencies(String kind) throws Exception {
        fx(() -> {
            var draft = populatedHousehold(true);
            var household = draft.getPlan().getHousehold();
            if (kind.equals("ss")) household.getSpouse().addIncomeSource(new SocialSecurityIncome(
                    "Spouse Social Security", AccountOwnership.SPOUSE, LocalDate.of(2033, 7, 8),
                    null, BigDecimal.TEN, 67, BigDecimal.ZERO, 2026));
            else if (kind.equals("spouse-pension")) household.getSpouse().addIncomeSource(new Pension(
                    "Spouse pension", AccountOwnership.SPOUSE, LocalDate.of(2030, 1, 1), null, BigDecimal.TEN, BigDecimal.ZERO));
            else household.getPrimaryPerson().addIncomeSource(new Pension("Survivor pension", AccountOwnership.PRIMARY,
                    LocalDate.of(2030, 1, 1), null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE));
            var wizard = new NewRetirementPlanWizard(null, draft, List.of(new HouseholdWizardStep(draft)));
            wizard.show();
            try {
                ((Button) wizard.getDialogPane().lookup("#wizard-remove-spouse")).fire();
                String message = ((Label) wizard.getDialogPane().lookup("#wizard-validation")).getText();
                assertTrue(message.contains("pension") || message.contains("Social Security"));
                assertSame(household, draft.getPlan().getHousehold());
                assertTrue(household.hasSpouse());
                assertNotNull(person(wizard.getDialogPane(), "spouse"));
            }
            finally { wizard.close(); }
        });
    }

    private static void answerSocialSecurity(String benefit) {
        Platform.runLater(() -> javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup("#wizard-input-fra-monthly-benefit") != null).findFirst().ifPresent(pane -> {
                    ((TextField) pane.lookup("#wizard-input-fra-monthly-benefit")).setText(benefit);
                    button(pane, "OK").fire();
                }));
    }
}
