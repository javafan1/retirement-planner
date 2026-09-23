package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import java.util.Objects;
import java.util.function.Function;

/** Renders an immutable, already calculated result; it has no projection/controller access. */
public final class BreakEvenAnalysisView extends VBox {
    private final BreakEvenAnalysisResult result;
    private final BreakEvenContext context;
    private final Label insightHeadline = label("");
    private final ComboBox<BreakEvenMetric> metricSelector = new ComboBox<>();
    private final Label selectedSummary = new Label();
    private final Label selectedMetricName = new Label();
    private final GridPane summaryCards = new GridPane();
    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private final BreakEvenChart chart = new BreakEvenChart(xAxis, yAxis);
    private final TableView<BreakEvenYearResult> table = new TableView<>();
    private TableColumn<BreakEvenYearResult, String> baselineColumn;
    private TableColumn<BreakEvenYearResult, String> currentColumn;
    private Integer sustainedYear;

    public BreakEvenAnalysisView(BreakEvenAnalysisResult result) {
        this(result, BreakEvenContext.unavailable());
    }

    public BreakEvenAnalysisView(BreakEvenAnalysisResult result, BreakEvenContext context) {
        this(result, context, new BreakEvenInsightService().prepare(result));
    }

    public BreakEvenAnalysisView(BreakEvenAnalysisResult result, BreakEvenContext context, BreakEvenInsight insight) {
        this.result = Objects.requireNonNull(result);
        this.context = Objects.requireNonNull(context);
        setSpacing(6);
        setPadding(new Insets(10));
        setMinWidth(0);
        getStyleClass().add("break-even-view");
        var plans = new FlowPane(20, 2);
        plans.setId("break-even-plans");
        plans.setMinWidth(0);
        for (var entry : java.util.List.of(planCard("Baseline Plan", result.baselineAssumptions()),
                planCard("Current Plan", result.currentAssumptions()))) {
            entry.maxWidthProperty().bind(plans.widthProperty());
            plans.getChildren().add(entry);
        }
        getChildren().add(plans);
        getChildren().add(heading("Break-Even Summary"));
        summaryCards.setId("break-even-cards");
        summaryCards.setHgap(10);
        summaryCards.setVgap(6);
        summaryCards.setMinWidth(0);
        for (BreakEvenMetric metric : BreakEvenMetric.values()) {
            summaryCards.getChildren().add(summaryCard(result.metrics().get(metric)));
        }
        summaryCards.widthProperty().addListener((observable, oldWidth, width) -> arrangeSummaryCards(width.doubleValue()));
        arrangeSummaryCards(1000);
        getChildren().add(summaryCards);
        getChildren().add(insightPanel(insight));
        Label period = label(BreakEvenPresentation.period(result));
        period.setId("break-even-period");
        period.setTooltip(new Tooltip(BreakEvenPresentation.periodHelp()));
        period.setAccessibleHelp(BreakEvenPresentation.periodHelp());


        metricSelector.setId("break-even-metric");
        metricSelector.getItems().setAll(BreakEvenMetric.values());
        metricSelector.setConverter(new StringConverter<>() {
            @Override public String toString(BreakEvenMetric value) { return value == null ? "" : BreakEvenPresentation.metricName(value); }
            @Override public BreakEvenMetric fromString(String value) { throw new UnsupportedOperationException(); }
        });
        metricSelector.setAccessibleText("Break-Even Metric");
        metricSelector.setMinWidth(0);
        metricSelector.setPrefWidth(360);
        metricSelector.setMaxWidth(420);
        selectedSummary.setWrapText(true);
        selectedSummary.setMinHeight(Region.USE_PREF_SIZE);
        selectedSummary.setId("break-even-selected-summary");
        var banner = new VBox(selectedSummary);
        banner.setId("break-even-metric-banner");
        banner.getStyleClass().add("break-even-banner");
        var controls = new GridPane();
        controls.setId("break-even-comparison-controls");
        controls.setHgap(16);
        controls.setVgap(4);
        controls.setMinWidth(0);
        var selectorBox = new HBox(6, label("Chart:"), metricSelector);
        selectorBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        selectorBox.setMinWidth(0);
        HBox.setHgrow(metricSelector, Priority.ALWAYS);
        controls.add(period, 0, 0);
        controls.add(selectorBox, 1, 0);
        GridPane.setHgrow(period, Priority.ALWAYS);
        controls.widthProperty().addListener((observable, oldWidth, width) -> {
            boolean narrow = width.doubleValue() < 720;
            controls.getColumnConstraints().clear();
            var flexible = new ColumnConstraints(); flexible.setMinWidth(0); flexible.setHgrow(Priority.ALWAYS);
            controls.getColumnConstraints().add(flexible);
            if (!narrow) { var select = new ColumnConstraints(); select.setMinWidth(0); select.setPrefWidth(410); controls.getColumnConstraints().add(select); }
            GridPane.setColumnIndex(selectorBox, narrow ? 0 : 1);
            GridPane.setRowIndex(selectorBox, narrow ? 1 : 0);
        });
        chart.setId("break-even-chart");
        chart.setAnimated(false);
        chart.setMinHeight(360);
        chart.setPrefHeight(400);
        chart.setMaxHeight(400);
        chart.setHorizontalZeroLineVisible(true);
        chart.setAccessibleText("Difference: Current minus Baseline. Positive means Current ahead; negative means Baseline ahead. Zero means equal.");
        chart.setLegendVisible(false);
        xAxis.setLabel("Calendar Year");
        xAxis.setForceZeroInRange(false);
        xAxis.setMinorTickVisible(false);
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return Integer.toString(value.intValue()); }
            @Override public Number fromString(String value) { return Integer.valueOf(value); }
        });
        if (result.comparableYearCount() > 0) {
            xAxis.setAutoRanging(false);
            int start = result.comparisonStartYear(), end = result.comparisonEndYear();
            xAxis.setLowerBound(start == end ? start - 0.5 : start);
            xAxis.setUpperBound(start == end ? end + 0.5 : end);
            xAxis.setTickUnit(Math.max(1, Math.ceil((end - start) / 12.0)));
        }
        yAxis.setForceZeroInRange(true);
        table.setId("break-even-years");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("Year", point -> Integer.toString(point.year()));
        column(result.currentAssumptions().primary().name() + " Age", point -> BreakEvenPresentation.age(point.primaryAge()));
        column(result.currentAssumptions().spouse().name() + " Age", point -> BreakEvenPresentation.age(point.spouseAge()));
        baselineColumn = column("Baseline", point -> BreakEvenPresentation.money(point.baselineValue()));
        currentColumn = column("Current", point -> BreakEvenPresentation.money(point.currentValue()));
        column("Difference (Current − Baseline)", point -> BreakEvenPresentation.signedMoney(point.difference()));
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override
            protected void updateItem(BreakEvenYearResult point, boolean empty) {
                super.updateItem(point, empty);
                boolean sustained = !empty && point != null && Objects.equals(sustainedYear, point.year());
                pseudoClassStateChanged(PseudoClass.getPseudoClass("sustained-break-even"), sustained);
                setAccessibleText(sustained ? "Sustained break-even year " + point.year() : null);
            }
        });
        table.setMinHeight(220);
        table.setPrefHeight(220);
        table.setMaxHeight(220);
        table.setPlaceholder(new Label("No comparable years"));
        Label currentAhead = label("CURRENT PLAN AHEAD (+)");
        currentAhead.getStyleClass().addAll("break-even-region", "comparison-positive");
        Label baselineAhead = label("BASELINE AHEAD (−)");
        baselineAhead.getStyleClass().addAll("break-even-region", "comparison-negative");
        chart.setContext(context);
        getChildren().addAll(controls, banner, new VBox(0, currentAhead, chart, chart.survivalRow(), baselineAhead),
                heading("Key Years — Annual Comparison"), table);
        metricSelector.valueProperty().addListener((observable, oldValue, metric) -> showMetric(metric));
        metricSelector.setValue(BreakEvenMetric.TOTAL_NET_WORTH);
    }

    private void showMetric(BreakEvenMetric metric) {
        if (metric == null) return;
        insightHeadline.setText(BreakEvenInsightPresentation.headline(result, metric));
        var analysis = result.metrics().get(metric);
        boolean cumulative = metric == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY;
        selectedMetricName.setText(BreakEvenPresentation.metricName(metric));
        selectedSummary.setText(BreakEvenPresentation.metricName(metric) + " · " + BreakEvenPresentation.banner(result, analysis));
        chart.setTitle(BreakEvenPresentation.metricName(metric) + " Difference (Current − Baseline)");
        yAxis.setLabel(cumulative ? "Cumulative Difference ($)" : "End-of-Year Difference ($)");
        baselineColumn.setText(cumulative ? "Baseline (Cumulative)" : "Baseline");
        currentColumn.setText(cumulative ? "Current (Cumulative)" : "Current");
        sustainedYear = analysis.sustainedBreakEvenYear();
        table.getItems().setAll(analysis.years());
        table.refresh();
        var sustainedPoint = BreakEvenPresentation.sustainedPoint(analysis);
        chart.setBreakEven(sustainedPoint, sustainedPoint == null ? "" :
                (metric == BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY ? "SS Break-Even " : "Break-even ") + sustainedPoint.year()
                + "\n" + BreakEvenPresentation.compactAges(sustainedPoint, result.currentAssumptions()));
        chart.getData().clear();
        var difference = new XYChart.Series<Number, Number>();
        difference.setName("Current − Baseline");
        var zero = new XYChart.Series<Number, Number>();
        zero.setName("Equal / Break-even");
        var sustained = new XYChart.Series<Number, Number>();
        sustained.setName("Sustained Break-Even");
        for (var year : analysis.years()) {
            var data = new XYChart.Data<Number, Number>(year.year(), year.difference());
            var symbol = new StackPane();
            symbol.getStyleClass().add(year.difference().signum() < 0 ? "baseline-ahead-point" : "current-ahead-point");
            data.setNode(symbol);
            String pointDetails = "Year " + year.year() + "\n"
                    + BreakEvenPresentation.ages(year, result.currentAssumptions())
                    + "\nBaseline" + (cumulative ? " cumulative SS" : "") + ": " + BreakEvenPresentation.money(year.baselineValue())
                    + "\nCurrent" + (cumulative ? " cumulative SS" : "") + ": " + BreakEvenPresentation.money(year.currentValue())
                    + "\nDifference: " + BreakEvenPresentation.signedMoney(year.difference());
            var survival = context.survival().get(year.year());
            if (survival != null) pointDetails += "\nAt least one alive: " + BreakEvenPresentation.probability(survival);
            Tooltip.install(symbol, new Tooltip(pointDetails));
            // XYChart.Data supplies a generic binding when its node is assigned.
            symbol.accessibleTextProperty().unbind();
            symbol.setAccessibleText(pointDetails);
            difference.getData().add(data);
            var reference = new XYChart.Data<Number, Number>(year.year(), 0);
            var referenceSymbol = new StackPane();
            referenceSymbol.setMouseTransparent(true);
            reference.setNode(referenceSymbol);
            zero.getData().add(reference);
            if (Objects.equals(analysis.sustainedBreakEvenYear(), year.year())) {
                var marker = new XYChart.Data<Number, Number>(year.year(), year.difference());
                StackPane node = new StackPane();
                node.setId("break-even-marker");
                node.getStyleClass().add("sustained-break-even-point");
                Tooltip.install(node, new Tooltip("Sustained Break-Even\n" + pointDetails
                        + "\nThrough comparable period ending " + result.comparisonEndYear()));
                marker.setNode(node);
                node.accessibleTextProperty().unbind();
                node.setAccessibleText("Sustained break-even\n" + pointDetails);
                sustained.getData().add(marker);
            }
        }
        chart.getData().add(difference);
        chart.getData().add(zero);
        if (!sustained.getData().isEmpty()) chart.getData().add(sustained);
    }

    private VBox insightPanel(BreakEvenInsight insight) {
        insightHeadline.setId("break-even-insight-headline");
        insightHeadline.getStyleClass().add("break-even-banner-title");
        Hyperlink toggle = new Hyperlink("Show details");
        toggle.setId("break-even-insight-toggle");
        toggle.setAccessibleText("Show Break-Even Insight details");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox insightHeader = new HBox(8, heading("Break-Even Insight"), spacer, toggle);
        insightHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox panel = new VBox(3, insightHeader, insightHeadline);
        panel.setId("break-even-insight");
        panel.getStyleClass().add("break-even-banner");
        panel.setMinWidth(0);
        String withdrawal = BreakEvenInsightPresentation.withdrawal(insight);

        if (!insight.snapshot().isEmpty()) {

            FlowPane snapshot = new FlowPane(16, 3);
            snapshot.setMinWidth(0);
            Label snapshotHeading = label(BreakEvenInsightPresentation.snapshotHeading(result, insight)
                    .replace("sustained ", "").replace(" — Current − Baseline", ", Current − Baseline:"));
            snapshotHeading.maxWidthProperty().bind(snapshot.widthProperty());
            snapshot.getChildren().add(snapshotHeading);
            for (var metric : new BreakEvenMetric[]{BreakEvenMetric.INVESTABLE_ASSETS,
                    BreakEvenMetric.TOTAL_NET_WORTH, BreakEvenMetric.AFTER_TAX_ESTATE}) {
                var point = insight.snapshot().get(metric);
                if (point == null) continue;
                Label value = label(BreakEvenPresentation.metricName(metric) + ": " + BreakEvenPresentation.signedMoney(point.difference()));
                value.setId("break-even-insight-" + metric.name());
                value.getStyleClass().add(point.difference().signum() < 0 ? "comparison-negative" : "comparison-positive");
                value.maxWidthProperty().bind(snapshot.widthProperty());
                snapshot.getChildren().add(value);
            }
            panel.getChildren().add(snapshot);
        }
        VBox details = new VBox(5);
        details.setMinWidth(0);
        if (!withdrawal.isEmpty()) details.getChildren().add(label(withdrawal));
        details.getChildren().add(label("Social Security compares cumulative benefits. Wealth metrics compare balances, "
                + "which also reflect spending, taxes, investment growth and retained cash."));
        details.getChildren().add(label(result.comparisonEndYear() == null ? "No comparable years are available."
                : "Observed differences, not causal attribution. Totals include only shared years through "
                + (insight.referenceYear() == null ? "comparison end" : insight.referenceYear())
                + ". Sustained means at or above Baseline through " + result.comparisonEndYear()
                + "; no conclusion is made beyond the comparable period."));
        for (var metric : BreakEvenMetric.values()) {
            var point = insight.snapshot().get(metric);
            if (point != null) details.getChildren().add(label(detailValues(BreakEvenPresentation.metricName(metric),
                    point.baselineValue(), point.currentValue(), point.difference())));
        }
        for (var observation : insight.observations()) {
            details.getChildren().add(label(detailValues(BreakEvenInsightPresentation.driverName(observation.driver()),
                    observation.baseline(), observation.current(), observation.difference())));
        }
        details.getChildren().add(label("Gross withdrawals may include RMD cash later redeposited; they are not household consumption. "
                + "Taxes and growth are observed totals, not an additive explanation of the asset gap."));
        if (insight.observations().isEmpty()) details.getChildren().add(label("Annual driver totals unavailable for this comparison."));
        var ssYear = result.metrics().get(BreakEvenMetric.CUMULATIVE_SOCIAL_SECURITY).sustainedBreakEvenYear();
        var survival = ssYear == null ? null : context.survival().get(ssYear);
        if (survival != null) {
            Label probability = label("At SS break-even: " + BreakEvenPresentation.probability(survival) + " modeled probability at least one alive.");
            probability.setTooltip(new Tooltip(context.survivalExplanation()));
            details.getChildren().add(probability);
        }
        details.setId("break-even-insight-details");
        details.setVisible(false);
        details.managedProperty().bind(details.visibleProperty());
        toggle.setOnAction(event -> {
            details.setVisible(!details.isVisible());
            toggle.setText(details.isVisible() ? "Hide details" : "Show details");
            toggle.setAccessibleText(toggle.getText() + " for Break-Even Insight");
        });
        panel.getChildren().add(details);
        return panel;
    }

    private String detailValues(String name, java.math.BigDecimal baseline, java.math.BigDecimal current, java.math.BigDecimal difference) {
        return name + " — Baseline: " + BreakEvenPresentation.money(baseline) + "; Current: "
                + BreakEvenPresentation.money(current) + "; Difference: " + BreakEvenPresentation.signedMoney(difference);
    }

    private VBox summaryCard(BreakEvenMetricResult metric) {
        Label title = label(BreakEvenPresentation.summaryName(metric.metric()));
        title.getStyleClass().add("break-even-card-title");
        Label value = label(BreakEvenPresentation.cardValue(metric));
        value.getStyleClass().add(metric.status() == BreakEvenStatus.BREAK_EVEN_REACHED
                ? "break-even-card-year" : "break-even-card-status");
        Label detail = label(BreakEvenPresentation.cardDetail(result, metric).replace("\n", " · "));
        detail.getStyleClass().add("break-even-card-detail");
        FlowPane outcome = new FlowPane(8, 0);
        outcome.setMinWidth(0);
        outcome.getChildren().add(value);
        value.maxWidthProperty().bind(outcome.widthProperty());
        VBox card = new VBox(2, title, outcome, detail);
        var survival = metric.sustainedBreakEvenYear() == null ? null : context.survival().get(metric.sustainedBreakEvenYear());
        if (survival != null) {
            Label probability = label(BreakEvenPresentation.probability(survival) + " chance at least one alive");
            probability.getStyleClass().add("break-even-card-survival");
            probability.setId("break-even-survival-" + metric.metric().name());
            probability.setTooltip(new Tooltip(context.survivalExplanation()));
            probability.maxWidthProperty().bind(outcome.widthProperty());
            outcome.getChildren().add(probability);
        }
        card.setId("break-even-card-" + metric.metric().name());
        card.getStyleClass().add("break-even-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAccessibleText(title.getText() + ". " + value.getText() + ". " + detail.getText()
                + (survival == null ? "" : ". At least one alive: " + BreakEvenPresentation.probability(survival)));
        Tooltip.install(card, new Tooltip(BreakEvenPresentation.summary(result, metric)));
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void arrangeSummaryCards(double width) {
        int columns = width >= 940 ? 4 : width >= 470 ? 2 : 1;
        if (summaryCards.getColumnConstraints().size() == columns) return;
        summaryCards.getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns);
            column.setMinWidth(0);
            column.setHgrow(Priority.ALWAYS);
            summaryCards.getColumnConstraints().add(column);
        }
        for (int i = 0; i < summaryCards.getChildren().size(); i++) {
            GridPane.setColumnIndex(summaryCards.getChildren().get(i), i % columns);
            GridPane.setRowIndex(summaryCards.getChildren().get(i), i / columns);
        }
    }

    private Label planCard(String title, BreakEvenPlanSummary plan) {
        Label summary = label(title + " — SS claiming ages: " + plan.primary().name() + " "
                + BreakEvenPresentation.age(plan.primary().retirementClaimingAge()) + " · "
                + plan.spouse().name() + " " + BreakEvenPresentation.age(plan.spouse().retirementClaimingAge()));
        summary.getStyleClass().add("break-even-plan-summary");
        return summary;
    }
    private TableColumn<BreakEvenYearResult, String> column(String title, Function<BreakEvenYearResult, String> value) {
        var column = new TableColumn<BreakEvenYearResult, String>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
        return column;
    }
    private static Label heading(String text) {
        Label label = label(text);
        label.getStyleClass().add("section-title");
        return label;
    }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
