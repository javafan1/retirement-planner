package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.components.PersonCard;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public class HouseholdView extends VBox {

    private final PersonCard primaryPersonCard;
    private final PersonCard spousePersonCard;

    public HouseholdView() {

        primaryPersonCard = new PersonCard();
        spousePersonCard = new PersonCard();

        setSpacing(15);
        setPadding(new Insets(15));

        getChildren().addAll(
                createPrimaryPersonPane(),
                createSpousePane()
        );
    }

    public void load(RetirementPlan plan) {

        Household household = plan.getHousehold();

        primaryPersonCard.load(household.getPrimaryPerson());
        spousePersonCard.load(household.getSpouse());
    }

    public void save(RetirementPlan plan) {

        Household household = plan.getHousehold();

        primaryPersonCard.save(household.getPrimaryPerson());
        spousePersonCard.save(household.getSpouse());
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