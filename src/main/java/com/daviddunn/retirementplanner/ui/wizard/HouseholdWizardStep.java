package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;

public final class HouseholdWizardStep implements NewPlanWizardStep {

    private final NewPlanDraft draft;
    private final VBox content = new VBox(12);
    private final PersonCard primaryCard = new PersonCard(true);
    private final Button addSpouseButton = new Button("+ Add Spouse");
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
        validationMessage.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        validationMessage.visibleProperty().bind(validationMessage.textProperty().isNotEmpty());
        validationMessage.managedProperty().bind(validationMessage.visibleProperty());
        Label required = new Label("* Required. First and last names are optional.");
        required.getStyleClass().add("wizard-description");
        required.setWrapText(true);
        content.getChildren().addAll(required, heading("Primary Person"), primaryCard,
                addSpouseButton, validationMessage);
    }

    @Override
    public String title() {

        return "Household";
    }

    @Override
    public Node content() {

        return content;
    }

    private void addSpouse() {

        draft.getPlan().setSpouse(new Person("", "", null));
        spouseCard = new PersonCard(true);
        spouseCard.setId("wizard-spouse");
        Button remove = new Button("Remove Spouse");
        remove.setId("wizard-remove-spouse");
        InputHelp.install(remove, "Remove the spouse and discard their entries from this new-plan draft.");
        remove.setOnAction(event -> removeSpouse());
        HBox heading = new HBox(16, heading("Spouse Person"), remove);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        spouseSection = new VBox(8, heading, spouseCard);
        content.getChildren().add(content.getChildren().size() - 1, spouseSection);
        addSpouseButton.setVisible(false);
        addSpouseButton.setManaged(false);
        validationMessage.setText("");
        spouseCard.requestFocus();
    }

    private void removeSpouse() {

        draft.getPlan().setSpouse(null);
        content.getChildren().remove(spouseSection);
        spouseSection = null;
        spouseCard = null;
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
        validationMessage.setText("");
        return true;
    }

    private static Label heading(String text) {

        Label label = new Label(text);
        label.getStyleClass().add("wizard-person-heading");
        return label;
    }
}
