package com.daviddunn.retirementplanner.ui.baseline;

import com.daviddunn.retirementplanner.app.baseline.*;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartView;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.*;
import java.text.NumberFormat;
import java.util.*;
import static com.daviddunn.retirementplanner.app.baseline.BaselineParameterComparison.*;
import static com.daviddunn.retirementplanner.app.baseline.ProjectionInputs.*;

/** Renders a frozen session. Filtering/expansion/chart selection have no controller or engine. */
public final class BaselineDetailsView extends VBox {
    private final BaselineDetailsSession session;
    private final VBox categories = new VBox(8);
    private final CheckBox changedOnly = new CheckBox("Show Changed Values Only");
    private final Map<String, Boolean> expansion = new HashMap<>();

    public BaselineDetailsView(BaselineDetailsSession session) {
        super(10);
        this.session = Objects.requireNonNull(session);
        getStyleClass().add("baseline-details");
        setMinWidth(0);
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/baseline-details.css")).toExternalForm());
        getChildren().addAll(note("Baseline: " + session.baselineDescription() + " | Captured: " + session.baselineCaptured()),
                note("Current projection: " + (session.currentRun() == null ? "Not recorded" : session.currentRun())
                        + " | Completed: " + (session.currentCompleted() == null ? "Not recorded" : session.currentCompleted())),
                note("Captured plan identity: " + (session.originalPlanIdentity() == null ? "Not recorded" : session.originalPlanIdentity()) + " | Current result years: " + session.currentYears()),
                note(session.status()), note("Saved Baseline Inputs | Frozen Current Projection Inputs | Difference"),
                note("Values use frozen accepted inputs when recorded. Legacy default-field provenance may be unavailable. Original result availability is identified below; unsaved plan changes are not yet durable."));
        if (session.baselineChart() == null || session.baselineChart().years().isEmpty()) {
            getChildren().addAll(note(BaselineDetailsSession.CHART_TITLE), note(BaselineDetailsSession.CHART_UNAVAILABLE));
        } else {
            var chart = new ProjectionChartView(BaselineDetailsSession.CHART_TITLE, true);
            chart.load(session.baselineChart());
            getChildren().add(chart);
            getChildren().add(note("Original baseline projection years: " + session.baselineChart().years().getFirst().year()
                    + "–" + session.baselineChart().years().getLast().year() + ". Values come from the original accepted result."));
        }
        changedOnly.setId("baseline-details-changed-only");
        changedOnly.setAccessibleHelp("Uncertain or unrecorded values remain visible in their own group.");
        changedOnly.selectedProperty().addListener((o, before, after) -> renderCategories());
        getChildren().addAll(changedOnly, categories);
        renderCategories();
    }
    private void renderCategories() {
        var grouped = new LinkedHashMap<String, List<Row>>();
        for (var row : session.rows()) {
            if (changedOnly.isSelected() && row.state() == State.UNCHANGED) continue;
            String category = row.state() == State.UNKNOWN ? "Uncertain or not recorded — " + row.category() : row.category();
            grouped.computeIfAbsent(category, key -> new ArrayList<>()).add(row);
        }
        categories.getChildren().clear();
        grouped.forEach((name, rows) -> {
            var table = new TableView<Row>(); table.setEditable(false);
            table.getStyleClass().add("baseline-parameter-table");
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.setMinWidth(0); table.setMinHeight(110); table.setPrefHeight(Math.min(340, 38 + rows.size() * 46));
            for (int i = 0; i < 4; i++) {
                final int index = i;
                var column = new TableColumn<Row, Row>(List.of("Parameter", "Baseline", "Current", "Difference").get(i));
                column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
                column.setSortable(false); column.setReorderable(false);
                column.setMinWidth(i == 0 ? 160 : 105); column.setPrefWidth(i == 0 ? 320 : 180);
                column.setCellFactory(ignored -> new TableCell<>() {
                    @Override protected void updateItem(Row row, boolean empty) {
                        super.updateItem(row, empty);
                        getStyleClass().removeAll("baseline-changed", "baseline-unknown", "baseline-difference");
                        setText(null); setGraphic(null); setTooltip(null);
                        if (empty || row == null) return;
                        String value = cellText(row, index);
                        var label = note(value); label.setMaxWidth(Double.MAX_VALUE);
                        label.prefWidthProperty().bind(widthProperty().subtract(16));
                        setGraphic(label); setAccessibleText(column.getText() + ": " + value);
                        setTooltip(new Tooltip(row.key() + "\n" + row.explanation() + "\nMatch: " + row.confidence()
                                + "\nExact baseline: " + display(row.baseline()) + "\nExact current: " + display(row.current())));
                        if (row.state() == State.UNKNOWN) getStyleClass().add("baseline-unknown");
                        else if (index == 1 || index == 2) {
                            if (row.state() != State.UNCHANGED) getStyleClass().add("baseline-changed");
                        }
                        if (index == 3) getStyleClass().add("baseline-difference");
                    }
                });
                table.getColumns().add(column);
            }
            table.setRowFactory(ignored -> {
                var row = new TableRow<Row>();
                var dependencies = new ArrayList<javafx.beans.Observable>();
                dependencies.add(row.itemProperty()); dependencies.add(row.fontProperty());
                table.getColumns().forEach(column -> dependencies.add(column.widthProperty()));
                row.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                    if (row.getItem() == null) return 32.0;
                    double height = 24;
                    for (int i = 0; i < 4; i++) {
                        var text = new javafx.scene.text.Text(cellText(row.getItem(), i));
                        text.setFont(row.getFont());
                        text.setWrappingWidth(Math.max(40, table.getColumns().get(i).getWidth() - 18));
                        height = Math.max(height, text.getLayoutBounds().getHeight());
                    }
                    return height + 16;
                }, dependencies.toArray(javafx.beans.Observable[]::new)));
                return row;
            });
            table.getItems().setAll(rows);
            var pane = new TitledPane(name + " (" + rows.size() + ")", table);
            pane.setExpanded(expansion.getOrDefault(name, true));
            pane.expandedProperty().addListener((o, before, after) -> expansion.put(name, after));
            categories.getChildren().add(pane);
        });
        if (grouped.isEmpty()) categories.getChildren().add(note("No definitively changed values. Unrecorded or uncertain data, if present, is shown separately."));
    }
    private static String cellText(Row row, int index) {
        return index == 0 ? row.label() : index == 1 ? format(row.baseline(), row.units())
                : index == 2 ? format(row.current(), row.units()) : difference(row);
    }
    public static String format(Value value, Units units) {
        if (!(value instanceof NumberValue number)) return display(value);
        if (units == Units.CURRENCY) return currency(number.value());
        if (units == Units.RATE) return number.value().movePointRight(2).setScale(Math.max(2, number.value().scale() - 2), RoundingMode.HALF_UP).toPlainString() + "%";
        return number.value().toPlainString();
    }
    public static String difference(Row row) {
        if (row.difference() == null) return switch (row.state()) {
            case UNKNOWN -> "Not recorded / uncertain";
            case ADDED -> "Added";
            case REMOVED -> "Removed";
            case CHANGED -> "Changed";
            case UNCHANGED -> "Unchanged";
        };
        var value = row.difference(); String sign = value.signum() > 0 ? "+" : "";
        return sign + switch (row.units()) {
            case CURRENCY -> currency(value) + (value.signum() != 0 && value.abs().compareTo(new BigDecimal("0.005")) < 0 ? " (below display precision)" : "");
            case RATE -> value.movePointRight(2).setScale(Math.max(2, value.scale() - 2), RoundingMode.HALF_UP).toPlainString() + " pp";
            case YEARS -> value.toPlainString() + " years";
            case DAYS -> value.toPlainString() + " days";
            default -> value.toPlainString();
        };
    }
    private static String currency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }
    private static Label note(String value) {
        var label = new Label(value); label.setWrapText(true); label.setMinWidth(0); label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
}
