package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenYearResult;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenContext;
import javafx.geometry.Bounds;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import java.util.List;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;

/** Positions a presentation-only callout at the supplied sustained result. */
final class BreakEvenChart extends LineChart<Number, Number> {
    private final Label annotation = new Label();
    private final Line connector = new Line();
    private BreakEvenYearResult point;
    private BreakEvenContext context = BreakEvenContext.unavailable();
    private final com.daviddunn.retirementplanner.ui.charts.TimelineClaimMarkers claims = new com.daviddunn.retirementplanner.ui.charts.TimelineClaimMarkers(getPlotChildren());
    private final Pane probabilities = new Pane();
    private final Label probabilityHeading = new Label("Probability at least one spouse alive ⓘ");
    private final VBox survivalRow = new VBox(2, probabilities, probabilityHeading);

    BreakEvenChart(NumberAxis xAxis, NumberAxis yAxis) {
        super(xAxis, yAxis);
        getStylesheets().add(getClass().getResource("/css/chart-timeline.css").toExternalForm());
        annotation.setId("break-even-annotation");
        annotation.getStyleClass().add("break-even-annotation");
        annotation.setWrapText(true);
        annotation.setMinHeight(Region.USE_PREF_SIZE);
        annotation.setManaged(false);
        annotation.setMouseTransparent(true);
        connector.getStyleClass().add("break-even-connector");
        connector.setManaged(false);
        connector.setMouseTransparent(true);
        getPlotChildren().addAll(connector, annotation);
        probabilities.setId("break-even-probability-values");
        probabilities.setPrefHeight(20);
        probabilityHeading.setId("break-even-probability-heading");
        probabilityHeading.getStyleClass().add("break-even-survival-heading");
        probabilityHeading.setWrapText(true);
        probabilityHeading.setMinHeight(Region.USE_PREF_SIZE);
        survivalRow.setMinWidth(0);
        probabilities.widthProperty().addListener((observable, oldWidth, width) -> requestChartLayout());
        setBreakEven(null, "");
    }

    VBox survivalRow() { return survivalRow; }

    void setContext(BreakEvenContext context) {
        this.context = context;
        claims.setEvents(context.events().stream().map(event ->
                new com.daviddunn.retirementplanner.ui.charts.TimelineClaimMarkers.Event(event.year(),
                        BreakEvenPresentation.eventLabel(event), BreakEvenPresentation.eventLabel(event) + "\n"
                        + event.elections().stream().map(e -> e.plan() + " exact claim date: " + e.claimDate())
                        .collect(java.util.stream.Collectors.joining("\n")))).toList());
        probabilityHeading.setText(context.survival().isEmpty() ? context.survivalExplanation()
                : "Probability at least one spouse alive ⓘ");
        probabilityHeading.setTooltip(new Tooltip(context.survivalExplanation()));
        probabilityHeading.setAccessibleHelp(context.survivalExplanation());
        probabilities.setVisible(!context.survival().isEmpty());
        probabilities.setManaged(!context.survival().isEmpty());
        requestChartLayout();
    }

    void setBreakEven(BreakEvenYearResult point, String text) {
        this.point = point;
        annotation.setText(text);
        annotation.setAccessibleText(text);
        annotation.setVisible(point != null);
        connector.setVisible(point != null);
        requestChartLayout();
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        layoutProbabilities();
        List<Bounds> occupied = claims.layout(getXAxis(), getYAxis().getHeight());
        if (point == null) return;
        double plotWidth = getXAxis().getWidth();
        double plotHeight = getYAxis().getHeight();
        double x = getXAxis().getDisplayPosition(point.year());
        double y = getYAxis().getDisplayPosition(point.difference());
        double width = Math.min(220, Math.max(0, plotWidth - 12));
        double height = annotation.prefHeight(width);
        double left = x + width + 16 <= plotWidth ? x + 12 : x - width - 12;
        double top = y - height - 14 >= 6 ? y - height - 14 : y + 14;
        left = Math.max(6, Math.min(left, plotWidth - width - 6));
        top = Math.max(6, Math.min(top, plotHeight - height - 6));
        for (Bounds event : occupied) {
            if (event.intersects(left, top, width, height)) top = event.getMaxY() + 6;
        }
        top = Math.max(6, Math.min(top, plotHeight - height - 6));
        annotation.resizeRelocate(left, top, width, height);
        connector.setStartX(x);
        connector.setStartY(y);
        connector.setEndX(Math.max(left, Math.min(x, left + width)));
        connector.setEndY(Math.max(top, Math.min(y, top + height)));
        connector.toFront();
        annotation.toFront();
    }

    private void layoutProbabilities() {
        NumberAxis axis = (NumberAxis) getXAxis();
        // Major ticks and probability labels use the same axis positions at every width.
        double unit = Math.max(1, Math.ceil((axis.getUpperBound() - axis.getLowerBound())
                / Math.max(1, axis.getWidth() / 70)));
        if (axis.getTickUnit() != unit) axis.setTickUnit(unit);
        probabilities.getChildren().clear();
        if (context.survival().isEmpty()) return;
        for (var tick : axis.getTickMarks()) {
            int year = tick.getValue().intValue();
            if (tick.getValue().doubleValue() != year) continue;
            var probability = context.survival().get(year);
            Label label = new Label(probability == null ? "—" : BreakEvenPresentation.probability(probability));
            label.getStyleClass().add("break-even-probability");
            label.setId("break-even-probability-" + year);
            label.setAccessibleText(year + ": " + (probability == null ? "Unavailable" : label.getText()) + " at least one alive");
            Tooltip.install(label, new Tooltip(label.getAccessibleText() + "\n" + context.survivalExplanation()));
            probabilities.getChildren().add(label);
            label.applyCss();
            double width = label.prefWidth(-1);
            double x = probabilities.sceneToLocal(axis.localToScene(axis.getDisplayPosition(year), 0)).getX();
            label.resizeRelocate(x - width / 2, 0, width, label.prefHeight(width));
        }
    }
}
