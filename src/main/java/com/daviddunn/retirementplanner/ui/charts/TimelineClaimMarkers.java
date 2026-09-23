package com.daviddunn.retirementplanner.ui.charts;

import javafx.collections.ObservableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.Line;
import java.util.ArrayList;
import java.util.List;

/** Shared plot-space rendering and collision handling. Knows no claiming or financial rules. */
public final class TimelineClaimMarkers {
    public record Event(int year, String label, String tooltip) { }
    private record Nodes(Event event, Line line, Label label) { }
    private final ObservableList<Node> plot;
    private final List<Nodes> events = new ArrayList<>();

    public TimelineClaimMarkers(ObservableList<Node> plot) { this.plot = plot; }

    public void setEvents(List<Event> values) {
        for (var event : events) plot.removeAll(event.line(), event.label());
        events.clear();
        for (var event : values) {
            Line line = new Line();
            line.setManaged(false);
            line.setMouseTransparent(true);
            line.getStyleClass().addAll("timeline-claim-line", "break-even-claim-line");
            Label label = new Label(event.label());
            label.setManaged(false);
            label.setWrapText(true);
            label.getStyleClass().addAll("timeline-claim-label", "break-even-claim-label");
            label.setAccessibleText(event.label());
            Tooltip.install(label, new Tooltip(event.tooltip()));
            events.add(new Nodes(event, line, label));
            plot.addAll(line, label);
        }
    }

    public List<Bounds> layout(Axis<Number> axis, double plotHeight) {
        return layout(axis, plotHeight, false);
    }

    /** Filled projection charts need foreground strokes so area fills cannot wash them out. */
    public List<Bounds> layout(Axis<Number> axis, double plotHeight, boolean foregroundStrokes) {
        List<Bounds> occupied = new ArrayList<>();
        double plotWidth = axis.getWidth();
        for (var event : events) {
            double x = axis.getDisplayPosition(event.event().year());
            double width = Math.min(145, Math.max(30, plotWidth - 12));
            double height = event.label().prefHeight(width);
            double left = Math.max(4, Math.min(x - width / 2, plotWidth - width - 4));
            double top = 4;
            boolean collision;
            do {
                collision = false;
                for (Bounds prior : occupied) {
                    if (prior.intersects(left - 3, top - 3, width + 6, height + 6)) {
                        top = prior.getMaxY() + 7;
                        collision = true;
                        break;
                    }
                }
            } while (collision);
            event.line().setStartX(x); event.line().setEndX(x);
            event.line().setStartY(0); event.line().setEndY(plotHeight);
            if (foregroundStrokes) event.line().toFront(); else event.line().toBack();
            event.label().resizeRelocate(left, top, width, height);
            event.label().toFront();
            occupied.add(new BoundingBox(left, top, width, height));
        }
        return occupied;
    }
}
