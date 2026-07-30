package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import com.daviddunn.retirementplanner.ui.summary.ProjectionYearSummaryPane;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProjectionYearSummaryDialog {

    private final Stage stage;

    public ProjectionYearSummaryDialog(
            ProjectionYear projectionYear) {

        stage = new Stage();

        stage.initModality(Modality.APPLICATION_MODAL);

        //stage.setTitle("Projection Year Summary");
        stage.setTitle(
                "Projection Year "
                        + projectionYear.getCalendarYear()
                        + " (Age "
                        + projectionYear.getPrimaryPersonAge()
                        + ")");

        ProjectionYearSummaryPane pane =
                new ProjectionYearSummaryPane(
                        projectionYear);

        stage.setScene(
                new Scene(pane, 700, 500));
    }


    public void show() {
        stage.showAndWait();
    }
}