package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public final class HouseholdWizardStep implements NewPlanWizardStep {

    private final NewPlanDraft draft;
    private final VBox content = new VBox(12);
    private final PersonCard primaryCard = new PersonCard(true);
    private final Button addSpouseButton = new Button("Me and my spouse");
    private final Label householdChoice = new Label("Household: Just me");
    private final FlowPane householdChoices = new FlowPane(10, 8);
    private Button removeSpouseButton;
    private final Label validationMessage = new Label();
    private VBox spouseSection;
    private PersonCard spouseCard;

    public HouseholdWizardStep(NewPlanDraft draft) {

        this.draft = Objects.requireNonNull(draft, "Draft is required.");
        primaryCard.setId("wizard-primary");
        primaryCard.load(draft.getPlan().getHousehold().getPrimaryPerson());
        addSpouseButton.setId("wizard-add-spouse");
        InputHelp.install(addSpouseButton, "Optionally include a spouse. Leave this as a one-person household if you are planning for yourself.");
        addSpouseButton.setOnAction(event -> addSpouse());
        validationMessage.setId("wizard-validation");
        validationMessage.getStyleClass().add("wizard-field-error");
        validationMessage.setWrapText(true);
        validationMessage.setFocusTraversable(true);
        validationMessage.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        validationMessage.visibleProperty().bind(validationMessage.textProperty().isNotEmpty());
        validationMessage.managedProperty().bind(validationMessage.visibleProperty());
        Label required = new Label("* Required. First and last names are optional.");
        required.getStyleClass().add("wizard-description");
        required.setWrapText(true);
        Label introduction = guidance("Welcome. Create a new retirement plan, then edit it in the existing tabs. "
                + "Optional financial sections may be left empty. Creating does not save a file; plans stay local on your computer when saved.");
        Label categoryHelp = guidance("Date of birth and mortality table category are required for each included person. "
                + "The category selects the table used for longevity calculations; it is not inferred from a name or birth date. "
                + "Household selection does not change filing status.");
        householdChoices.getChildren().setAll(householdChoice, addSpouseButton);
        content.getChildren().addAll(introduction, householdChoices, required, categoryHelp, heading("Primary Person"), primaryCard,
                validationMessage);
        if (draft.getPlan().getHousehold().hasSpouse()) showSpouse();
    }

    @Override
    public StepKey key() { return StepKey.HOUSEHOLD; }

    @Override
    public String title() {

        return "Welcome and Household";
    }

    @Override
    public Node content() {

        return content;
    }

    private void addSpouse() {

        draft.getPlan().setSpouse(new Person("", "", null));
        showSpouse();
    }

    private void showSpouse() {
        spouseCard = new PersonCard(true);
        spouseCard.setId("wizard-spouse");
        spouseCard.load(draft.getPlan().getHousehold().requireSpouse("New Plan Wizard"));
        Button remove = new Button("Just me");
        removeSpouseButton = remove;
        remove.setId("wizard-remove-spouse");
        remove.setAccessibleText("Change household to Just me; confirm removal of spouse information");
        InputHelp.install(remove, "Remove the spouse from this draft. Spouse-owned or joint financial records and survivor benefits must first be removed or reassigned; nothing is deleted automatically.");
        remove.setOnAction(event -> removeSpouse());
        householdChoices.getChildren().add(remove);
        spouseSection = new VBox(8, heading("Spouse Person"), spouseCard);
        content.getChildren().add(content.getChildren().size() - 1, spouseSection);
        addSpouseButton.setVisible(false);
        addSpouseButton.setManaged(false);
        householdChoice.setText("Household: Me and my spouse");
        validationMessage.setText("");
        spouseCard.requestFocus();
    }

    private void removeSpouse() {

        ButtonType remove = new ButtonType("Remove Spouse", ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Your spouse's entered person information will be discarded if removal succeeds. "
                        + "Spouse-owned or joint accounts and income are never deleted or reassigned automatically.",
                remove, ButtonType.CANCEL);
        confirmation.setTitle("Confirm household change");
        confirmation.setHeaderText("Change to Just me and remove your spouse?");
        confirmation.getDialogPane().setId("wizard-remove-spouse-confirmation");
        if (content.getScene() != null) confirmation.initOwner(content.getScene().getWindow());
        ((Button) confirmation.getDialogPane().lookupButton(remove)).setDefaultButton(false);
        ((Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != remove) return;
        try {
            draft.getPlan().setSpouse(null);
        }
        catch (IllegalArgumentException exception) {
            validationMessage.setText(exception.getMessage()
                    + " Review Accounts, Social Security and Pensions to resolve the named dependencies manually. Nothing was removed.");
            javafx.application.Platform.runLater(() -> {
                if (content.getScene() == null) return;
                var viewport = content.getScene().lookup("#wizard-page");
                if (viewport instanceof javafx.scene.control.ScrollPane scroll) scroll.setVvalue(1);
                validationMessage.requestFocus();
            });
            return;
        }
        content.getChildren().remove(spouseSection);
        spouseSection = null;
        spouseCard = null;
        householdChoices.getChildren().remove(removeSpouseButton);
        removeSpouseButton = null;
        householdChoice.setText("Household: Just me");
        addSpouseButton.setVisible(true);
        addSpouseButton.setManaged(true);
        validationMessage.setText("");
        addSpouseButton.requestFocus();
    }

    @Override
    public boolean validateAndApply() {

        PersonCard.Edit primary;
        PersonCard.Edit spouse = null;
        String person = "Primary Person";
        try {
            primary = primaryCard.readValidated();
            if (spouseCard != null) {
                person = "Spouse Person";
                spouse = spouseCard.readValidated();
            }
        }
        catch (IllegalArgumentException exception) {
            validationMessage.setText(person + ": " + exception.getMessage());
            return false;
        }
        primary.applyTo(draft.getPlan().getHousehold().getPrimaryPerson());
        if (spouse != null) {
            spouse.applyTo(draft.getPlan().getHousehold().requireSpouse("New Plan Wizard"));
        }
        draft.refreshSocialSecurityDates();
        validationMessage.setText("");
        return true;
    }

    private static Label heading(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("wizard-person-heading");
        return label;
    }

    private static Label guidance(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        label.getStyleClass().add("wizard-description");
        return label;
    }
}
