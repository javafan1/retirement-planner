package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import javafx.geometry.Insets;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.Objects;

public class HouseholdView extends VBox {

    private final PersonCard primaryPersonCard;
    private final PersonCard spousePersonCard;
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
                createSpousePane(),
                buttonBar,
                statusLabel
        );
    }

    public void load(RetirementPlan plan) {

        currentPlan = Objects.requireNonNull(plan);
        loading = true;
        Household household = plan.getHousehold();

        primaryPersonCard.load(household.getPrimaryPerson());
        spousePersonCard.load(household.getSpouse());
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
            spouse = spousePersonCard.readValidated();
        }
        catch (IllegalArgumentException exception) {
            statusLabel.setText(exception.getMessage());
            return false;
        }

        if (!isDirty()) {
            return true;
        }

        // Validate both cards before touching either existing Person (and its accounts/income).
        Household household = currentPlan.getHousehold();
        primary.applyTo(household.getPrimaryPerson());
        spouse.applyTo(household.getSpouse());
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
                    || !spousePersonCard.matches(currentPlan.getHousehold().getSpouse())));
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
