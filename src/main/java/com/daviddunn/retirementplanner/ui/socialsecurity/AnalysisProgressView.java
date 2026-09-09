package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Consistent accessible presentation for background analysis progress. */
final class AnalysisProgressView extends HBox {

    private final ProgressIndicator activity = new ProgressIndicator();
    private final ProgressBar bar = new ProgressBar();
    private final Label headline = new Label();
    private final Label detail = new Label();

    AnalysisProgressView() {
        super(8);
        activity.setPrefSize(24, 24);
        bar.setPrefWidth(180);
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(activity, bar, new VBox(2, headline, detail));
        setVisible(false);
        setManaged(false);
    }

    void show(SocialSecurityAnalysisProgressModel model) {
        unbind();
        var update = model.update();
        activity.setProgress(-1);
        bar.setProgress(update == null ? -1 : update.fractionComplete());
        headline.setText(model.text());
        detail.setText(update == null ? "" : update.completedWork() + " of " + update.totalWork()
                + " (" + update.wholePercent() + "% of phase)");
        setManaged(true);
        setVisible(true);
    }
    void hide() {
        unbind();
        setVisible(false);
        setManaged(false);
    }

    private void unbind() {
        activity.progressProperty().unbind();
        bar.progressProperty().unbind();
        headline.textProperty().unbind();
        detail.textProperty().unbind();
    }
}
