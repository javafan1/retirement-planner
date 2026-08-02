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

        stage.setScene(
                new Scene(
                        pane,
                        700,
                        600));
    }

    public void show() {
        stage.showAndWait();
    }
}

/*
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

        ProjectionYearDetailsPane pane =
                new ProjectionYearDetailsPane(
                        projectionYear);

        ScrollPane scrollPane =
                new ScrollPane(pane);

        //scrollPane.setFitToWidth(true);
        //scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);

        stage.setScene(
                new Scene(
                        scrollPane,
                        700,
                        700));
    }


    public void show() {
        stage.showAndWait();
    }
}

 */