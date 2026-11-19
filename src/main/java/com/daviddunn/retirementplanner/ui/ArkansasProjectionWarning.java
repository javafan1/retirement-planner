package com.daviddunn.retirementplanner.ui;

import com.daviddunn.retirementplanner.domain.projection.ArkansasProjectionUnavailableException;
import com.daviddunn.retirementplanner.domain.tax.state.ArkansasPensionPreflight.Review;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.List;

/** The usual State Tax Unavailable warning with an optional existing Income-workspace action. */
final class ArkansasProjectionWarning {
    private final Alert alert = new Alert(Alert.AlertType.WARNING);
    private final ButtonType review = new ButtonType("Review Pensions", ButtonBar.ButtonData.OK_DONE);
    private final ComboBox<Review> choices = new ComboBox<>();

    ArkansasProjectionWarning(Window owner, ArkansasProjectionUnavailableException failure, List<Review> available) {
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("State Tax Unavailable");
        alert.setHeaderText("Arkansas state income tax could not be estimated.");
        var message = new Label(ProjectionFailureMessages.stateTaxUnavailableMessage(failure).orElseThrow());
        message.setWrapText(true);
        var contents = new VBox(10, message);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        if (!available.isEmpty()) {
            choices.setId("arkansas-pension-review");
            choices.getItems().setAll(available);
            choices.setMaxWidth(Double.MAX_VALUE);
            choices.setAccessibleText("Pension to review");
            choices.setConverter(new StringConverter<>() {
                @Override public String toString(Review value) {
                    if (value == null) return "";
                    return value.name() + " (" + value.originalOwner()
                            + (value.originalOwner() == value.recipient() ? "" : ", paid to " + value.recipient()) + ")";
                }
                @Override public Review fromString(String value) { throw new UnsupportedOperationException(); }
            });
            choices.getSelectionModel().selectFirst();
            contents.getChildren().add(choices);
            alert.getButtonTypes().addFirst(review);
        }
        alert.getDialogPane().setContent(contents);
        alert.getDialogPane().setPrefWidth(460);
    }

    Alert dialog() { return alert; }

    java.util.Optional<Review> selectedReview(ButtonType button) {
        return button == review ? java.util.Optional.ofNullable(choices.getValue()) : java.util.Optional.empty();
    }
}
