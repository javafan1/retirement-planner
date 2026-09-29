package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Cached annual differences only. Floating point is used exclusively for display geometry. */
public final class MonteCarloDifferenceChart extends Region {
    private final NumberAxis x = new NumberAxis();
    private final NumberAxis y = new NumberAxis();
    private final Pane plot = new Pane();
    private final Path outer = path("mc-outer-band");
    private final Path inner = path("mc-inner-band");
    private final List<Path> lines = new ArrayList<>();
    private final Line zero = new Line();
    private final Line guide = new Line();
    private final List<Rectangle> hits = new ArrayList<>();
    private final ReadOnlyStringWrapper selected = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper warning = new ReadOnlyStringWrapper("");
    private List<MonteCarloPairedAnnualResult> years = List.of();
    private int index = -1;
    private static final List<Function<MonteCarloPercentiles, BigDecimal>> VALUES = List.of(
            MonteCarloPercentiles::p10, MonteCarloPercentiles::p25, MonteCarloPercentiles::p50,
            MonteCarloPercentiles::p75, MonteCarloPercentiles::p90);

    public MonteCarloDifferenceChart() {
        setId("mc-comparison-chart");
        getStyleClass().add("mc-difference-chart");
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.NODE);
        setAccessibleRoleDescription("Paired investable assets difference fan chart");
        setAccessibleHelp("Left/Right selects a year; Home/End selects the first/last year. Above zero means Current Plan has more; below zero means Saved Baseline has more.");
        setMinWidth(0);
        setMinHeight(205);
        setPrefHeight(205);
        setMaxHeight(205);
        x.setAnimated(false);
        y.setAnimated(false);
        x.setSide(Side.BOTTOM);
        y.setSide(Side.LEFT);
        x.setAutoRanging(false);
        y.setAutoRanging(false);
        x.setMinorTickVisible(false);
        y.setMinorTickVisible(false);
        x.setLabel("Calendar Year");
        y.setLabel("Current − Baseline ($)");
        x.setTickLabelFormatter(new StringConverter<>() {
            public String toString(Number v) { return v.doubleValue() == Math.rint(v.doubleValue()) ? Integer.toString(v.intValue()) : ""; }
            public Number fromString(String v) { return Integer.valueOf(v); }
        });
        y.setTickLabelFormatter(new StringConverter<>() {
            public String toString(Number v) { return MonteCarloStrategyComparisonPresentation.money(BigDecimal.valueOf(v.doubleValue())); }
            public Number fromString(String v) { throw new UnsupportedOperationException(); }
        });
        zero.setId("mc-comparison-zero");
        zero.getStyleClass().add("mc-comparison-zero");
        guide.setId("mc-comparison-guide");
        guide.getStyleClass().add("mc-active-year");
        for (var shape : List.of(zero, guide)) {
            shape.setManaged(false);
            shape.setMouseTransparent(true);
        }
        plot.getChildren().addAll(outer, inner, zero);
        for (int i = 0; i < 5; i++) {
            var line = path(i == 2 ? "mc-difference-median" : "mc-difference-percentile");
            lines.add(line);
            plot.getChildren().add(line);
        }
        plot.getChildren().add(guide);
        getChildren().addAll(x, y, plot);
        setOnKeyPressed(event -> {
            if (years.isEmpty()) return;
            int next = switch (event.getCode()) {
                case LEFT -> index - 1;
                case RIGHT -> index + 1;
                case HOME -> 0;
                case END -> years.size() - 1;
                default -> Integer.MIN_VALUE;
            };
            if (next != Integer.MIN_VALUE) { select(next); event.consume(); }
        });
    }

    private static Path path(String style) {
        var path = new Path();
        path.setManaged(false);
        path.setMouseTransparent(true);
        path.getStyleClass().add(style);
        return path;
    }

    public ReadOnlyStringProperty selectedYearProperty() { return selected.getReadOnlyProperty(); }
    public ReadOnlyStringProperty warningProperty() { return warning.getReadOnlyProperty(); }
    public List<MonteCarloPairedAnnualResult> years() { return years; }
    public NumberAxis valueAxis() { return y; }
    public int selectedIndex() { return index; }

    public void load(Map<Integer, MonteCarloPairedAnnualResult> annual) {
        years = List.copyOf(new TreeMap<>(annual).values());
        plot.getChildren().removeAll(hits);
        hits.clear();
        double magnitude = 1;
        for (var year : years) {
            var p = year.investableAssetsDifference().differencePercentiles();
            if (p.isPresent()) {
                magnitude = Math.max(magnitude, Math.max(p.orElseThrow().p10().abs().doubleValue(), p.orElseThrow().p90().abs().doubleValue()));
            }
            int position = hits.size();
            var hit = new Rectangle();
            hit.setId("mc-comparison-hit-" + year.year());
            hit.setManaged(false);
            hit.setFill(Color.TRANSPARENT);
            hit.setOnMouseEntered(event -> select(position));
            hit.setOnMouseClicked(event -> { select(position); requestFocus(); });
            Tooltip.install(hit, new Tooltip(MonteCarloStrategyComparisonPresentation.selectedYear(year)));
            hits.add(hit);
        }
        plot.getChildren().addAll(hits);
        // Symmetric around zero, including identical-strategy runs. No absolute-balance lower-bound assumption.
        y.setLowerBound(-magnitude * 1.12);
        y.setUpperBound(magnitude * 1.12);
        y.setTickUnit(magnitude * 1.12 / 2);
        x.setLowerBound(years.isEmpty() ? 0 : years.getFirst().year() - 0.5);
        x.setUpperBound(years.isEmpty() ? 1 : years.getLast().year() + 0.5);
        x.setTickUnit(Math.max(1, Math.ceil(years.size() / 14.0)));
        index = -1;
        selected.set("No annual financial rows: all sampled lifetimes end at opening.");
        warning.set("");
        setAccessibleText(selected.get());
        if (!years.isEmpty()) select(0);
        requestLayout();
    }

    public void select(int requestedIndex) {
        if (years.isEmpty()) return;
        index = Math.max(0, Math.min(requestedIndex, years.size() - 1));
        var annual = years.get(index);
        selected.set(MonteCarloStrategyComparisonPresentation.selectedYear(annual));
        warning.set(MonteCarloStrategyComparisonPresentation.warning(annual));
        setAccessibleText("Investable Assets Difference Over Time. " + selected.get() + " " + warning.get());
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        double left = 150, top = 8, width = Math.max(1, getWidth() - left - 18), height = Math.max(1, getHeight() - 58);
        y.resizeRelocate(0, top, left, height);
        x.resizeRelocate(left, top + height, width, 48);
        plot.resizeRelocate(left, top, width, height);
        y.layout();
        x.layout();
        zero.setStartX(0); zero.setEndX(width);
        zero.setStartY(y.getDisplayPosition(0)); zero.setEndY(y.getDisplayPosition(0));
        outer.getElements().clear(); inner.getElements().clear();
        lines.forEach(line -> line.getElements().clear());
        var segment = new ArrayList<MonteCarloPairedAnnualResult>();
        for (var year : years) {
            if (year.investableAssetsDifference().differencePercentiles().isEmpty()) {
                draw(segment); segment.clear();
            } else segment.add(year);
        }
        draw(segment);
        zero.toFront();
        for (int i = 0; i < hits.size(); i++) {
            var hit = hits.get(i);
            double start = x.getDisplayPosition(years.get(i).year() - 0.5);
            hit.setX(start); hit.setY(0);
            hit.setWidth(Math.max(0, x.getDisplayPosition(years.get(i).year() + 0.5) - start));
            hit.setHeight(height);
        }
        guide.setVisible(index >= 0);
        if (index >= 0) {
            double at = x.getDisplayPosition(years.get(index).year());
            guide.setStartX(at); guide.setEndX(at); guide.setStartY(0); guide.setEndY(height);
            guide.toFront();
        }
    }

    private double value(MonteCarloPairedAnnualResult year, int quantile) {
        return y.getDisplayPosition(VALUES.get(quantile).apply(year.investableAssetsDifference().differencePercentiles().orElseThrow()));
    }

    private void draw(List<MonteCarloPairedAnnualResult> segment) {
        if (segment.isEmpty()) return;
        polygon(outer, segment, 0, 4);
        polygon(inner, segment, 1, 3);
        for (int q = 0; q < 5; q++) {
            var elements = lines.get(q).getElements();
            for (int i = 0; i < segment.size(); i++) {
                var year = segment.get(i);
                double at = x.getDisplayPosition(year.year());
                elements.add(i == 0 ? new MoveTo(at, value(year, q)) : new LineTo(at, value(year, q)));
                if (segment.size() == 1) elements.add(new LineTo(at + 3, value(year, q)));
            }
        }
    }

    private void polygon(Path path, List<MonteCarloPairedAnnualResult> segment, int low, int high) {
        for (int i = 0; i < segment.size(); i++) {
            var year = segment.get(i);
            double at = x.getDisplayPosition(year.year());
            path.getElements().add(i == 0 ? new MoveTo(at, value(year, high)) : new LineTo(at, value(year, high)));
        }
        for (int i = segment.size() - 1; i >= 0; i--) {
            var year = segment.get(i);
            path.getElements().add(new LineTo(x.getDisplayPosition(year.year()), value(year, low)));
        }
        path.getElements().add(new ClosePath());
    }
}
