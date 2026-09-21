package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsPane;
import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsRequest;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.math.BigDecimal;
import java.util.List;

public class ProjectionYearDetailsDialog {

    private final Stage stage;
    private final ProjectionYearDetailsRequest request;
    private final List<ProjectionYear> baselineYears;
    private final List<NonInvestableAssetProjection> baselineAssets;
    private final ProjectionYearDetailsPane pane;
    private final Button previousButton = navigationButton("Previous Projection Year", FontAwesomeSolid.ARROW_LEFT);
    private final Button nextButton = navigationButton("Next Projection Year", FontAwesomeSolid.ARROW_RIGHT);
    private final Label yearLabel = new Label();
    private int currentIndex;

    public ProjectionYearDetailsDialog(
            ProjectionYearDetailsRequest request,
            List<ProjectionYear> baselineYears,
            List<NonInvestableAssetProjection> baselineAssets) {

        this.request = request == null ? new ProjectionYearDetailsRequest(null, null, null) : request;
        this.baselineYears = baselineYears == null ? List.of() : List.copyOf(baselineYears);
        this.baselineAssets = baselineAssets == null ? List.of() : List.copyOf(baselineAssets);
        currentIndex = this.request.initialYear() == null ? -1 : this.request.years().indexOf(this.request.initialYear());

        stage = new Stage();

        stage.initModality(
                Modality.APPLICATION_MODAL);

        pane = new ProjectionYearDetailsPane(null, null, BigDecimal.ZERO, null);
        yearLabel.setStyle("-fx-font-size:18px; -fx-font-weight:bold;");
        HBox navigation = new HBox(12, previousButton, yearLabel, nextButton);
        navigation.setAlignment(Pos.CENTER);
        navigation.setPadding(new Insets(12));
        BorderPane root = new BorderPane(pane);
        root.setTop(navigation);
        previousButton.setOnAction(event -> navigate(-1));
        nextButton.setOnAction(event -> navigate(1));
        updateYear();

        Scene scene =
                new Scene(root);
        // Only the header buttons/background participate. Leave tables, scroll panes
        // and editable controls their native arrow behavior, including focus traversal.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            var focus = scene.getFocusOwner();
            if (event.isConsumed() || event.isAltDown() || event.isControlDown()
                    || event.isMetaDown() || event.isShiftDown()
                    || (focus != null && focus != previousButton && focus != nextButton && focus != root)) {
                return;
            }
            if (event.getCode() == KeyCode.LEFT && !previousButton.isDisabled()) {
                navigate(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT && !nextButton.isDisabled()) {
                navigate(1);
                event.consume();
            }
        });

        stage.setScene(scene);

        stage.setWidth(1400);
        stage.setHeight(800);

        stage.setMaxWidth(1400);
        stage.setMaxHeight(850);
    }

    public ProjectionYear getProjectionYear() {
        return currentIndex < 0 ? null : request.years().get(currentIndex);
    }

    private void navigate(int direction) {
        int nextIndex = currentIndex + direction;
        if (currentIndex < 0 || nextIndex < 0 || nextIndex >= request.years().size()) {
            return;
        }
        currentIndex = nextIndex;
        updateYear();
    }

    private void updateYear() {
        previousButton.setDisable(currentIndex <= 0);
        nextButton.setDisable(currentIndex < 0 || currentIndex >= request.years().size() - 1);
        ProjectionYear current = getProjectionYear();
        if (current == null) {
            stage.setTitle("Projection Year Details");
            yearLabel.setText(request.years().isEmpty() ? "No projection years available."
                    : "Selected projection year is not available in this snapshot.");
            return;
        }
        int calendarYear = current.getCalendarYear();
        ProjectionYear baseline = baselineYears.stream()
                .filter(year -> year.getCalendarYear() == calendarYear).findFirst().orElse(null);
        pane.setProjectionYear(current, baseline,
                assetValue(request.nonInvestableAssets(), calendarYear, BigDecimal.ZERO),
                baseline == null ? null : assetValue(baselineAssets, calendarYear, null));
        yearLabel.setText("Projection Year " + calendarYear);
        stage.setTitle(yearLabel.getText() + " (Age " + current.getPrimaryPersonAge() + ")");
    }

    private static BigDecimal assetValue(List<NonInvestableAssetProjection> values, int year, BigDecimal missing) {
        return values.stream().filter(value -> value.getCalendarYear() == year)
                .map(NonInvestableAssetProjection::getTotalValue).findFirst().orElse(missing);
    }

    private static Button navigationButton(String description, FontAwesomeSolid icon) {
        Button button = new Button(null, new FontIcon(icon));
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    public void show() {
        stage.showAndWait();
    }
}
