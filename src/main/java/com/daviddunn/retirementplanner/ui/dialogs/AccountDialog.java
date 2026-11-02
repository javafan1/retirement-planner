package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.RetirementParticipantFactsEditor;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport;
import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedRothIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import javafx.application.Platform;

public class AccountDialog extends Dialog<Account> {

    private final boolean hasSpouse;

    private final TextField nameField;
    private final Label nameLabel = new Label("Name:");
    private final Label nameError = new Label();
    private final ComboBox<AccountType> typeCombo;
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final TextField balanceField;
    private final RetirementParticipantFactsEditor participantFactsEditor;
    private final com.daviddunn.retirementplanner.ui.controls.EmployerRothAvailabilityEditor availabilityEditor;
    private ScrollPane formScrollPane;
    private boolean guidedCreation;
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();

    private final Label originalOwnerDobLabel;
    private final DatePicker originalOwnerDobPicker;

    private final Label originalOwnerDeathLabel;
    private final DatePicker originalOwnerDeathPicker;

    private final Label beneficiaryRelationshipLabel;
    private final ComboBox<BeneficiaryRelationship>
            beneficiaryRelationshipCombo;

    public AccountDialog(Account account) {
        this(account, true);
    }

    public AccountDialog(Account account, com.daviddunn.retirementplanner.domain.model.Household household) {
        this(account, household.hasSpouse());
    }

    public AccountDialog(Account account, com.daviddunn.retirementplanner.domain.model.Household household,
                         boolean guidedCreation) {
        this(account, household.hasSpouse());
        if (guidedCreation) {
            this.guidedCreation = true;
            updateForAccountTypeChange();
            var converter = getResultConverter();
            var fields = java.util.List.of(
                    new WizardDialogSupport.Field("Name", nameField,
                            WizardDialogSupport.Kind.TEXT, true),
                    new WizardDialogSupport.Field("Type", typeCombo,
                            WizardDialogSupport.Kind.CHOICE, true),
                    new WizardDialogSupport.Field("Owner", ownershipCombo,
                            WizardDialogSupport.Kind.CHOICE, true),
                    new WizardDialogSupport.Field("Current Balance", balanceField,
                            WizardDialogSupport.Kind.NUMBER, true),
                    new WizardDialogSupport.Field("Original Owner DOB", originalOwnerDobPicker,
                            WizardDialogSupport.Kind.DATE, true),
                    new WizardDialogSupport.Field("Original Owner Date of Death", originalOwnerDeathPicker,
                            WizardDialogSupport.Kind.DATE, true),
                    new WizardDialogSupport.Field("Beneficiary Relationship", beneficiaryRelationshipCombo,
                            WizardDialogSupport.Kind.CHOICE, true));
            WizardDialogSupport.install(this, fields,
                    () -> converter.call(ButtonType.OK));
            WizardDialogSupport.guidance(this, "Name is required. Balance is required when adding an account: enter dollars as of projection start. "
                    + "Choose the actual owner; retirement accounts cannot be Joint.");
        }
    }

    private AccountDialog(Account account, boolean hasSpouse) {
        this.hasSpouse = hasSpouse;
        if (!hasSpouse && account != null && account.getOwnership() != AccountOwnership.PRIMARY) {
            throw new IllegalArgumentException("Single-person accounts require Primary ownership; ownership is not reassigned automatically.");
        }

        if (account == null) {
            setTitle("Add Account");
            setHeaderText("Enter account information.");
        } else {
            setTitle("Edit Account");
            setHeaderText("Update account information.");
        }

        nameField = new TextField();
        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            nameError.setText("");
            nameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), false);
        });

        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(
                AccountFactory.getSupportedTypes());
        typeCombo.getSelectionModel().selectFirst();

        ownershipCombo = new ComboBox<>();
        updateOwnershipChoices();

        balanceField = new TextField();
        participantFactsEditor = new RetirementParticipantFactsEditor(
                account == null ? RetirementParticipantFacts.UNKNOWN
                        : account.getRetirementParticipantFacts());
        availabilityEditor = new com.daviddunn.retirementplanner.ui.controls.EmployerRothAvailabilityEditor(
                account == null ? null : account.getRothConversionAvailability(),
                account != null && com.daviddunn.retirementplanner.domain.roth.RothConversionEligibilityPolicy
                        .isEmployerPretaxSource(account.getType()) && account.getRothConversionAvailability() == null);

        /*
         * Inherited-account fields
         */

        originalOwnerDobLabel =
                new Label("Original Owner DOB:");

        originalOwnerDobPicker =
                new DatePicker();

        originalOwnerDeathLabel =
                new Label("Original Owner Date of Death:");

        originalOwnerDeathPicker =
                new DatePicker();

        beneficiaryRelationshipLabel =
                new Label("Beneficiary Relationship:");

        beneficiaryRelationshipCombo =
                new ComboBox<>();

        beneficiaryRelationshipCombo
                .getItems()
                .addAll(BeneficiaryRelationship.values());

        beneficiaryRelationshipCombo
                .getSelectionModel()
                .selectFirst();

        /*
         * Populate fields when editing.
         */

        if (account != null) {

            nameField.setText(
                    account.getName());

            typeCombo.setValue(
                    account.getType());

            ownershipCombo.setValue(
                    account.getOwnership());

            balanceField.setText(
                    account.getCurrentBalance()
                            .toPlainString());

            InheritedAccountInformation inheritedInfo =
                    getInheritedInformation(account);

            if (inheritedInfo != null) {

                originalOwnerDobPicker.setValue(
                        inheritedInfo
                                .getOriginalOwnerDateOfBirth());

                originalOwnerDeathPicker.setValue(
                        inheritedInfo
                                .getOriginalOwnerDateOfDeath());

                beneficiaryRelationshipCombo.setValue(
                        inheritedInfo
                                .getBeneficiaryRelationship());
            }
        }

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        nameField.setId("account-name");
        nameError.setId("account-name-error");
        nameError.getStyleClass().add("wizard-field-error");
        nameError.setWrapText(true);
        nameError.managedProperty().bind(nameError.textProperty().isNotEmpty());
        nameError.visibleProperty().bind(nameError.managedProperty());
        grid.add(nameLabel, 0, row);

        grid.add(
                nameField,
                1,
                row++);
        grid.add(nameError, 0, row++, 2, 1);

        Label typeLabel = new Label("Type:");
        grid.add(
                typeLabel,
                0,
                row);

        grid.add(
                typeCombo,
                1,
                row++);

        Label ownerLabel = new Label("Owner:");
        grid.add(
                ownerLabel,
                0,
                row);

        grid.add(
                ownershipCombo,
                1,
                row++);

        Label balanceLabel = new Label("Current Balance:");
        grid.add(
                balanceLabel,
                0,
                row);

        grid.add(
                balanceField,
                1,
                row++);

        grid.add(
                originalOwnerDobLabel,
                0,
                row);

        grid.add(
                originalOwnerDobPicker,
                1,
                row++);

        grid.add(
                originalOwnerDeathLabel,
                0,
                row);

        grid.add(
                originalOwnerDeathPicker,
                1,
                row++);

        grid.add(
                beneficiaryRelationshipLabel,
                0,
                row);

        grid.add(
                beneficiaryRelationshipCombo,
                1,
                row);
        grid.add(participantFactsEditor, 0, row + 1, 2, 1);
        grid.add(availabilityEditor, 0, row + 2, 2, 1);
        int legendRow = row + 3;
        grid.add(RequiredFieldSupport.createLegend(), 0, legendRow, 2, 1);

        requiredFieldAttachments.add(RequiredFieldSupport.attach(nameLabel, nameField));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(typeLabel, typeCombo));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(ownerLabel, ownershipCombo));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(balanceLabel, balanceField));
        var inheritedRequired = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> isInheritedType(typeCombo.getValue()), typeCombo.valueProperty());
        requiredFieldAttachments.add(RequiredFieldSupport.attach(originalOwnerDobLabel,
                originalOwnerDobPicker, inheritedRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(originalOwnerDeathLabel,
                originalOwnerDeathPicker, inheritedRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(beneficiaryRelationshipLabel,
                beneficiaryRelationshipCombo, inheritedRequired));

        /*
         * Show inherited fields only when appropriate.
         */

        typeCombo.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateForAccountTypeChange());

        updateForAccountTypeChange();

        InputHelp.install(typeCombo, "Account category determines modeled tax treatment, RMD rules and eligibility for withdrawals or Roth conversions. Inherited IRAs require original-owner and beneficiary information.");
        InputHelp.install(nameField, "Display name for this modeled account. Leading and trailing whitespace is removed when saved.");
        InputHelp.install(ownershipCombo, "Person who owns the account. Retirement accounts require Primary or Spouse ownership; only eligible non-retirement accounts allow Joint. RMDs and Roth transfers follow the individual owner.");
        InputHelp.install(balanceField, "Current account value in dollars used as the opening investable balance. Enter the balance at the projection start; prior December 31 RMD balances are entered separately.");
        InputHelp.install(originalOwnerDobPicker, "Original deceased owner's birth date, not the beneficiary's. Required for inherited IRAs and used with death date and relationship to determine inherited-account RMD treatment.");
        InputHelp.install(originalOwnerDeathPicker, "Original owner's date of death. Required for inherited IRAs; its year helps determine the applicable inherited distribution rules and deadlines.");
        InputHelp.install(beneficiaryRelationshipCombo, "Beneficiary's relationship to the original owner. Used with inherited IRA dates to select the modeled distribution treatment; it does not designate a new beneficiary for this account.");
        InputHelp.linkGridLabels(grid);
        getDialogPane().setContent(grid);

        // Resize after the optional section changes its preferred height.
        // Without animation the queued resize sees the complete expanded form.
        participantFactsEditor.setAnimated(false);
        participantFactsEditor.expandedProperty().addListener(
                (observable, oldValue, newValue) -> resizeToContent());
        availabilityEditor.setOnPreferredHeightChanged(this::resizeToContent);

        setResizable(true);
        setOnShowing(event -> installScrollableForm());
        setOnShown(event -> resizeToContent());
        setOnHidden(event -> {
            requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
            requiredFieldAttachments.clear();
            participantFactsEditor.close();
            availabilityEditor.close();
        });

        getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                com.daviddunn.retirementplanner.domain.model.RequiredNameValidation
                        .normalize(nameField.getText(), "Account name");
            } catch (IllegalArgumentException invalid) {
                event.consume();
                nameError.setText(invalid.getMessage());
                nameField.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), true);
                nameField.requestFocus();
                Platform.runLater(() -> scrollTo(nameField));
            }
        });
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (availabilityEditor.isManaged() && !availabilityEditor.validateInput()) event.consume();
        });

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name = com.daviddunn.retirementplanner.domain.model.RequiredNameValidation
                    .normalize(nameField.getText(), "Account name");

            AccountType type =
                    typeCombo.getValue();

            AccountOwnership ownership =
                    ownershipCombo.getValue();

            if (ownership == null) {
                throw new IllegalArgumentException(
                        "Select PRIMARY or SPOUSE for an individually owned retirement account.");
            }

            BigDecimal balance =
                    new BigDecimal(
                            balanceField
                                    .getText()
                                    .trim());

            if (isInheritedType(type)) {

                if (originalOwnerDobPicker.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Original owner date of birth is required.");
                }

                if (originalOwnerDeathPicker.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Original owner date of death is required.");
                }

                if (beneficiaryRelationshipCombo.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Beneficiary relationship is required.");
                }

                InheritedAccountInformation inheritedInfo =
                        new InheritedAccountInformation(
                                originalOwnerDobPicker.getValue(),
                                originalOwnerDeathPicker.getValue(),
                                beneficiaryRelationshipCombo.getValue());

                return AccountFactory.createInherited(
                        type,
                        name,
                        ownership,
                        balance,
                        inheritedInfo);
            }

            Account result = AccountFactory.create(
                    type,
                    name,
                    ownership,
                    balance);
            if (result.supportsRetirementParticipantFacts()) {
                result.setRetirementParticipantFacts(participantFactsEditor.value());
            }
            if (com.daviddunn.retirementplanner.domain.roth.RothConversionEligibilityPolicy.isEmployerPretaxSource(type)) {
                result.setRothConversionAvailability(availabilityEditor.value());
            }
            return result;
        });
    }

    private boolean isInheritedType(
            AccountType type) {

        return type ==
                AccountType.INHERITED_TRADITIONAL_IRA
                ||
                type ==
                        AccountType.INHERITED_ROTH_IRA;
    }

    private void updateInheritedFieldsVisibility() {

        boolean visible =
                isInheritedType(
                        typeCombo.getValue());

        originalOwnerDobLabel.setVisible(visible);
        originalOwnerDobLabel.setManaged(visible);

        originalOwnerDobPicker.setVisible(visible);
        originalOwnerDobPicker.setManaged(visible);

        originalOwnerDeathLabel.setVisible(visible);
        originalOwnerDeathLabel.setManaged(visible);

        originalOwnerDeathPicker.setVisible(visible);
        originalOwnerDeathPicker.setManaged(visible);

        beneficiaryRelationshipLabel.setVisible(visible);
        beneficiaryRelationshipLabel.setManaged(visible);

        beneficiaryRelationshipCombo.setVisible(visible);
        beneficiaryRelationshipCombo.setManaged(visible);

        resizeToContent();
    }

    private void resizeToContent() {

        Platform.runLater(() -> {

            if (getDialogPane().getScene() != null &&
                    getDialogPane().getScene().getWindow() != null) {
                applyScreenBounds();
            }
        });
    }

    private void scrollTo(Node node) {
        if (formScrollPane == null || formScrollPane.getContent() == null) return;
        var viewport = formScrollPane.getViewportBounds();
        var content = formScrollPane.getContent();
        var bounds = node.localToScene(node.getBoundsInLocal());
        var viewportBounds = formScrollPane.localToScene(viewport);
        double range = Math.max(1, content.getLayoutBounds().getHeight() - viewport.getHeight());
        double delta = bounds.getMinY() < viewportBounds.getMinY()
                ? bounds.getMinY() - viewportBounds.getMinY()
                : bounds.getMaxY() > viewportBounds.getMaxY()
                ? bounds.getMaxY() - viewportBounds.getMaxY() : 0;
        if (delta != 0) formScrollPane.setVvalue(Math.max(0, Math.min(1,
                formScrollPane.getVvalue() + delta / range)));
    }

    private void installScrollableForm() {
        if (formScrollPane != null) return;
        Node form = getDialogPane().getContent();
        formScrollPane = new ScrollPane(form);
        formScrollPane.setId("account-dialog-form-scroll");
        formScrollPane.setFitToWidth(true);
        formScrollPane.setFitToHeight(false);
        formScrollPane.setPannable(true);
        formScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        getDialogPane().setContent(formScrollPane);
        getDialogPane().setPrefWidth(560);
    }

    /** Keeps the DialogPane action bar outside the bounded, scrollable form. */
    private void applyScreenBounds() {
        if (formScrollPane == null || getDialogPane().getScene() == null) return;
        Window dialogWindow = getDialogPane().getScene().getWindow();
        Rectangle2D visual = visualBounds(dialogWindow);
        double outerMargin = 24;
        double maxWindowHeight = Math.max(320, visual.getHeight() - outerMargin);
        double maxWindowWidth = Math.max(280, visual.getWidth() - outerMargin);
        double maxFormHeight = Math.max(220, maxWindowHeight - 170);

        getDialogPane().setMaxWidth(maxWindowWidth);
        getDialogPane().setPrefWidth(Math.min(560, maxWindowWidth));
        formScrollPane.setMaxWidth(maxWindowWidth - 24);
        formScrollPane.setMaxHeight(maxFormHeight);
        getDialogPane().applyCss();
        getDialogPane().layout();
        Node form = formScrollPane.getContent();
        double naturalHeight = form.prefHeight(Math.max(200, formScrollPane.getWidth()));
        if (!Double.isFinite(naturalHeight) || naturalHeight <= 0) naturalHeight = maxFormHeight;
        formScrollPane.setPrefViewportHeight(Math.min(naturalHeight, maxFormHeight));

        if (dialogWindow instanceof javafx.stage.Stage stage) {
            stage.setMaxHeight(maxWindowHeight);
            stage.setMaxWidth(maxWindowWidth);
            stage.sizeToScene();
            if (stage.getHeight() > maxWindowHeight) stage.setHeight(maxWindowHeight);
            if (stage.getWidth() > maxWindowWidth) stage.setWidth(maxWindowWidth);
        }
    }

    private Rectangle2D visualBounds(Window dialogWindow) {
        Window owner = getOwner();
        double x = owner == null ? dialogWindow.getX() : owner.getX();
        double y = owner == null ? dialogWindow.getY() : owner.getY();
        double width = owner == null ? dialogWindow.getWidth() : owner.getWidth();
        double height = owner == null ? dialogWindow.getHeight() : owner.getHeight();
        return Screen.getScreensForRectangle(x, y, width, height).stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private void updateForAccountTypeChange() {

        AccountType type = typeCombo.getValue();
        boolean employer = type == AccountType.TRADITIONAL_401K
                || type == AccountType.TRADITIONAL_403B || type == AccountType.ROTH_401K;
        participantFactsEditor.setVisible(employer && !guidedCreation);
        participantFactsEditor.setManaged(employer && !guidedCreation);
        boolean traditionalEmployer = com.daviddunn.retirementplanner.domain.roth.RothConversionEligibilityPolicy
                .isEmployerPretaxSource(type);
        availabilityEditor.setVisible(traditionalEmployer);
        availabilityEditor.setManaged(traditionalEmployer);

        updateOwnershipChoices();
        updateInheritedFieldsVisibility();
    }

    private void updateOwnershipChoices() {

        AccountType type = typeCombo.getValue();

        AccountOwnership currentOwnership =
                ownershipCombo.getValue();

        List<AccountOwnership> allowedOwnerships =
                !hasSpouse ? List.of(AccountOwnership.PRIMARY) : type != null &&
                        type.requiresIndividualOwnership()
                        ? List.of(
                                AccountOwnership.PRIMARY,
                                AccountOwnership.SPOUSE)
                        : List.of(AccountOwnership.values());

        ownershipCombo.getItems().setAll(
                allowedOwnerships);

        if (currentOwnership != null &&
                allowedOwnerships.contains(currentOwnership)) {
            ownershipCombo.setValue(currentOwnership);
            return;
        }

        if (currentOwnership == AccountOwnership.JOINT &&
                type != null &&
                type.requiresIndividualOwnership()) {
            ownershipCombo.setValue(null);
            return;
        }

        ownershipCombo.getSelectionModel().selectFirst();
    }

    private InheritedAccountInformation getInheritedInformation(
            Account account) {

        if (account instanceof InheritedTraditionalIRA inherited) {

            return inherited
                    .getInheritedAccountInformation();
        }

        if (account instanceof InheritedRothIRA inherited) {

            return inherited
                    .getInheritedAccountInformation();
        }

        return null;
    }
}
