package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles;
import com.daviddunn.retirementplanner.ui.charts.ProjectionPlotDecorations;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.AccessibleRole;
import javafx.scene.shape.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fan polygons use prepared percentiles; doubles are used only for pixel coordinates.
 */
public final class MonteCarloFanChart extends LineChart<Number, Number> {
    private final ProjectionPlotDecorations decorations = new ProjectionPlotDecorations(getPlotChildren());
    private final Path outer = band("mc-outer-band");
    private final Path inner = band("mc-inner-band");
    private final Circle singleMedian = new Circle(3.5, javafx.scene.paint.Color.web("#235dc4"));
    private final Circle singleReference = new Circle(3.5, javafx.scene.paint.Color.web("#a96506"));
    private final List<Rectangle> hitAreas = new ArrayList<>();
    private MonteCarloFanModel model;
    private int activeIndex = -1;
    private final ReadOnlyStringWrapper selectedDetail = new ReadOnlyStringWrapper("");
    private final Line activeYear = new Line();

    public MonteCarloFanChart() {
        super(new NumberAxis(), new NumberAxis());
        setId("monte-carlo-fan");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.NODE);
        setAccessibleRoleDescription("Investable assets fan chart");
        setAccessibleHelp("Use Left and Right Arrow to inspect years, Home for the first year, and End for the last year.");
        activeYear.setManaged(false);
        activeYear.setMouseTransparent(true);
        activeYear.getStyleClass().add("mc-active-year");
        activeYear.setVisible(false);
        focusedProperty().addListener((observable, oldValue, focused) -> requestChartLayout());
        setOnKeyPressed(event -> {
            if (model == null || model.years().isEmpty()) {
                return;
            }
            int next = switch (event.getCode()) {
                case LEFT -> activeIndex - 1;
                case RIGHT -> activeIndex + 1;
                case HOME -> 0;
                case END -> model.years().size() - 1;
                default -> -2;
            };
            if (next != -2) {
                selectYear(next);
                event.consume();
            }
        });
        getStyleClass().add("mc-fan-chart");
        setAnimated(false);
        setCreateSymbols(false);
        setLegendVisible(false);
        setMinHeight(330);
        setPrefHeight(370);
        setMaxHeight(410);
        setHorizontalZeroLineVisible(false);
        setVerticalZeroLineVisible(false);
        var x = (NumberAxis) getXAxis();
        x.setForceZeroInRange(false);
        x.setMinorTickVisible(false);
        x.setLabel("Calendar Year");
        x.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number number) {
                return number.doubleValue() == Math.rint(number.doubleValue()) ? Integer.toString(number.intValue()) : "";
            }

            @Override
            public Number fromString(String value) {
                return Integer.valueOf(value);
            }
        });
        var y = (NumberAxis) getYAxis();
        y.setForceZeroInRange(true);
        y.setLabel("Investable Assets · future dollars");
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number number) {
                return UIFormatters.money(new BigDecimal(number.toString()));
            }

            @Override
            public Number fromString(String value) {
                throw new UnsupportedOperationException();
            }
        });
        for (var dot : List.of(singleMedian, singleReference)) {
            dot.setManaged(false);
            dot.setMouseTransparent(true);
            dot.setVisible(false);
        }
        getPlotChildren().addAll(outer, inner, singleMedian, singleReference, activeYear);
    }

    public ReadOnlyStringProperty selectedDetailProperty() {
        return selectedDetail.getReadOnlyProperty();
    }

    private void selectYear(int index) {
        activeIndex = Math.max(0, Math.min(index, model.years().size() - 1));
        String detail = MonteCarloPresentation.tooltip(model, model.years().get(activeIndex));
        selectedDetail.set(detail);
        setAccessibleText("Investable Assets. " + detail);
        // Selection changes plot geometry without changing the outer chart's size.
        requestChartLayout();
    }

    private static Path band(String style) {
        var path = new Path();
        path.setManaged(false);
        path.setMouseTransparent(true);
        path.getStyleClass().add(style);
        return path;
    }

    public void load(MonteCarloFanModel model) {
        this.model = model;
        getData().clear();
        getPlotChildren().removeAll(hitAreas);
        hitAreas.clear();
        addSeries("P10", MonteCarloPercentiles::p10);
        addSeries("P90", MonteCarloPercentiles::p90);
        addSeries("Median", MonteCarloPercentiles::p50);
        var deterministic = new XYChart.Series<Number, Number>();
        deterministic.setName("Deterministic Projection");
        for (var year : model.years()) {
            year.deterministic().ifPresent(value -> deterministic.getData().add(new XYChart.Data<>(year.calendarYear(), value)));
            var hit = new Rectangle();
            hit.setManaged(false);
            hit.setFill(javafx.scene.paint.Color.TRANSPARENT);
            String detail = MonteCarloPresentation.tooltip(model, year);
            var tooltip = new Tooltip(detail);
            tooltip.setShowDelay(javafx.util.Duration.millis(100));
            Tooltip.install(hit, tooltip);
            hit.setAccessibleText(detail);
            final int index = hitAreas.size();
            hit.setOnMouseEntered(event -> selectYear(index));
            hit.setOnMouseClicked(event -> {
                selectYear(index);
                requestFocus();
            });
            hitAreas.add(hit);
        }
        getData().add(deterministic);
        getPlotChildren().addAll(hitAreas);
        if (!model.years().isEmpty()) {
            var x = (NumberAxis) getXAxis();
            x.setAutoRanging(false);
            x.setLowerBound(model.years().getFirst().calendarYear() - 0.5);
            x.setUpperBound(model.years().getLast().calendarYear() + 0.5);
            x.setTickUnit(Math.max(1, Math.ceil(model.years().size() / 14.0)));
        }
        decorations.load(model.context(), false, point -> {
        });
        setAccessibleText("Investable Assets. P10–P90 outer range, P25–P75 inner range, median and deterministic projection. "
                + "Each year includes simulations completing that year; hover for sample counts.");
        activeIndex = -1;
        selectedDetail.set("");
        if (!model.years().isEmpty()) {
            selectYear(0);
        }
        requestLayout();
    }

    private void addSeries(String name, Function<MonteCarloPercentiles, BigDecimal> value) {
        var series = new XYChart.Series<Number, Number>();
        series.setName(name);
        for (var year : model.years()) {
            year.percentiles().ifPresent(percentiles -> series.getData().add(
                    new XYChart.Data<>(year.calendarYear(), value.apply(percentiles))));
        }
        getData().add(series);
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        if (model == null) {
            return;
        }
        drawBand(outer, MonteCarloPercentiles::p10, MonteCarloPercentiles::p90);
        drawBand(inner, MonteCarloPercentiles::p25, MonteCarloPercentiles::p75);
        inner.toBack();
        outer.toBack();
        singlePoint(singleMedian, model.years().stream().filter(year -> year.percentiles().isPresent()).toList(),
                year -> year.percentiles().orElseThrow().p50());
        singlePoint(singleReference, model.years().stream().filter(year -> year.deterministic().isPresent()).toList(),
                year -> year.deterministic().orElseThrow());
        for (int index = 0; index < hitAreas.size(); index++) {
            var year = model.years().get(index);
            var hit = hitAreas.get(index);
            double left = getXAxis().getDisplayPosition(year.calendarYear() - 0.5);
            double right = getXAxis().getDisplayPosition(year.calendarYear() + 0.5);
            hit.setX(left);
            hit.setY(0);
            hit.setWidth(Math.max(0, right - left));
            hit.setHeight(getYAxis().getHeight());
            hit.toFront();
        }
        decorations.layout(getXAxis(), getYAxis());
        activeYear.setVisible(isFocused() && activeIndex >= 0);
        if (activeIndex >= 0) {
            double x = getXAxis().getDisplayPosition(model.years().get(activeIndex).calendarYear());
            activeYear.setStartX(x);
            activeYear.setEndX(x);
            activeYear.setStartY(0);
            activeYear.setEndY(getYAxis().getHeight());
            activeYear.toFront();
        }
    }

    private void singlePoint(Circle dot, List<MonteCarloFanModel.Year> years,
                             Function<MonteCarloFanModel.Year, BigDecimal> value) {
        dot.setVisible(years.size() == 1);
        if (years.size() != 1) {
            return;
        }
        var year = years.getFirst();
        dot.setCenterX(getXAxis().getDisplayPosition(year.calendarYear()));
        dot.setCenterY(getYAxis().getDisplayPosition(value.apply(year)));
        dot.toFront();
    }

    private void drawBand(Path path, Function<MonteCarloPercentiles, BigDecimal> low,
                          Function<MonteCarloPercentiles, BigDecimal> high) {
        path.getElements().clear();
        List<MonteCarloFanModel.Year> segment = new ArrayList<>();
        for (var year : model.years()) {
            if (year.percentiles().isEmpty()) {
                polygon(path, segment, low, high);
                segment.clear();
            } else {
                segment.add(year);
            }
        }
        polygon(path, segment, low, high);
    }

    private void polygon(Path path, List<MonteCarloFanModel.Year> segment,
                         Function<MonteCarloPercentiles, BigDecimal> low,
                         Function<MonteCarloPercentiles, BigDecimal> high) {
        if (segment.isEmpty()) {
            return;
        }
        if (segment.size() == 1) {
            var year = segment.getFirst();
            double left = getXAxis().getDisplayPosition(year.calendarYear() - 0.2);
            double right = getXAxis().getDisplayPosition(year.calendarYear() + 0.2);
            double top = getYAxis().getDisplayPosition(high.apply(year.percentiles().orElseThrow()));
            double bottom = getYAxis().getDisplayPosition(low.apply(year.percentiles().orElseThrow()));
            path.getElements().addAll(new MoveTo(left, top), new LineTo(right, top),
                    new LineTo(right, bottom), new LineTo(left, bottom), new ClosePath());
            return;
        }
        for (int index = 0; index < segment.size(); index++) {
            var year = segment.get(index);
            double x = getXAxis().getDisplayPosition(year.calendarYear());
            double y = getYAxis().getDisplayPosition(high.apply(year.percentiles().orElseThrow()));
            path.getElements().add(index == 0 ? new MoveTo(x, y) : new LineTo(x, y));
        }
        for (int index = segment.size() - 1; index >= 0; index--) {
            var year = segment.get(index);
            path.getElements().add(new LineTo(getXAxis().getDisplayPosition(year.calendarYear()),
                    getYAxis().getDisplayPosition(low.apply(year.percentiles().orElseThrow()))));
        }
        path.getElements().add(new ClosePath());
    }
}
