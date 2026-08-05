package com.daviddunn.retirementplanner.ui.controls;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

public class HelpIcon extends Label {

    private static final double ICON_SIZE = 18;

    public HelpIcon(String helpText) {

        super("ⓘ");

        getStyleClass().add("help-icon");

        setAlignment(Pos.CENTER);

        setCursor(Cursor.HAND);

        setMinSize(ICON_SIZE, ICON_SIZE);
        setPrefSize(ICON_SIZE, ICON_SIZE);
        setMaxSize(ICON_SIZE, ICON_SIZE);

        Tooltip tooltip = new Tooltip(helpText);

        tooltip.setWrapText(true);
        tooltip.setMaxWidth(375);
        tooltip.setStyle(
                "-fx-font-size: 12px;");

        tooltip.setShowDelay(Duration.millis(250));
        tooltip.setShowDuration(Duration.minutes(5));

        setTooltip(tooltip);
    }
}