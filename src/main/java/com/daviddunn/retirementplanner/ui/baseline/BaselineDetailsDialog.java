package com.daviddunn.retirementplanner.ui.baseline;

import com.daviddunn.retirementplanner.app.baseline.BaselineDetailsSession;
import javafx.scene.control.*;
import javafx.stage.Screen;

/** Read-only modal: all financial/cache/context preparation precedes construction. */
public final class BaselineDetailsDialog extends Dialog<Void> {
    public BaselineDetailsDialog(BaselineDetailsSession session) {
        this(session, null);
    }
    public BaselineDetailsDialog(BaselineDetailsSession session, com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        setTitle("Baseline Projection Details");
        setHeaderText("Baseline Projection Details");
        setResizable(true);
        var content = new javafx.scene.layout.VBox(10, new BaselineDetailsView(session));
        if (source != null) {
            var history = new Button("View Projection Analyzer History");
            history.setOnAction(e -> {
                var state = source.getCurrentPlan().getProjectionState();
                var dialog = new com.daviddunn.retirementplanner.ui.socialsecurity.ProjectionAnalyzerHistoryDialog(source,
                        state == null || state.baseline() == null ? null : state.baseline().id());
                dialog.initOwner(getDialogPane().getScene().getWindow()); dialog.showAndWait();
            });
            content.getChildren().addFirst(history);
        }
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll);
        var screen = Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(Math.min(1180, screen.getWidth() - 64), Math.min(900, screen.getHeight() - 80));
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }
}
