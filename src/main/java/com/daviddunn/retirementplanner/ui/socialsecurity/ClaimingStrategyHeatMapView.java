package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.IntConsumer;

/** FX-only interaction with immutable display values. The sole navigation callback identifies an existing result. */
final class ClaimingStrategyHeatMapView extends VBox {
    static final String EMPTY_TEXT = "Run the integrated Social Security analysis to view the claiming-age heat map.";
    private static final String UNAVAILABLE = "Not Available";
    private static final String SURVIVOR_HELP = "Each age pair shows its best successful complete strategy from the integrated analyzer. "
            + "Primary survivor elections apply when the spouse dies first; spouse survivor elections apply when the primary dies first. "
            + "The exact survivor ages and dates are retained from that result. No survivor election is assumed.";
    private final ClaimingStrategyHeatMapProfile profile;
    private final IntConsumer openStrategy;
    final ComboBox<ClaimingStrategyHeatMapMetric> metric = new ComboBox<>();
    final Label resultNotice = label("");
    private final Label empty = label(EMPTY_TEXT);
    private final VBox results = new VBox(8);
    private final Label context = label("");
    private final GridPane grid = new GridPane();
    private final GridPane strategyLayout = new GridPane();
    private final VBox gridArea = new VBox(4);
    private final VBox details = new VBox(6);
    private boolean sideBySide;
    private final Map<ClaimingStrategyHeatMapCell, ToggleButton> buttons = new LinkedHashMap<>();
    private final ToggleGroup selection = new ToggleGroup();
    private final Label selectedTitle = label("Selected Strategy Details");
    private final Label selectedPercent = label("");
    private final Label selectedNote = label("");
    private final Label selectedMetric = label("");
    private final Label selectedRank = label("");
    private final Label elections = label("");
    private final EnumMap<ClaimingStrategyHeatMapMetric, Label> detailValues = new EnumMap<>(ClaimingStrategyHeatMapMetric.class);
    final Button fullAnalysis = new Button("View Full Strategy Analysis");
    private ClaimingStrategyHeatMapModel model;
    private ClaimingStrategyHeatMapCell selected;
    private boolean busy;

    ClaimingStrategyHeatMapView(IntConsumer openStrategy) {
        this(ClaimingStrategyHeatMapProfile.weighted(), openStrategy);
    }

    ClaimingStrategyHeatMapView(ClaimingStrategyHeatMapProfile profile, IntConsumer openStrategy) {
        super(8);
        this.profile = Objects.requireNonNull(profile);
        this.openStrategy = Objects.requireNonNull(openStrategy);
        getStyleClass().add("claiming-heat-map");
        setStyle(ClaimingHeatMapPalette.cssVariables());
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/claiming-strategy-heat-map.css")).toExternalForm());
        setMinWidth(0);
        setMinHeight(Region.USE_PREF_SIZE);
        Label title = label("Retirement Plan Value by Social Security Claiming Ages");
        title.getStyleClass().add("heat-map-heading");
        title.setTooltip(HelpIcon.createTooltip("Each cell shows how the complete retirement plan performs for a given primary "
                + "and spouse Social Security claiming-age combination. The best result is 100%."));
        HBox heading = new HBox(8, title, new HelpIcon(profile.explanation()));
        heading.setAlignment(Pos.CENTER_LEFT);
        metric.getItems().setAll(profile.displayMetrics());
        metric.setValue(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL);
        metric.setAccessibleText("Heat map display metric");
        metric.setTooltip(HelpIcon.createTooltip(profile.help(metric.getValue())));
        metric.setMaxWidth(Double.MAX_VALUE);
        Label display = new Label("Display:");
        display.setLabelFor(metric);
        HBox selector = new HBox(8, display, metric, context);
        selector.setAlignment(Pos.CENTER_LEFT);
        FlowPane legend = new FlowPane(8, 6);
        for (var tier : ClaimingHeatMapPalette.values()) {
            if (tier == ClaimingHeatMapPalette.UNAVAILABLE) continue;
            Label swatch = label(tier.label);
            swatch.getStyleClass().addAll("heat-map-legend-item", tier.css);
            swatch.setTooltip(HelpIcon.createTooltip(tier.help));
            legend.getChildren().add(swatch);
        }
        Label legendNote = label("★ Optimal");
        legendNote.setTooltip(HelpIcon.createTooltip("Colors always show % of optimal using unrounded values. ★ marks an exact optimal result, including ties. "
                + "Essentially optimal: at least 99.5%. Very close: 99.0% to below 99.5%. "
                + "Moderate difference: 97.0% to below 99.0%. Meaningful difference: 95.0% to below 97.0%. "
                + "Substantially worse: below 95.0%. These are display bands, not recommendations."));
        legend.getChildren().add(legendNote);
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setMinWidth(0);
        GridPane metrics = new GridPane();
        metrics.setHgap(12);
        metrics.setVgap(4);
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setPercentWidth(55);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPercentWidth(45);
        metrics.getColumnConstraints().addAll(nameColumn, valueColumn);
        int row = 0;
        for (var detailMetric : profile.detailMetrics()) {
            if (detailMetric == ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL) continue;
            Label name = label(detailMetric.toString());
            name.setTooltip(HelpIcon.createTooltip(profile.help(detailMetric)));
            Label value = label(UNAVAILABLE);
            value.setTooltip(HelpIcon.createTooltip(profile.help(detailMetric)));
            detailValues.put(detailMetric, value);
            metrics.addRow(row++, name, value);
        }
        selectedTitle.getStyleClass().add("heat-map-heading");
        selectedPercent.setTooltip(HelpIcon.createTooltip(profile.help(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)));
        Label survivorHeading = label("Complete Social Security Elections");
        survivorHeading.getStyleClass().add("heat-map-heading");
        survivorHeading.setTooltip(HelpIcon.createTooltip(SURVIVOR_HELP));
        elections.setTooltip(HelpIcon.createTooltip(SURVIVOR_HELP));
        details.getChildren().addAll(selectedTitle, selectedPercent, selectedRank, selectedMetric, selectedNote, metrics, survivorHeading, elections, fullAnalysis);
        details.setId("heat-map-details");
        details.getStyleClass().add("heat-map-details");
        details.setMinHeight(Region.USE_PREF_SIZE);
        Label primaryAxis = label("Primary Claiming Age");
        primaryAxis.setAlignment(Pos.CENTER);
        primaryAxis.getStyleClass().add("heat-map-axis");
        gridArea.getChildren().addAll(primaryAxis, grid);
        gridArea.setId("heat-map-grid-area");
        gridArea.setMinWidth(0);
        gridArea.setMinHeight(Region.USE_PREF_SIZE);
        strategyLayout.setHgap(16);
        strategyLayout.setVgap(12);
        strategyLayout.setMinHeight(Region.USE_PREF_SIZE);
        strategyLayout.getChildren().addAll(gridArea, details);
        arrange(false);
        widthProperty().addListener((observable, before, after) -> arrange(after.doubleValue() >= 1200));
        results.getChildren().add(strategyLayout);
        results.setMinHeight(Region.USE_PREF_SIZE);
        resultNotice.managedProperty().bind(resultNotice.textProperty().isNotEmpty());
        resultNotice.visibleProperty().bind(resultNotice.managedProperty());
        getChildren().addAll(heading, selector, legend, resultNotice, empty, results);
        results.setVisible(false);
        results.setManaged(false);
        metric.valueProperty().addListener((observable, before, after) -> {
            if (after != null) {
                metric.setTooltip(HelpIcon.createTooltip(profile.help(after)));
                updateCellText();
                if (selected != null) updateSelectedMetric();
            }
        });
        fullAnalysis.setOnAction(event -> {
            if (selected != null && !busy) selected.strategy().ifPresent(value -> this.openStrategy.accept(value.inputOrder()));
        });
        setAnalysisBusy(false);
    }

    void render(ClaimingStrategyHeatMapModel value) {
        model = Objects.requireNonNull(value);
        metric.setValue(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL);
        empty.setVisible(false);
        empty.setManaged(false);
        results.setVisible(true);
        results.setManaged(true);
        context.setText(value.context()
                + (value.optimalValue().isEmpty() ? " — No successful strategies."
                : value.optimalValue().orElseThrow().signum() <= 0
                        ? " — % unavailable: highest value is zero or negative." : ""));
        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        buttons.clear();
        selection.getToggles().clear();
        for (int column = 0; column <= value.primaryAges().size(); column++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / (value.primaryAges().size() + 1));
            constraint.setMinWidth(0);
            grid.getColumnConstraints().add(constraint);
        }
        Label spouseAxis = label("Spouse\nClaiming Age");
        spouseAxis.getStyleClass().add("heat-map-axis");
        grid.add(spouseAxis, 0, 0);
        for (int column = 0; column < value.primaryAges().size(); column++) {
            grid.add(label(Integer.toString(value.primaryAges().get(column))), column + 1, 0);
        }
        for (int row = 0; row < value.spouseAges().size(); row++) {
            grid.add(label(Integer.toString(value.spouseAges().get(row))), 0, row + 1);
            for (int column = 0; column < value.primaryAges().size(); column++) {
                var cell = value.cell(value.primaryAges().get(column), value.spouseAges().get(row));
                ToggleButton button = new ToggleButton();
                button.setId("claiming-heat-map-" + cell.primaryClaimingAge() + "-" + cell.spouseClaimingAge());
                button.setToggleGroup(selection);
                button.setMinWidth(0);
                button.setMinHeight(40);
                button.setPrefHeight(40);
                button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                button.setWrapText(true);
                button.getStyleClass().addAll("heat-map-cell", tierClass(cell));
                if (cell.optimal()) button.getStyleClass().add("heat-map-optimal");
                button.setOnAction(event -> select(cell));
                buttons.put(cell, button);
                grid.add(button, column + 1, row + 1);
            }
        }
        updateCellText();
        select(value.cells().stream().filter(ClaimingStrategyHeatMapCell::optimal).findFirst()
                .orElse(value.cells().getFirst()));
        setAnalysisBusy(busy);
    }

    private void arrange(boolean horizontal) {
        if (sideBySide == horizontal && !strategyLayout.getColumnConstraints().isEmpty()) return;
        sideBySide = horizontal;
        strategyLayout.getColumnConstraints().clear();
        ColumnConstraints gridColumn = new ColumnConstraints();
        gridColumn.setPercentWidth(horizontal ? 73 : 100);
        gridColumn.setMinWidth(0);
        strategyLayout.getColumnConstraints().add(gridColumn);
        if (horizontal) {
            ColumnConstraints detailColumn = new ColumnConstraints();
            detailColumn.setPercentWidth(27);
            detailColumn.setMinWidth(0);
            strategyLayout.getColumnConstraints().add(detailColumn);
        }
        GridPane.setConstraints(gridArea, 0, 0);
        GridPane.setConstraints(details, horizontal ? 1 : 0, horizontal ? 0 : 1);
        GridPane.setValignment(gridArea, javafx.geometry.VPos.TOP);
        GridPane.setValignment(details, javafx.geometry.VPos.TOP);
    }

    void setAnalysisBusy(boolean value) {
        busy = value;
        metric.setDisable(value || model == null);
        grid.setDisable(value);
        fullAnalysis.setDisable(value || selected == null || selected.strategy().isEmpty());
        if (model == null) empty.setText(value ? "Integrated analysis is running. Progress is shown above." : EMPTY_TEXT);
    }

    private void updateCellText() {
        buttons.forEach((cell, button) -> {
            String value = format(metric.getValue(), cell.value(metric.getValue()));
            button.setText((cell.optimal() ? "★ " : "") + value);
            String description = "Primary " + cell.primaryClaimingAge() + " / Spouse " + cell.spouseClaimingAge()
                    + (cell.optimal() ? " — Optimal" : "") + "\n" + metric.getValue() + ": " + value;
            button.setAccessibleText(description);
            button.setTooltip(HelpIcon.createTooltip(description + "\n" + note(cell)));
        });
    }

    private void select(ClaimingStrategyHeatMapCell cell) {
        selected = cell;
        buttons.get(cell).setSelected(true);
        selectedTitle.setText("Primary " + cell.primaryClaimingAge() + " / Spouse " + cell.spouseClaimingAge());
        selectedPercent.setText(cell.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)
                .map(value -> format(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL, Optional.of(value)) + " of optimal")
                .orElse("% of optimal: " + UNAVAILABLE) + (cell.optimal() ? " — Optimal" : ""));
        selectedNote.setText(note(cell));
        selectedRank.setText(profile.rankLabel() + ": " + cell.strategy().map(value -> value.rank().isPresent()
                ? Integer.toString(value.rank().getAsInt()) : UNAVAILABLE).orElse(UNAVAILABLE));
        updateSelectedMetric();
        detailValues.forEach((key, label) -> label.setText(format(key, cell.value(key))));
        elections.setText(cell.strategy().map(value -> "Primary own age: " + cell.primaryClaimingAge()
                + "\nSpouse own age: " + cell.spouseClaimingAge()
                + "\nPrimary Survivor Benefit Claiming Age: " + age(value.primarySurvivor())
                + "\nSpouse Survivor Benefit Claiming Age: " + age(value.spouseSurvivor()))
                .orElse("Primary own age: " + cell.primaryClaimingAge() + "\nSpouse own age: " + cell.spouseClaimingAge()
                        + "\nPrimary Survivor Benefit Claiming Age: " + UNAVAILABLE + "\nSpouse Survivor Benefit Claiming Age: " + UNAVAILABLE));
        fullAnalysis.setDisable(busy || cell.strategy().isEmpty());
    }

    private void updateSelectedMetric() {
        selectedMetric.setText(metric.getValue() + ": " + format(metric.getValue(), selected.value(metric.getValue())));
        selectedMetric.setTooltip(HelpIcon.createTooltip(profile.help(metric.getValue())));
    }

    private static String note(ClaimingStrategyHeatMapCell cell) {
        if (cell.strategy().isEmpty()) return "No successful result for this age pair. "
                + (cell.failedStrategyCount() == 0 ? "This combination was not included in the successful analysis results."
                : cell.failedStrategyCount() + " complete strategies failed evaluation.");
        return "Best successful complete strategy for this age pair."
                + (cell.failedStrategyCount() == 0 ? "" : " " + cell.failedStrategyCount() + " other strategies for this pair failed evaluation.");
    }

    static String format(ClaimingStrategyHeatMapMetric metric, Optional<BigDecimal> value) {
        return ClaimingHeatMapFormatting.format(metric, value);
    }

    private static String age(ClaimingStrategyHeatMapCell.SurvivorElection value) {
        return value.ageYears() + (value.ageMonths() == 0 ? "" : " years " + value.ageMonths() + " months") + " — " + value.claimDate();
    }

    static String tierClass(ClaimingStrategyHeatMapCell cell) {
        return ClaimingHeatMapPalette.forPercent(cell.value(ClaimingStrategyHeatMapMetric.PERCENT_OF_OPTIMAL)).css;
    }

    ClaimingStrategyHeatMapModel reportModel() { return model; }
    ClaimingStrategyHeatMapCell reportSelection() { return selected; }

    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
