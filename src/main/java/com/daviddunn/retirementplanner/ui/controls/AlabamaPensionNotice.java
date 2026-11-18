package com.daviddunn.retirementplanner.ui.controls;

import com.daviddunn.retirementplanner.domain.income.Pension;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** Session-only notice with a link to each affected record in the existing Income editor. */
public final class AlabamaPensionNotice extends VBox {
    private final SimpleBooleanProperty open = new SimpleBooleanProperty();
    private final SimpleBooleanProperty inWorkspace = new SimpleBooleanProperty(true);
    private final Label affected = new Label();
    private final MenuButton pensionChoices = new MenuButton("Choose a Pension");
    private List<Pension> pensions = List.of();
    private Consumer<Pension> review = pension -> { };

    public AlabamaPensionNotice(String id) {
        setId(id);
        setSpacing(6);
        setMinHeight(Region.USE_PREF_SIZE);
        getStyleClass().add("alabama-pension-notice");
        visibleProperty().bind(open.and(inWorkspace));
        managedProperty().bind(visibleProperty());

        Label title = new Label("Pension Classification Needed for Alabama");
        title.getStyleClass().add("alabama-pension-notice-title");
        Label message = new Label("Alabama may exempt qualifying defined-benefit pensions. "
                + "Defined-contribution pensions can receive different tax treatment. "
                + "Review the original pension plan documents to verify the classification.");
        for (Label label : List.of(title, message, affected)) {
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }

        Button reviewButton = new Button("Review Pensions");
        reviewButton.getStyleClass().add("review-alabama-pensions");
        reviewButton.setOnAction(event -> {
            if (!pensions.isEmpty()) review.accept(pensions.getFirst());
        });
        pensionChoices.visibleProperty().bind(pensionChoices.managedProperty());
        Button close = new Button("Close");
        close.getStyleClass().add("dismiss-alabama-pensions");
        close.setOnAction(event -> open.set(false));
        getChildren().addAll(title, message, affected, new FlowPane(8, 6, reviewButton, pensionChoices, close));
    }

    public void setOnReview(Consumer<Pension> action) {
        review = action;
    }

    public void setWorkspaceVisible(boolean visible) {
        inWorkspace.set(visible);
    }

    public void update(List<Pension> candidates) {
        if (pensions.equals(candidates)) return;
        pensions = candidates;
        affected.setText(switch (pensions.size()) {
            case 0 -> "";
            case 1 -> "Unknown classification for " + description(pensions.getFirst());
            default -> pensions.size() + " pensions have an unknown classification.";
        });
        pensionChoices.getItems().clear();
        for (int i = 0; i < pensions.size(); i++) {
            Pension pension = pensions.get(i);
            MenuItem item = new MenuItem((i + 1) + ". " + description(pension));
            item.setOnAction(event -> review.accept(pension));
            pensionChoices.getItems().add(item);
        }
        pensionChoices.setManaged(pensions.size() > 1);
        open.set(!pensions.isEmpty());
    }

    private static String description(Pension pension) {
        return pension.getName() + " (" + pension.getOwnership() + ")";
    }
}
