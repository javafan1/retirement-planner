package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.List;

/** Renders a prepared single-projection snapshot; selection and hover never invoke a controller. */
public final class ProjectionChartView extends VBox {
    private ProjectionChartModel model = ProjectionChartModel.empty();
    private final ComboBox<ProjectionChartMetric> selector = new ComboBox<>();
    private final StackPane plot = new StackPane();
    private final FlowPane legend = new FlowPane(14, 3);
    private final Label selectedYear = new Label("Select a year point for details; hover for values.");
    private final Label notice = new Label();

    public ProjectionChartView() {
        super(5);
        setId("projection-chart-panel");
        getStyleClass().add("chart-card");
        setMinWidth(0);
        setPadding(new Insets(10));
        getStylesheets().add(getClass().getResource("/css/chart-timeline.css").toExternalForm());
        getStylesheets().add(getClass().getResource("/css/projection-chart.css").toExternalForm());
        Label title = new Label("Projection Chart");
        title.getStyleClass().add("chart-title");
        selector.setId("projection-chart-metric");
        selector.setAccessibleText("Projection Chart Metric");
        selectedYear.setId("projection-chart-selected-year");
        selector.getItems().setAll(ProjectionChartMetric.values());
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.valueProperty().addListener((observable, oldValue, value) -> render());
        plot.setMinWidth(0);
        plot.setMinHeight(300);
        plot.setPrefHeight(330);
        plot.setMaxHeight(330);
        for (var label : List.of(selectedYear, notice)) {
            label.setWrapText(true); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE);
        }
        legend.setMinWidth(0);
        getChildren().addAll(title, selector, legend, plot, notice, selectedYear);
        selector.setValue(ProjectionChartMetric.INVESTABLE_ASSETS);
    }

    public void load(ProjectionChartModel model) {
        this.model = java.util.Objects.requireNonNull(model);
        render();
    }

    private NumberAxis yearAxis() {
        NumberAxis axis = new NumberAxis();
        axis.setLabel("Calendar Year");
        axis.setForceZeroInRange(false);
        axis.setMinorTickVisible(false);
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) {
                return value.doubleValue() == Math.rint(value.doubleValue()) ? Integer.toString(value.intValue()) : "";
            }
            @Override public Number fromString(String value) { return Integer.valueOf(value); }
        });
        if (!model.years().isEmpty()) {
            axis.setAutoRanging(false);
            axis.setLowerBound(model.years().getFirst().year() - 0.5);
            axis.setUpperBound(model.years().getLast().year() + 0.5);
        }
        axis.widthProperty().addListener((observable, oldWidth, width) -> axis.setTickUnit(
                Math.max(1, Math.ceil((axis.getUpperBound() - axis.getLowerBound()) / Math.max(1, width.doubleValue() / 75)))));
        return axis;
    }

    private NumberAxis moneyAxis() {
        NumberAxis axis = new NumberAxis();
        axis.setLabel("Projected dollars (same values as table)");
        axis.setForceZeroInRange(true);
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return UIFormatters.money(new BigDecimal(value.toString())); }
            @Override public Number fromString(String value) { throw new UnsupportedOperationException(); }
        });
        return axis;
    }

    private void render() {
        var metric = selector.getValue();
        if (metric == null) return;
        selector.setDisable(model.years().isEmpty());
        legend.getChildren().clear();
        selectedYear.setText("Select a year point for details; hover for values.");
        selectedYear.setTooltip(null);
        if (model.years().isEmpty()) {
            plot.getChildren().setAll(new Label("No projection available."));
            notice.setText("");
            return;
        }
        boolean composition = metric == ProjectionChartMetric.INVESTABLE_ASSETS
                && model.years().stream().allMatch(ProjectionChartModel.Point::compositionComplete);
        XYChart<Number, Number> chart;
        if (composition) {
            var area = new AssetChart(yearAxis(), moneyAxis());
            area.setCreateSymbols(false);
            for (var type : ProjectionAssetType.values()) {
                var series = new XYChart.Series<Number, Number>();
                series.setName(ProjectionChartPresentation.category(type));
                for (var point : model.years()) series.getData().add(new XYChart.Data<>(point.year(), point.composition().get(type)));
                area.getData().add(series);
                addLegend(series.getName(), "projection-category-" + type.ordinal());
            }
            area.decorations.load(model, true, this::select);
            chart = area;
            addLegend("Total Investable Assets", "projection-total-legend");
        } else {
            var line = new ValueChart(yearAxis(), moneyAxis());
            var series = new XYChart.Series<Number, Number>();
            series.setName(metric.toString());
            for (var point : model.years()) {
                var data = new XYChart.Data<Number, Number>(point.year(), point.values().get(metric));
                StackPane dot = new StackPane();
                data.setNode(dot);
                String details = ProjectionChartPresentation.tooltip(point, metric);
                Tooltip.install(dot, new Tooltip(details));
                dot.accessibleTextProperty().unbind();
                dot.setAccessibleText(details);
                dot.setOnMouseClicked(event -> select(point));
                series.getData().add(data);
            }
            line.getData().add(series);
            line.decorations.load(model, false, this::select);
            chart = line;
        }
        chart.setId("projection-chart");
        chart.getStyleClass().add("projection-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setHorizontalZeroLineVisible(false);
        chart.setVerticalZeroLineVisible(false);
        chart.setTitle(metric.toString());
        chart.setMinWidth(0);
        chart.setAccessibleText("Single projection: " + metric + ". Social Security claim labels show exact configured elections. "
                + "Labeled Roth Conversion and RMD periods identify separate actual transactions, including overlapping years.");
        plot.getChildren().setAll(chart);
        notice.setText(metric == ProjectionChartMetric.INVESTABLE_ASSETS && !composition
                ? "Detailed asset composition is unavailable for this projection."
                : "");
        notice.setManaged(!notice.getText().isEmpty());
        notice.setVisible(!notice.getText().isEmpty());
    }

    private void select(ProjectionChartModel.Point point) {
        selectedYear.setText(point.year() + " · " + selector.getValue() + ": " + UIFormatters.money(point.values().get(selector.getValue())));
        selectedYear.setTooltip(new Tooltip(ProjectionChartPresentation.tooltip(point, selector.getValue())));
        selectedYear.setAccessibleText(ProjectionChartPresentation.tooltip(point, selector.getValue()));
    }
    private void addLegend(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.maxWidthProperty().bind(legend.widthProperty());
        legend.getChildren().add(label);
    }
    private static final class AssetChart extends StackedAreaChart<Number, Number> {
        final ProjectionPlotDecorations decorations = new ProjectionPlotDecorations(getPlotChildren());
        AssetChart(NumberAxis x, NumberAxis y) { super(x, y); setAnimated(false); }
        @Override protected void layoutPlotChildren() {
            super.layoutPlotChildren(); decorations.layout(getXAxis(), getYAxis());
        }
    }
    private static final class ValueChart extends LineChart<Number, Number> {
        final ProjectionPlotDecorations decorations = new ProjectionPlotDecorations(getPlotChildren());
        ValueChart(NumberAxis x, NumberAxis y) { super(x, y); setAnimated(false); }
        @Override protected void layoutPlotChildren() {
            super.layoutPlotChildren(); decorations.layout(getXAxis(), getYAxis());
        }
    }
}
