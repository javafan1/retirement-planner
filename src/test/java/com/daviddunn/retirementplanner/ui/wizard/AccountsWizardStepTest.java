package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.ui.dialogs.AccountDialog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.daviddunn.retirementplanner.ui.wizard.NewPlanWizardTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountsWizardStepTest {
    @BeforeAll static void init() throws Exception { startFx(); }

    @Test void zeroAccountsRemainLegal() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var step = new AccountsWizardStep(draft);
            assertTrue(step.validateAndApply());
            assertTrue(draft.getPlan().getAccountPortfolio().getAccounts().isEmpty());
        });
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void authoritativeTypesAndTaxClassificationsSurviveCreation(AccountType type) throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var dialog = new AccountDialog(null, draft.getPlan().getHousehold(), true);
            dialog.show();
            ((ComboBox<AccountType>) field(dialog, "typeCombo")).setValue(type);
            ((TextField) field(dialog, "nameField")).setText("Initial account");
            ((TextField) field(dialog, "balanceField")).setText("123456.78");
            if (type == AccountType.INHERITED_ROTH_IRA || type == AccountType.INHERITED_TRADITIONAL_IRA) {
                ((DatePicker) field(dialog, "originalOwnerDobPicker")).setValue(LocalDate.of(1930, 1, 1));
                ((DatePicker) field(dialog, "originalOwnerDeathPicker")).setValue(LocalDate.of(2024, 1, 1));
            }
            button(dialog.getDialogPane(), "OK").fire();
            assertFalse(dialog.isShowing());
            Account account = dialog.getResult();
            assertEquals(type, account.getType());
            assertEquals(type.getTaxTreatment(), account.getTaxTreatment());
            assertEquals(AccountOwnership.PRIMARY, account.getOwnership());
            assertEquals(new BigDecimal("123456.78"), account.getCurrentBalance());
        });
    }

    @Test void ownershipChoicesUseHouseholdAndAccountTypeRules() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var single = new AccountDialog(null, draft.getPlan().getHousehold(), true);
            ComboBox<AccountOwnership> owner = field(single, "ownershipCombo");
            assertEquals(java.util.List.of(AccountOwnership.PRIMARY), owner.getItems());
            draft.getPlan().setSpouse(new Person("Sam", "Example", null));
            var couple = new AccountDialog(null, draft.getPlan().getHousehold(), true);
            ComboBox<AccountType> types = field(couple, "typeCombo");
            owner = field(couple, "ownershipCombo");
            assertFalse(owner.getItems().contains(AccountOwnership.JOINT));
            types.setValue(AccountType.BROKERAGE);
            assertTrue(owner.getItems().contains(AccountOwnership.JOINT));
            owner.setValue(AccountOwnership.JOINT);
            types.setValue(AccountType.ROTH_IRA);
            assertNull(owner.getValue());
            assertFalse(owner.getItems().contains(AccountOwnership.JOINT));
        });
    }

    @Test void malformedBalanceAndMissingInheritedDatesStayInEditor() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var dialog = new AccountDialog(null, draft.getPlan().getHousehold(), true);
            dialog.show();
            try {
                TextField balance = field(dialog, "balanceField");
                balance.setText("not money");
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(dialog.isShowing());
                assertTrue(balance.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid")));
                balance.setText("100");
                ((ComboBox<AccountType>) field(dialog, "typeCombo")).setValue(AccountType.INHERITED_ROTH_IRA);
                button(dialog.getDialogPane(), "OK").fire();
                assertTrue(dialog.isShowing());
                assertTrue(((Label) dialog.getDialogPane().lookup("#wizard-editor-error")).getText().contains("DOB"));
                assertTrue(draft.getPlan().getAccountPortfolio().getAccounts().isEmpty());
            }
            finally { dialog.close(); }
        });
    }

    @Test void listAddEditRemoveUsesDraftPortfolioOnly() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var step = new AccountsWizardStep(draft);
            var stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) step.content(), 680, 400));
            stage.show();
            try {
                answerAccount("150000");
                ((Button) step.content().lookup("#wizard-add-account")).fire();
                assertEquals(1, draft.getPlan().getAccountPortfolio().getAccounts().size());
                var table = (TableView<?>) step.content().lookup("#wizard-accounts");
                table.getSelectionModel().select(0);
                answerAccount("160000");
                ((Button) step.content().lookup("#wizard-edit-account")).fire();
                assertEquals(new BigDecimal("160000"), draft.getPlan().getAccountPortfolio().getTotalBalance());
                table.getSelectionModel().select(0);
                ((Button) step.content().lookup("#wizard-remove-account")).fire();
                assertTrue(draft.getPlan().getAccountPortfolio().getAccounts().isEmpty());
            }
            finally { stage.close(); }
        });
    }

    @Test void spouseRemovalIsBlockedWithoutDeletionOrReassignment() throws Exception {
        fx(() -> {
            var draft = new NewPlanDraft();
            var household = new HouseholdWizardStep(draft);
            var wizard = new NewRetirementPlanWizard(null, draft, java.util.List.of(household));
            wizard.show();
            try {
                ((Button) wizard.getDialogPane().lookup("#wizard-add-spouse")).fire();
                var account = new BrokerageAccount("Joint savings", AccountOwnership.JOINT, BigDecimal.TEN);
                draft.getPlan().getAccountPortfolio().addAccount(account);
                ((Button) wizard.getDialogPane().lookup("#wizard-remove-spouse")).fire();
                assertTrue(draft.getPlan().getHousehold().hasSpouse());
                assertSame(account, draft.getPlan().getAccountPortfolio().getAccounts().getFirst());
                assertEquals(AccountOwnership.JOINT, account.getOwnership());
                assertTrue(((Label) wizard.getDialogPane().lookup("#wizard-validation")).getText().contains("Joint savings"));
            }
            finally { wizard.close(); }
        });
    }

    private static void answerAccount(String balance) {
        Platform.runLater(() -> javafx.stage.Window.getWindows().stream()
                .filter(window -> window.isShowing() && window.getScene().getRoot() instanceof DialogPane)
                .map(window -> (DialogPane) window.getScene().getRoot())
                .filter(pane -> pane.lookup("#wizard-input-current-balance") != null)
                .findFirst().ifPresent(pane -> {
                    ((TextField) pane.lookup("#wizard-input-name")).setText("Funding");
                    ((TextField) pane.lookup("#wizard-input-current-balance")).setText(balance);
                    button(pane, "OK").fire();
                }));
    }
}
