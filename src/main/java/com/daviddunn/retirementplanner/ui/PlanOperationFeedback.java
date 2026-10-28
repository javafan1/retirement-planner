package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/** FX-owned, delayed owner-modal feedback. Showing the stage never enters another event loop. */
final class PlanOperationFeedback implements AutoCloseable {
    private final VBox view = new VBox(12);
    private final Stage owner;
    private final PauseTransition delay = new PauseTransition(Duration.millis(300));
    private final Label label = new Label();
    private final Button cancel = new Button("Cancel");
    private final InvalidationListener position = ignored -> center();
    private final ChangeListener<Boolean> ownerShowing = (observable, old, showing) -> { if (!showing) close(); };
    private Stage dialog;
    private boolean closed;

    PlanOperationFeedback(Stage owner, String message) {
        this.owner = owner;
        var spinner = new ProgressIndicator();
        spinner.setPrefSize(38, 38);
        spinner.setMaxSize(38, 38);
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMinHeight(40);
        label.setPrefHeight(40);
        label.setMaxWidth(310);
        cancel.setId("plan-operation-cancel");
        cancel.setVisible(false); // Reserve its space to keep phase changes visually stable.
        view.getChildren().addAll(spinner, label, cancel);
        view.setAlignment(Pos.CENTER);
        view.getStyleClass().add("plan-operation-dialog");
        view.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        view.setVisible(false);
        phase(message, null);
        delay.setOnFinished(event -> showSubtle());
        delay.play();
    }

    VBox view() { return view; }
    Stage dialog() { return dialog; }

    void phase(String message, Runnable cancellation) {
        label.setText(message);
        view.setAccessibleText(message);
        cancel.setVisible(cancellation != null);
        cancel.setOnAction(cancellation == null ? null : event -> cancellation.run());
        if (dialog != null) dialog.setTitle(message);
    }

    void showSubtle() {
        if (closed || dialog != null || owner == null || !owner.isShowing()) return;
        dialog = new Stage(StageStyle.UNDECORATED);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(false);
        dialog.setTitle(label.getText());
        view.setVisible(true);
        var scene = new Scene(view, 360, 200);
        ApplicationThemeManager.prepare(scene);
        dialog.setScene(scene);
        dialog.setOnCloseRequest(event -> event.consume());
        dialog.setWidth(360);
        dialog.setHeight(200);
        owner.xProperty().addListener(position);
        owner.yProperty().addListener(position);
        owner.widthProperty().addListener(position);
        owner.heightProperty().addListener(position);
        owner.showingProperty().addListener(ownerShowing);
        center();
        dialog.show();
        center();
    }

    private void center() {
        if (dialog == null) return;
        dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
        dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
    }

    @Override public void close() {
        closed = true;
        delay.stop();
        cancel.setOnAction(null);
        view.setVisible(false);
        if (owner != null) {
            owner.xProperty().removeListener(position);
            owner.yProperty().removeListener(position);
            owner.widthProperty().removeListener(position);
            owner.heightProperty().removeListener(position);
            owner.showingProperty().removeListener(ownerShowing);
        }
        if (dialog != null) {
            dialog.hide();
            dialog.setScene(null);
        }
    }
}
