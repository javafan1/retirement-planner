package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.*;
import javafx.stage.*;

public final class MonteCarloStrategyComparisonDialog extends Dialog<Void> {
    public MonteCarloStrategyComparisonDialog(Window owner, ApplicationController controller) {
        this(owner, new MonteCarloStrategyComparisonView(controller));
    }

    MonteCarloStrategyComparisonDialog(Window owner, MonteCarloStrategyComparisonView view) {
        setTitle("Monte Carlo Strategy Comparison"); initOwner(owner); initModality(Modality.WINDOW_MODAL); setResizable(true);
        var scroll = new ScrollPane(view); scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        getDialogPane().setContent(scroll); getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var screen = Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(Math.min(1900, screen.getWidth() - 64), Math.min(1040, screen.getHeight() - 70));
        setOnHidden(event -> view.close());
    }
}
