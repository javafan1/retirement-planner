package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import javafx.geometry.Insets;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.Objects;

public class HouseholdView extends VBox {

    private final PersonCard primaryPersonCard;
    private final PersonCard spousePersonCard;
    private final CheckBox includeSpouse = new CheckBox("Include spouse (optional)");
    private final TitledPane spousePane;
    private final Button applyButton = new Button("Apply");
    private final Button cancelButton = new Button("Cancel");
    private final Label statusLabel = new Label();
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private RetirementPlan currentPlan;
    private Runnable onPlanChanged;
    private boolean loading;

    public HouseholdView() {

        primaryPersonCard = new PersonCard();
        spousePersonCard = new PersonCard();
        spousePane = createSpousePane();
        includeSpouse.setId("include-spouse");
        InputHelp.install(includeSpouse, "Select to include a spouse in this household. Leave unchecked for a Primary-only plan. Removing a spouse is blocked while financial records depend on that person; nothing is deleted automatically.");
        spousePane.visibleProperty().bind(includeSpouse.selectedProperty());
        spousePane.managedProperty().bind(includeSpouse.selectedProperty());
        includeSpouse.selectedProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        primaryPersonCard.setOnEdited(this::updateDirty);
        spousePersonCard.setOnEdited(this::updateDirty);
        applyButton.disableProperty().bind(dirty.not());
        cancelButton.disableProperty().bind(dirty.not());
        applyButton.setOnAction(event -> applyChanges());
        cancelButton.setOnAction(event -> cancelChanges());
        HBox buttonBar = new HBox(10, applyButton, cancelButton);
        buttonBar.setPadding(new Insets(10));

        setSpacing(15);
        setPadding(new Insets(15));

        getChildren().addAll(
                createPrimaryPersonPane(),
                includeSpouse,
                spousePane,
                buttonBar,
                statusLabel
        );
    }

    public void load(RetirementPlan plan) {

        currentPlan = Objects.requireNonNull(plan);
        loading = true;
        Household household = plan.getHousehold();

        primaryPersonCard.load(household.getPrimaryPerson());
        spousePersonCard.load(household.spouse().orElse(null));
        includeSpouse.setSelected(household.hasSpouse());
        loading = false;
        statusLabel.setText("");
        updateDirty();
    }

    public boolean save(RetirementPlan plan) {

        return plan == currentPlan && applyChanges();
    }

    public void refresh(RetirementPlan plan) {

        if (plan != currentPlan || !isDirty()) {
            load(plan);
        }
    }

    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void setOnPlanChanged(Runnable onPlanChanged) {
        this.onPlanChanged = onPlanChanged;
    }

    public boolean applyChanges() {

        if (currentPlan == null) {
            return false;
        }
        PersonCard.Edit primary;
        PersonCard.Edit spouse;
        try {
            primary = primaryPersonCard.readValidated();
            spouse = includeSpouse.isSelected() ? spousePersonCard.readValidated() : null;
        }
        catch (IllegalArgumentException exception) {
            statusLabel.setText(exception.getMessage());
            return false;
        }

        if (!isDirty()) {
            return true;
        }

        // Validate all selected people and membership before changing existing person fields.
        Household household = currentPlan.getHousehold();
        try {
            if (!includeSpouse.isSelected()) {
                currentPlan.setSpouse(null);
            }
            else if (!household.hasSpouse()) {
                Person added = new Person(spouse.firstName(), spouse.lastName(), spouse.birthDate());
                spouse.applyTo(added);
                currentPlan.setSpouse(added);
            }
        }
        catch (IllegalArgumentException exception) {
            statusLabel.setText(exception.getMessage());
            return false;
        }
        primary.applyTo(currentPlan.getHousehold().getPrimaryPerson());
        if (spouse != null) spouse.applyTo(currentPlan.getHousehold().getSpouse());
        load(currentPlan);
        statusLabel.setText("Household changes applied.");
        if (onPlanChanged != null) {
            onPlanChanged.run();
        }
        return true;
    }

    public void cancelChanges() {

        if (currentPlan != null) {
            load(currentPlan);
        }
    }

    private void updateDirty() {

        if (!loading) {
            dirty.set(currentPlan != null
                    && (!primaryPersonCard.matches(currentPlan.getHousehold().getPrimaryPerson())
                    || includeSpouse.isSelected() != currentPlan.getHousehold().hasSpouse()
                    || (includeSpouse.isSelected() && currentPlan.getHousehold().hasSpouse()
                    && !spousePersonCard.matches(currentPlan.getHousehold().getSpouse()))));
        }
    }

    private TitledPane createPrimaryPersonPane() {

        TitledPane pane = new TitledPane("Primary Person", primaryPersonCard);
        pane.setCollapsible(false);
        return pane;

    }

    private TitledPane createSpousePane() {

        TitledPane pane = new TitledPane("Spouse Person", spousePersonCard);
        pane.setCollapsible(false);
        return pane;
    }
}
