package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Separate Analysis-menu feature; no Social Security analyzer lifecycle or settings.
 */
public final class MonteCarloAnalysisDialog extends Dialog<Void> {
    public MonteCarloAnalysisDialog(Window owner, ApplicationController controller) {
        setTitle("Monte Carlo Retirement Analysis");
        initOwner(owner);
        initModality(javafx.stage.Modality.WINDOW_MODAL);
        setResizable(true);
        var view = new MonteCarloAnalysisView(controller);
        var scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var bounds = Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(Math.min(1760, bounds.getWidth() - 64),
                Math.min(980, bounds.getHeight() - 70));
        setOnHidden(event -> view.close());
    }
}
