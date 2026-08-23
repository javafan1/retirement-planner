package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;

public class ProjectionYearDetailsDialog {

    private final Stage stage;

    public ProjectionYearDetailsDialog(
            ProjectionYear currentYear,
            ProjectionYear baselineYear,
            BigDecimal nonInvestableAssetValue,
            BigDecimal baselineNonInvestableAssetValue) {

        stage = new Stage();

        stage.initModality(
                Modality.APPLICATION_MODAL);

        stage.setTitle(
                "Projection Year "
                        + currentYear.getCalendarYear()
                        + " (Age "
                        + currentYear.getPrimaryPersonAge()
                        + ")");

        ProjectionYearDetailsPane pane =
                new ProjectionYearDetailsPane(
                        currentYear,
                        baselineYear,
                        nonInvestableAssetValue,
                        baselineNonInvestableAssetValue);

        Scene scene =
                new Scene(pane);

        stage.setScene(scene);

        stage.setWidth(1400);
        stage.setHeight(800);

        stage.setMaxWidth(1400);
        stage.setMaxHeight(850);
    }

    public void show() {
        stage.showAndWait();
    }
}