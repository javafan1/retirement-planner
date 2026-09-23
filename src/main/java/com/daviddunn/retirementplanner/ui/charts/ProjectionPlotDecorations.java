package com.daviddunn.retirementplanner.ui.charts;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.geometry.Bounds;
import javafx.geometry.BoundingBox;
import javafx.scene.shape.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Plot-only annotations: full-year tints plus separate top/bottom stripes preserve overlaps. */
public final class ProjectionPlotDecorations {
    private record Band(ProjectionChartModel.Period period, boolean roth, Rectangle fill, Rectangle stripe, Label label) { }
    private final ObservableList<Node> plot;
    private final TimelineClaimMarkers claims;
    private final List<Band> bands = new ArrayList<>();
    private final Path total = new Path();
    private final List<Circle> points = new ArrayList<>();
    private ProjectionChartModel model = ProjectionChartModel.empty();

    public ProjectionPlotDecorations(ObservableList<Node> plot) {
        this.plot = plot;
        claims = new TimelineClaimMarkers(plot);
        total.setManaged(false);
        total.setMouseTransparent(true);
        total.getStyleClass().add("projection-total-line");
        plot.add(total);
    }

    public void load(ProjectionChartModel model, boolean showTotal, Consumer<ProjectionChartModel.Point> selection) {
        this.model = model;
        for (var band : bands) plot.removeAll(band.fill(), band.stripe(), band.label());
        bands.clear();
        plot.removeAll(points);
        points.clear();
        total.setVisible(showTotal);
        total.getElements().clear();
        claims.setEvents(model.claims().stream().map(event -> {
            String text = event.year() + "\n" + event.person() + " claims at " + event.age();
            return new TimelineClaimMarkers.Event(event.year(), text, text + "\nExact claim date: " + event.date());
        }).toList());
        addBands(model.rothPeriods(), true);
        addBands(model.rmdPeriods(), false);
        if (showTotal) for (var point : model.years()) {
            Circle dot = new Circle(3.5);
            dot.setManaged(false);
            dot.getStyleClass().add("projection-total-point");
            String detail = ProjectionChartPresentation.tooltip(point, ProjectionChartMetric.INVESTABLE_ASSETS);
            Tooltip.install(dot, new Tooltip(detail));
            dot.setAccessibleText(detail);
            dot.setOnMouseClicked(event -> selection.accept(point));
            points.add(dot);
            plot.add(dot);
        }
    }

    private void addBands(List<ProjectionChartModel.Period> periods, boolean roth) {
        for (var period : periods) {
            Rectangle fill = new Rectangle(), stripe = new Rectangle();
            for (var node : List.of(fill, stripe)) { node.setManaged(false); node.setMouseTransparent(true); }
            fill.getStyleClass().add(roth ? "projection-roth-band" : "projection-rmd-band");
            stripe.getStyleClass().add(roth ? "projection-roth-stripe" : "projection-rmd-stripe");
            Label label = new Label(ProjectionChartPresentation.periodLabel(period, roth));
            label.setManaged(false);
            label.setWrapText(true);
            label.setAccessibleText(label.getText());
            label.getStyleClass().addAll("projection-period-label", roth ? "projection-roth-label" : "projection-rmd-label");
            Tooltip.install(label, new Tooltip(label.getText()));
            bands.add(new Band(period, roth, fill, stripe, label));
            plot.addAll(fill, stripe, label);
        }
    }

    public void layout(Axis<Number> x, Axis<Number> y) {
        for (var band : bands) {
            double left = Math.max(0, x.getDisplayPosition(band.period().firstYear() - 0.5));
            double right = Math.min(x.getWidth(), x.getDisplayPosition(band.period().lastYear() + 0.5));
            band.fill().setX(left); band.fill().setY(0);
            band.fill().setWidth(Math.max(0, right - left)); band.fill().setHeight(y.getHeight());
            band.fill().toBack();
            band.stripe().setX(left); band.stripe().setY(band.roth() ? 0 : Math.max(0, y.getHeight() - 3));
            band.stripe().setWidth(Math.max(0, right - left)); band.stripe().setHeight(3);
        }
        List<Bounds> occupied = new ArrayList<>(claims.layout(x, y.getHeight(), true));
        for (var band : bands) {
            double width = Math.min(135, Math.max(70, band.fill().getWidth()));
            width = Math.min(width, Math.max(0, x.getWidth() - 8));
            double height = band.label().prefHeight(width);
            double left = Math.max(4, Math.min(band.fill().getX() + (band.fill().getWidth() - width) / 2, x.getWidth() - width - 4));
            double top = 5;
            boolean collision;
            do {
                collision = false;
                for (var prior : occupied) {
                    if (prior.intersects(left - 3, top - 3, width + 6, height + 6)) {
                        top = prior.getMaxY() + 7;
                        collision = true;
                        break;
                    }
                }
            } while (collision);
            band.label().resizeRelocate(left, top, width, height);
            band.label().toFront();
            occupied.add(new BoundingBox(left, top, width, height));
            // Independent edges make simultaneous distributions discoverable without instructional text.
            boolean overlaps = bands.stream().anyMatch(other -> other.roth() != band.roth()
                    && other.period().firstYear() <= band.period().lastYear()
                    && other.period().lastYear() >= band.period().firstYear());
            band.stripe().setVisible(overlaps);
        }
        if (total.isVisible()) {
            total.getElements().clear();
            for (int i = 0; i < model.years().size(); i++) {
                var point = model.years().get(i);
                double px = x.getDisplayPosition(point.year());
                double py = y.getDisplayPosition(point.values().get(ProjectionChartMetric.INVESTABLE_ASSETS));
                total.getElements().add(i == 0 ? new MoveTo(px, py) : new LineTo(px, py));
                points.get(i).setCenterX(px); points.get(i).setCenterY(py);
                points.get(i).toFront();
            }
            total.toFront();
        }
    }
}
