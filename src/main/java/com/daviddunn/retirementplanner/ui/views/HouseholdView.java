package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.ui.components.PersonCard;
import javafx.geometry.Insets;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public class HouseholdView extends VBox {

    public HouseholdView() {

        setSpacing(15);
        setPadding(new Insets(15));

        getChildren().addAll(
                createPrimaryPersonPane(),
                createSpousePane()
        );
    }

    private TitledPane createPrimaryPersonPane() {

        return new TitledPane(
                "Primary Person",
                new PersonCard()
        );
    }

    private TitledPane createSpousePane() {

        return new TitledPane(
                "Spouse",
                new PersonCard()
        );
    }
}