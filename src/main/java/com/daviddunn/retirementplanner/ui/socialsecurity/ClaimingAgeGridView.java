package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** UI-only age-pair geometry and interaction. Financial interpretation belongs to callers. */
final class ClaimingAgeGridView extends GridPane {
    record Pair(int primary, int spouse) { }
    record Cell(Pair pair, String text, Optional<BigDecimal> percent, boolean optimal,
                boolean retained, String description, String tooltip) { }

    private final Map<Pair, ToggleButton> buttons = new LinkedHashMap<>();
    private final Map<Pair, Cell> cells = new LinkedHashMap<>();
    private final ToggleGroup selection = new ToggleGroup();
    private List<Integer> primary = List.of();
    private List<Integer> spouse = List.of();
    private Consumer<Pair> changed = pair -> { };
    private Pair selected;
    private boolean retainedMarkers;

    ClaimingAgeGridView() {
        setHgap(4);
        setVgap(4);
        setMinWidth(0);
        setMinHeight(Region.USE_PREF_SIZE);
    }

    void render(List<Integer> primaryAges, List<Integer> spouseAges, List<Cell> values,
                String idPrefix, Consumer<Pair> onSelected) {
        clear();
        primary = List.copyOf(primaryAges);
        spouse = List.copyOf(spouseAges);
        changed = Objects.requireNonNull(onSelected);
        retainedMarkers = values.stream().anyMatch(Cell::retained);
        for (Cell value : values) {
            if (!primary.contains(value.pair.primary) || !spouse.contains(value.pair.spouse)
                    || cells.putIfAbsent(value.pair, value) != null) throw new IllegalArgumentException("Invalid claiming grid pair.");
        }
        for (int column = 0; column <= primary.size(); column++) {
            var constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / (primary.size() + 1));
            constraint.setMinWidth(0);
            getColumnConstraints().add(constraint);
        }
        var axis = label("Spouse\nClaiming Age");
        axis.getStyleClass().add("heat-map-axis");
        add(axis, 0, 0);
        for (int x = 0; x < primary.size(); x++) add(label(primary.get(x).toString()), x + 1, 0);
        for (int y = 0; y < spouse.size(); y++) {
            add(label(spouse.get(y).toString()), 0, y + 1);
            for (int x = 0; x < primary.size(); x++) {
                var pair = new Pair(primary.get(x), spouse.get(y));
                if (!cells.containsKey(pair)) continue; // Do not manufacture absent source cells.
                var button = new ToggleButton();
                button.setId(idPrefix + pair.primary + "-" + pair.spouse);
                button.setToggleGroup(selection);
                button.setMinWidth(0);
                button.setMinHeight(40);
                button.setPrefHeight(40);
                button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                button.setWrapText(true);
                button.widthProperty().addListener((o, before, after) -> {
                    if (retainedMarkers && (before.doubleValue() < 110) != (after.doubleValue() < 110)) update(cells.get(pair));
                });
                button.getStyleClass().add("heat-map-cell");
                button.setOnAction(event -> select(pair));
                button.setOnKeyPressed(event -> {
                    int px = primary.indexOf(pair.primary), sy = spouse.indexOf(pair.spouse);
                    switch (event.getCode()) {
                        case LEFT -> px--;
                        case RIGHT -> px++;
                        case UP -> sy--;
                        case DOWN -> sy++;
                        default -> { return; }
                    }
                    if (px >= 0 && px < primary.size() && sy >= 0 && sy < spouse.size()) {
                        var target = new Pair(primary.get(px), spouse.get(sy));
                        if (buttons.containsKey(target)) { select(target); buttons.get(target).requestFocus(); }
                    }
                    event.consume();
                });
                buttons.put(pair, button);
                add(button, x + 1, y + 1);
                update(cells.get(pair));
            }
        }
    }

    void update(Cell cell) {
        cells.put(cell.pair, cell);
        var button = buttons.get(cell.pair);
        if (button == null) return;
        button.getStyleClass().removeIf(name -> name.startsWith("heat-map-tier-") || name.equals("heat-map-unavailable") || name.equals("heat-map-optimal"));
        button.getStyleClass().add(ClaimingHeatMapPalette.forPercent(cell.percent).css);
        if (cell.optimal) button.getStyleClass().add("heat-map-optimal");
        String markers = (cell.optimal ? "★ " : "") + (cell.retained ? "▶ " : "");
        boolean stackedMarkers = retainedMarkers && button.getWidth() < 110;
        button.setMinHeight(stackedMarkers ? 52 : 40);
        button.setPrefHeight(stackedMarkers ? 52 : 40);
        button.setText(markers + (stackedMarkers && !markers.isEmpty() ? "\n" : "") + cell.text);
        button.setTooltip(HelpIcon.createTooltip(cell.tooltip));
        button.setAccessibleText(cell.description + (cell.pair.equals(selected) ? " — Selected" : " — Not selected"));
    }

    void select(Pair pair) {
        if (!buttons.containsKey(pair)) return;
        var before = selected;
        selected = pair;
        buttons.get(pair).setSelected(true);
        if (before != null) update(cells.get(before));
        update(cells.get(pair));
        changed.accept(pair);
    }
    Pair selectedPair() { return selected; }
    boolean contains(Pair pair) { return buttons.containsKey(pair); }
    void clear() {
        getChildren().clear(); getColumnConstraints().clear();
        selection.getToggles().clear(); buttons.clear(); cells.clear(); selected = null;
    }

    static FlowPane legend(String note) {
        var legend = new FlowPane(8, 6);
        for (var tier : ClaimingHeatMapPalette.values()) {
            if (tier == ClaimingHeatMapPalette.UNAVAILABLE) continue;
            var swatch = label(tier.label);
            swatch.getStyleClass().addAll("heat-map-legend-item", tier.css);
            swatch.setTooltip(HelpIcon.createTooltip(tier.help));
            legend.getChildren().add(swatch);
        }
        var marker = label(note);
        marker.setTooltip(HelpIcon.createTooltip("Colors use unrounded percentages. ★ marks exact optima, including ties. "
                + "99.5%, 99%, 97% and 95% are display thresholds, not recommendations."));
        legend.getChildren().add(marker);
        return legend;
    }

    GridPane withDetails(Region details) {
        var area = new VBox(4);
        var axis = label("Primary Claiming Age");
        axis.setAlignment(javafx.geometry.Pos.CENTER);
        axis.getStyleClass().add("heat-map-axis");
        area.getChildren().addAll(axis, this);
        area.setId("heat-map-grid-area");
        area.setMinWidth(0); area.setMinHeight(Region.USE_PREF_SIZE);
        details.setMinWidth(0); details.setMinHeight(Region.USE_PREF_SIZE);
        var layout = new GridPane();
        layout.setHgap(16); layout.setVgap(12); layout.setMinWidth(0);
        layout.setMinHeight(Region.USE_PREF_SIZE);
        layout.getChildren().addAll(area, details);
        Runnable arrange = () -> {
            boolean horizontal = layout.getWidth() >= 1200;
            layout.getColumnConstraints().clear();
            var first = new ColumnConstraints(); first.setPercentWidth(horizontal ? 73 : 100); first.setMinWidth(0);
            layout.getColumnConstraints().add(first);
            if (horizontal) {
                var second = new ColumnConstraints(); second.setPercentWidth(27); second.setMinWidth(0);
                layout.getColumnConstraints().add(second);
            }
            GridPane.setConstraints(area, 0, 0);
            GridPane.setConstraints(details, horizontal ? 1 : 0, horizontal ? 0 : 1);
            GridPane.setValignment(area, VPos.TOP); GridPane.setValignment(details, VPos.TOP);
        };
        layout.widthProperty().addListener((o, before, after) -> {
            if ((before.doubleValue() >= 1200) != (after.doubleValue() >= 1200)) arrange.run();
        });
        arrange.run();
        return layout;
    }

    static Label label(String text) {
        var label = new Label(text); label.setWrapText(true); label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE); label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
