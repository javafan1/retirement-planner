package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ProjectionYearDetailsDialog {

    private final Stage stage;

    public ProjectionYearDetailsDialog(
            ProjectionYear projectionYear) {

        stage = new Stage();

        stage.initModality(
                Modality.APPLICATION_MODAL);

        stage.setTitle(
                "Projection Year "
                        + projectionYear.getCalendarYear()
                        + " (Age "
                        + projectionYear.getPrimaryPersonAge()
                        + ")");

        ProjectionYearDetailsPane pane =
                new ProjectionYearDetailsPane(
                        projectionYear);

        Scene scene =
                new Scene(pane);

        stage.setScene(scene);

        /*
         * Allow JavaFX to calculate the initial
         * window size from the two-column content.
         */
        stage.sizeToScene();

        /*
         * Prevent the dialog from becoming excessively
         * large on very large displays.
         */
        stage.setMaxWidth(1100);
        stage.setMaxHeight(850);
    }

    public void show() {
        stage.showAndWait();
    }
}