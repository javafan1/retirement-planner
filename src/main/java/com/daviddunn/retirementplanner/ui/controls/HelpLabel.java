package com.daviddunn.retirementplanner.ui.controls;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class HelpLabel extends HBox {

    public HelpLabel(
            String labelText,
            String helpText) {

        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(
                new Label(labelText),
                new HelpIcon(helpText));
    }
}