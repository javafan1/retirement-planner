package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ResultsSummaryView extends BorderPane {

    private final TextField investmentReturnField =
            new TextField();

    private final TextField inflationField =
            new TextField();

    private final Button applyEconomicButton =
            new Button("Apply Economic Assumptions");

    private final Button applyRothButton =
            new Button("Apply Roth Conversion");

    private final Button applyDeathButton =
            new Button("Apply Death Scenario");

    private final CheckBox rothEnabledCheckBox =
            new CheckBox();

    private final TextField rothStartYearField =
            new TextField();

    private final TextField rothAmountField =
            new TextField();

    private final ComboBox<RothConversionFrequency>
            rothFrequencyComboBox =
            new ComboBox<>();

    private final ComboBox<RothConversionStopRule>
            rothStopRuleComboBox =
            new ComboBox<>();

    private final ComboBox<RothConversionStrategy>
            rothStrategyComboBox =
            new ComboBox<>();

    private final ComboBox<String> deathScenarioComboBox =
            new ComboBox<>();

    private final TextField deathYearField =
            new TextField();

    private final ComboBox<Integer> survivorAgeComboBox =
            new ComboBox<>();

    private final TableView<ProjectionYear> projectionTable =
            new TableView<>();

    private final Label projectionRangeLabel =
            new Label();

    private final Label endingAssetsValue =
            createMetricValueLabel();

    private final Label peakAssetsValue =
            createMetricValueLabel();

    private final Label estateValue =
            createMetricValueLabel();

    private final Label effectiveTaxRateValue =
            createMetricValueLabel();

    private final Label endingAssetsDetail =
            new Label();

    private final Label peakAssetsDetail =
            new Label();

    private final Label estateDetail =
            new Label();

    private final Label taxRateDetail =
            new Label();

    private final LineChart<Number, Number> assetChart;

    private final StackedAreaChart<Number, Number>
            compositionChart;

    private Consumer<PlanningAssumptions>
            economicAssumptionsHandler;

    private Consumer<PlanningAssumptions>
            deathScenarioHandler;

    private RetirementPlan currentPlan;

    private Consumer<RothConversionRequest>
            rothConversionHandler;

    private Consumer<ProjectionYear>
            yearDoubleClickHandler;

    private final ProjectionCsvExporter projectionCsvExporter =
            new ProjectionCsvExporter();

    private final ProjectionPdfExporter projectionPdfExporter =
            new ProjectionPdfExporter();

    private Projection currentProjection;

    public ResultsSummaryView() {

        /*
         * Root styling is now handled by
         * results-summary.css.
         */
        getStyleClass().add(
                "results-summary");

        getStylesheets().add(
                Objects.requireNonNull(
                                getClass()
                                        .getResource(
                                                "/css/results-summary.css"))
                        .toExternalForm());

        setPadding(
                new Insets(12));

        investmentReturnField.setPrefWidth(90);
        inflationField.setPrefWidth(90);

        investmentReturnField.setAlignment(
                Pos.CENTER_RIGHT);

        inflationField.setAlignment(
                Pos.CENTER_RIGHT);

        configurePlaceholderControls();

        deathScenarioComboBox
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateDeathScenarioControls());

        NumberAxis assetXAxis =
                new NumberAxis();

        NumberAxis assetYAxis =
                new NumberAxis();

        assetXAxis.setLabel("Year");
        assetYAxis.setLabel("Portfolio Value");

        assetChart =
                new LineChart<>(
                        assetXAxis,
                        assetYAxis);

        assetChart.setAnimated(false);
        assetChart.setCreateSymbols(false);
        assetChart.setLegendVisible(false);
        assetChart.setTitle(
                "Total Investable Assets");

        assetChart.getStyleClass().add(
                "results-chart");

        NumberAxis compositionXAxis =
                new NumberAxis();

        NumberAxis compositionYAxis =
                new NumberAxis();

        compositionXAxis.setLabel("Year");
        compositionYAxis.setLabel("Balance");

        compositionChart =
                new StackedAreaChart<>(
                        compositionXAxis,
                        compositionYAxis);

        compositionChart.setAnimated(false);
        compositionChart.setCreateSymbols(false);
        compositionChart.setTitle(
                "Asset Composition");

        compositionChart.getStyleClass().add(
                "results-chart");

        projectionTable.setPlaceholder(
                new Label(
                        "No projection available."));

        projectionTable.getStyleClass().add(
                "projection-table");

        projectionTable.setPrefHeight(280);
        projectionTable.setMinHeight(240);
        projectionTable.setMaxHeight(320);

        /*
         * Preserve the existing double-click behavior.
         */
        projectionTable.setRowFactory(tv -> {

            TableRow<ProjectionYear> row =
                    new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (event.getClickCount() == 2
                        && !row.isEmpty()
                        && yearDoubleClickHandler != null) {

                    yearDoubleClickHandler.accept(
                            row.getItem());
                }
            });

            return row;
        });

        createProjectionColumns();

        applyEconomicButton.setOnAction(
                event -> applyEconomicAssumptions());

        applyRothButton.setOnAction(
                event -> applyRothConversion());

        applyDeathButton.setOnAction(
                event -> applyDeathScenario());

        /*
         * Apply button styling.
         *
         * Behavior remains unchanged.
         */
        applyEconomicButton.getStyleClass().addAll(
                "apply-button",
                "apply-economic");

        applyRothButton.getStyleClass().addAll(
                "apply-button",
                "apply-roth");

        applyDeathButton.getStyleClass().addAll(
                "apply-button",
                "apply-death");

        setTop(
                createHeader());

        setCenter(
                createMainContent());

        setRight(
                createAssumptionPanel());

        BorderPane.setMargin(
                getRight(),
                new Insets(
                        0,
                        0,
                        0,
                        12));
    }


    private void applyDeathScenario() {

        if (currentPlan == null
                || deathScenarioHandler == null) {

            return;
        }

        try {

            String selectedScenario =
                    deathScenarioComboBox.getValue();

            if (selectedScenario == null) {
                return;
            }

            DeathScenario deathScenario =
                    parseDeathScenario(
                            selectedScenario);

            PlanningAssumptions current =
                    currentPlan
                            .getPlanningAssumptions();

            DeathScenarioAssumptions existing =
                    current
                            .getDeathScenarioAssumptions();

            Integer deathYear = null;

            Integer survivorClaimingAge = null;

            if (deathScenario
                    != DeathScenario.BOTH_SURVIVE) {

                deathYear =
                        Integer.parseInt(
                                deathYearField
                                        .getText()
                                        .trim());

                survivorClaimingAge =
                        survivorAgeComboBox
                                .getValue();

                if (survivorClaimingAge == null) {

                    throw new IllegalArgumentException(
                            "Survivor claiming age is required.");
                }
            }

            DeathScenarioAssumptions updatedDeath =
                    new DeathScenarioAssumptions(
                            deathScenario,
                            deathYear,
                            survivorClaimingAge,
                            existing
                                    .getPostDeathExpenseFactor());

            PlanningAssumptions updated =
                    new PlanningAssumptions(
                            current
                                    .getEconomicAssumptions(),
                            current
                                    .getTaxAssumptions(),
                            current
                                    .getWithdrawalAssumptions(),
                            updatedDeath,
                            current
                                    .getProjectionLengthYears(),
                            current
                                    .getProjectionStartDate());

            deathScenarioHandler.accept(
                    updated);

        } catch (Exception ex) {

            showError(
                    "Please enter valid death scenario values.");
        }
    }


    private void updateDeathScenarioControls() {

        boolean bothSurvive =
                "Both Survive".equals(
                        deathScenarioComboBox.getValue());

        deathYearField.setDisable(
                bothSurvive);

        survivorAgeComboBox.setDisable(
                bothSurvive);

        if (bothSurvive) {

            deathYearField.clear();

            survivorAgeComboBox
                    .getSelectionModel()
                    .clearSelection();
        }

        /*
         * Both Survive is a valid change and must be
         * apply-able.
         */
        applyDeathButton.setDisable(
                deathScenarioComboBox.getValue() == null);
    }


    private DeathScenario parseDeathScenario(
            String value) {

        return switch (value) {

            case "Primary Dies" ->
                    DeathScenario.PRIMARY_DIES;

            case "Spouse Dies" ->
                    DeathScenario.SPOUSE_DIES;

            case "Both Survive" ->
                    DeathScenario.BOTH_SURVIVE;

            default ->
                    throw new IllegalArgumentException(
                            "Unknown death scenario: "
                                    + value);
        };
    }


    private void configurePlaceholderControls() {

        /*
         * Roth strategy options.
         */
        rothStrategyComboBox
                .getItems()
                .addAll(
                        RothConversionStrategy.FIXED_AMOUNT,
                        RothConversionStrategy.FILL_12_PERCENT_BRACKET,
                        RothConversionStrategy.FILL_22_PERCENT_BRACKET,
                        RothConversionStrategy.FILL_24_PERCENT_BRACKET);

        /*
         * User-friendly strategy labels.
         */
        rothStrategyComboBox.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(
                            RothConversionStrategy strategy) {

                        if (strategy == null) {
                            return "";
                        }

                        return switch (strategy) {

                            case FIXED_AMOUNT ->
                                    "Fixed Amount";

                            case FILL_12_PERCENT_BRACKET ->
                                    "Fill 12% Bracket";

                            case FILL_22_PERCENT_BRACKET ->
                                    "Fill 22% Bracket";

                            case FILL_24_PERCENT_BRACKET ->
                                    "Fill 24% Bracket";
                        };
                    }

                    @Override
                    public RothConversionStrategy fromString(
                            String value) {

                        return switch (value) {

                            case "Fixed Amount" ->
                                    RothConversionStrategy
                                            .FIXED_AMOUNT;

                            case "Fill 12% Bracket" ->
                                    RothConversionStrategy
                                            .FILL_12_PERCENT_BRACKET;

                            case "Fill 22% Bracket" ->
                                    RothConversionStrategy
                                            .FILL_22_PERCENT_BRACKET;

                            case "Fill 24% Bracket" ->
                                    RothConversionStrategy
                                            .FILL_24_PERCENT_BRACKET;

                            default ->
                                    null;
                        };
                    }
                });

        rothFrequencyComboBox
                .getItems()
                .addAll(
                        RothConversionFrequency.values());

        rothStopRuleComboBox
                .getItems()
                .addAll(
                        RothConversionStopRule.values());

        rothEnabledCheckBox.setSelected(false);

        rothStrategyComboBox
                .getSelectionModel()
                .select(
                        RothConversionStrategy.FIXED_AMOUNT);

        rothFrequencyComboBox
                .getSelectionModel()
                .selectFirst();

        rothStopRuleComboBox
                .getSelectionModel()
                .selectFirst();

        rothStartYearField.setPromptText(
                "YYYY");

        rothAmountField.setPromptText(
                "Amount");

        applyRothButton.setOnAction(
                event -> applyRothConversion());

        updateRothControls();

        /*
         * Death scenario values.
         */
        deathScenarioComboBox
                .getItems()
                .addAll(
                        "Both Survive",
                        "Primary Dies",
                        "Spouse Dies");

        deathScenarioComboBox
                .getSelectionModel()
                .selectFirst();

        deathYearField.setPromptText(
                "YYYY");

        survivorAgeComboBox
                .getItems()
                .addAll(
                        62,
                        63,
                        64,
                        65,
                        66,
                        67,
                        68,
                        69,
                        70);

        survivorAgeComboBox.setPromptText(
                "Age");

        applyDeathButton.setDisable(
                true);

        updateDeathScenarioControls();
    }


    private void updateRothControls() {

        boolean enabled =
                rothEnabledCheckBox.isSelected();

        RothConversionStrategy strategy =
                rothStrategyComboBox.getValue();

        boolean fixedAmount =
                strategy ==
                        RothConversionStrategy.FIXED_AMOUNT;

        rothStrategyComboBox.setDisable(
                !enabled);

        rothStartYearField.setDisable(
                !enabled);

        rothFrequencyComboBox.setDisable(
                !enabled);

        rothStopRuleComboBox.setDisable(
                !enabled);

        rothAmountField.setDisable(
                !enabled || !fixedAmount);

        applyRothButton.setDisable(
                !enabled);
    }


    private HBox createHeader() {

        Label title =
                new Label(
                        "Results");

        title.getStyleClass().add(
                "results-title");

        projectionRangeLabel.getStyleClass().add(
                "results-subtitle");

        VBox titleBox =
                new VBox(
                        3,
                        title,
                        projectionRangeLabel);

        Button recalculateButton =
                new Button(
                        "⟳  Recalculate Projection");

        recalculateButton.getStyleClass().add(
                "results-action-button");

        recalculateButton.setOnAction(
                event -> {

                    if (economicAssumptionsHandler != null
                            && currentPlan != null) {

                        applyEconomicAssumptions();
                    }
                });

        Button exportButton =
                new Button("Export");

        exportButton.getStyleClass().add(
                "results-action-button");

        exportButton.setDisable(false);

        exportButton.setOnAction(
                event -> showExportDialog());

        HBox actions =
                new HBox(
                        10,
                        recalculateButton,
                        exportButton);

        actions.setAlignment(
                Pos.CENTER_RIGHT);

        HBox header =
                new HBox(
                        titleBox,
                        actions);

        HBox.setHgrow(
                titleBox,
                Priority.ALWAYS);

        header.setAlignment(
                Pos.CENTER_LEFT);

        header.setPadding(
                new Insets(
                        0,
                        0,
                        12,
                        0));

        return header;
    }


    private VBox createMainContent() {

        VBox content =
                new VBox(12);

        content.getChildren().add(
                createMetricCards());

        VBox projectionSection =
                createProjectionSection();

        content.getChildren().add(
                projectionSection);

        HBox charts =
                new HBox(
                        12,
                        createChartCard(assetChart),
                        createChartCard(compositionChart));

        HBox.setHgrow(
                charts.getChildren().get(0),
                Priority.ALWAYS);

        HBox.setHgrow(
                charts.getChildren().get(1),
                Priority.ALWAYS);

        content.getChildren().add(
                charts);

        VBox.setVgrow(
                projectionSection,
                Priority.NEVER);

        return content;
    }

    private HBox createMetricCards() {

        HBox cards =
                new HBox(10);

        cards.getChildren().addAll(

                createMetricCard(
                        "Ending Investable Assets",
                        FontAwesomeSolid.CHART_LINE,
                        "metric-blue",
                        "metric-icon-blue",
                        endingAssetsValue,
                        endingAssetsDetail),

                createMetricCard(
                        "Peak Investable Assets",
                        FontAwesomeSolid.SHIELD_ALT,
                        "metric-green",
                        "metric-icon-green",
                        peakAssetsValue,
                        peakAssetsDetail),

                createMetricCard(
                        "After-Tax Estate",
                        FontAwesomeSolid.USERS,
                        "metric-purple",
                        "metric-icon-purple",
                        estateValue,
                        estateDetail),

                createMetricCard(
                        "Effective Tax Rate",
                        FontAwesomeSolid.PERCENT,
                        "metric-orange",
                        "metric-icon-orange",
                        effectiveTaxRateValue,
                        taxRateDetail));

        for (var card :
                cards.getChildren()) {

            HBox.setHgrow(
                    card,
                    Priority.ALWAYS);
        }

        return cards;
    }


    private VBox createMetricCard(
            String title,
            FontAwesomeSolid iconCode,
            String valueColorClass,
            String iconColorClass,
            Label value,
            Label detail) {

        FontIcon icon =
                new FontIcon(iconCode);

        icon.getStyleClass().addAll(
                "metric-icon",
                iconColorClass);

        Label titleLabel =
                new Label(title);

        titleLabel.getStyleClass().add(
                "metric-title");

        value.getStyleClass().add(
                "metric-value");

        value.getStyleClass().add(
                valueColorClass);

        detail.getStyleClass().add(
                "metric-detail");

        VBox text =
                new VBox(
                        4,
                        titleLabel,
                        value,
                        detail);

        HBox content =
                new HBox(
                        12,
                        icon,
                        text);

        content.setAlignment(
                Pos.CENTER_LEFT);

        VBox box =
                new VBox(
                        content);

        box.getStyleClass().add(
                "metric-card");

        box.setMinWidth(170);

        return box;
    }


    private VBox createProjectionSection() {

        Label title =
                new Label(
                        "Projection Summary");

        title.getStyleClass().add(
                "section-title");

        Label subtitle =
                new Label(
                        "(All values in today's dollars)");

        subtitle.getStyleClass().add(
                "section-subtitle");

        HBox heading =
                new HBox(
                        6,
                        title,
                        subtitle);

        heading.setAlignment(
                Pos.CENTER_LEFT);

        /*
         * The TableView owns the vertical scrollbar.
         */
        VBox section =
                new VBox(
                        8,
                        heading,
                        projectionTable);

        section.getStyleClass().add(
                "summary-section");

        section.setPadding(
                new Insets(12));

        return section;
    }


    private VBox createAssumptionPanel() {

        VBox panel =
                new VBox(12);

        panel.setPrefWidth(320);
        panel.setMinWidth(300);

        Label heading =
                new Label(
                        "KEY ASSUMPTIONS");

        heading.getStyleClass().add(
                "assumptions-title");

        panel.getChildren().add(
                heading);

        panel.getChildren().add(
                createEconomicPanel());

        panel.getChildren().add(
                createRothPanel());

        panel.getChildren().add(
                createDeathPanel());

        Label note =
                new Label(
                        "Changes apply to the current plan. "
                                + "The projection is recalculated when "
                                + "an Apply button is used.");

        note.setWrapText(true);

        note.getStyleClass().add(
                "assumption-note");

        panel.getChildren().add(
                note);

        return panel;
    }


    private VBox createEconomicPanel() {

        Label heading =
                createPanelHeading(
                        "ECONOMIC ASSUMPTIONS",
                        FontAwesomeSolid.CHART_LINE,
                        "assumption-title-blue");

        GridPane grid =
                createTwoColumnGrid();

        Label investmentLabel =
                new Label(
                        "Investment Return");

        investmentLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                investmentLabel,
                0,
                0);

        investmentReturnField.getStyleClass().add(
                "assumption-field");

        grid.add(
                investmentReturnField,
                1,
                0);

        Label inflationLabel =
                new Label(
                        "General Inflation");

        inflationLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                inflationLabel,
                0,
                1);

        inflationField.getStyleClass().add(
                "assumption-field");

        grid.add(
                inflationField,
                1,
                1);

        return createPanel(
                heading,
                grid,
                applyEconomicButton,
                "assumption-card");
    }


    private VBox createRothPanel() {

        Label heading =
                createPanelHeading(
                        "ROTH CONVERSION",
                        FontAwesomeSolid.PERCENT,
                        "assumption-title-green");

        GridPane grid =
                createTwoColumnGrid();

        Label enabledLabel =
                new Label("Enabled");

        enabledLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                enabledLabel,
                0,
                0);

        grid.add(
                rothEnabledCheckBox,
                1,
                0);

        Label strategyLabel =
                new Label("Strategy");

        strategyLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                strategyLabel,
                0,
                1);

        grid.add(
                rothStrategyComboBox,
                1,
                1);

        Label startYearLabel =
                new Label("Start Year");

        startYearLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                startYearLabel,
                0,
                2);

        rothStartYearField.getStyleClass().add(
                "assumption-field");

        grid.add(
                rothStartYearField,
                1,
                2);

        Label amountLabel =
                new Label("Amount");

        amountLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                amountLabel,
                0,
                3);

        rothAmountField.getStyleClass().add(
                "assumption-field");

        grid.add(
                rothAmountField,
                1,
                3);

        Label frequencyLabel =
                new Label("Frequency");

        frequencyLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                frequencyLabel,
                0,
                4);

        grid.add(
                rothFrequencyComboBox,
                1,
                4);

        Label stopRuleLabel =
                new Label("Stop Rule");

        stopRuleLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                stopRuleLabel,
                0,
                5);

        grid.add(
                rothStopRuleComboBox,
                1,
                5);

        rothStrategyComboBox.setMaxWidth(
                Double.MAX_VALUE);

        rothFrequencyComboBox.setMaxWidth(
                Double.MAX_VALUE);

        rothStopRuleComboBox.setMaxWidth(
                Double.MAX_VALUE);

        rothEnabledCheckBox
                .selectedProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateRothControls());

        rothStrategyComboBox
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateRothControls());

        return createPanel(
                heading,
                grid,
                applyRothButton,
                "assumption-card");
    }


    private VBox createDeathPanel() {

        Label heading =
                createPanelHeading(
                        "DEATH SCENARIO",
                        FontAwesomeSolid.USERS,
                        "assumption-title-purple");


        GridPane grid =
                createTwoColumnGrid();

        Label scenarioLabel =
                new Label("Scenario");

        scenarioLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                scenarioLabel,
                0,
                0);

        grid.add(
                deathScenarioComboBox,
                1,
                0);

        Label deathYearLabel =
                new Label("Death Year");

        deathYearLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                deathYearLabel,
                0,
                1);

        deathYearField.getStyleClass().add(
                "assumption-field");

        grid.add(
                deathYearField,
                1,
                1);

        Label survivorAgeLabel =
                new Label(
                        "Survivor Claiming Age");

        survivorAgeLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                survivorAgeLabel,
                0,
                2);

        grid.add(
                survivorAgeComboBox,
                1,
                2);

        deathScenarioComboBox.setMaxWidth(
                Double.MAX_VALUE);

        survivorAgeComboBox.setMaxWidth(
                Double.MAX_VALUE);

        return createPanel(
                heading,
                grid,
                applyDeathButton,
                "assumption-card");
    }


    private VBox createPanel(
            Label heading,
            GridPane grid,
            Button button,
            String styleClass) {

        VBox box =
                new VBox(
                        10,
                        heading,
                        grid,
                        button);

        box.getStyleClass().add(
                styleClass);

        button.setMaxWidth(
                Double.MAX_VALUE);

        return box;
    }


    private GridPane createTwoColumnGrid() {

        GridPane grid =
                new GridPane();

        grid.setHgap(8);
        grid.setVgap(8);

        ColumnConstraints labelColumn =
                new ColumnConstraints();

        ColumnConstraints valueColumn =
                new ColumnConstraints();

        labelColumn.setHgrow(
                Priority.SOMETIMES);

        valueColumn.setHgrow(
                Priority.ALWAYS);

        grid.getColumnConstraints().addAll(
                labelColumn,
                valueColumn);

        return grid;
    }


    private Label createPanelHeading(
            String text,
            FontAwesomeSolid iconCode,
            String colorClass) {

        FontIcon icon =
                new FontIcon(iconCode);

        icon.getStyleClass().add(
                "sidebar-section-icon");

        icon.getStyleClass().add(
                colorClass);

        Label label =
                new Label(text);

        label.setGraphic(icon);

        label.setGraphicTextGap(8);

        label.getStyleClass().add(
                "assumption-title");

        return label;
    }

    private VBox createChartCard(
            javafx.scene.Node chart) {

        VBox box =
                new VBox(chart);

        box.getStyleClass().add(
                "chart-card");

        box.setPadding(
                new Insets(10));

        VBox.setVgrow(
                chart,
                Priority.ALWAYS);

        return box;
    }


    private void createProjectionColumns() {

        TableColumn<ProjectionYear, Integer>
                yearColumn =
                new TableColumn<>("Year");

        yearColumn.setCellValueFactory(
                data ->
                        new ReadOnlyObjectWrapper<>(
                                data.getValue()
                                        .getCalendarYear()));

        TableColumn<ProjectionYear, Integer>
                ageColumn =
                new TableColumn<>("Age");

        ageColumn.setCellValueFactory(
                data ->
                        new ReadOnlyObjectWrapper<>(
                                data.getValue()
                                        .getPrimaryPersonAge()));

        TableColumn<ProjectionYear, BigDecimal>
                beginningAssetsColumn =
                moneyColumn(
                        "Beginning Assets",
                        ProjectionYear::
                                getBeginningInvestableAssets);

        TableColumn<ProjectionYear, BigDecimal>
                growthColumn =
                moneyColumn(
                        "Investment Growth",
                        ProjectionYear::
                                getInvestmentGrowth);

        TableColumn<ProjectionYear, BigDecimal>
                netCashFlowColumn =
                moneyColumn(
                        "Net Cash Flow",
                        ProjectionYear::
                                getNetCashFlow);

        TableColumn<ProjectionYear, BigDecimal>
                endingAssetsColumn =
                moneyColumn(
                        "Ending Assets",
                        ProjectionYear::
                                getEndingInvestableAssets);

        TableColumn<ProjectionYear, BigDecimal>
                federalTaxColumn =
                moneyColumn(
                        "Federal Tax",
                        ProjectionYear::
                                getFederalIncomeTax);

        TableColumn<ProjectionYear, BigDecimal>
                estateColumn =
                moneyColumn(
                        "Estate Value",
                        ProjectionYear::
                                getAfterTaxEstateValue);

        projectionTable.getColumns().addAll(
                yearColumn,
                ageColumn,
                beginningAssetsColumn,
                netCashFlowColumn,
                growthColumn,
                endingAssetsColumn,
                federalTaxColumn,
                estateColumn);

        projectionTable.setColumnResizePolicy(
                TableView
                        .CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }


    private TableColumn<ProjectionYear, BigDecimal>
    moneyColumn(
            String title,
            java.util.function.Function<
                    ProjectionYear,
                    BigDecimal> valueFunction) {

        TableColumn<ProjectionYear, BigDecimal>
                column =
                new TableColumn<>(title);

        column.setCellValueFactory(
                data ->
                        new ReadOnlyObjectWrapper<>(
                                valueFunction.apply(
                                        data.getValue())));

        column.setCellFactory(
                ignored ->
                        new javafx.scene.control.TableCell<>() {

                            @Override
                            protected void updateItem(
                                    BigDecimal value,
                                    boolean empty) {

                                super.updateItem(
                                        value,
                                        empty);

                                setText(
                                        empty || value == null
                                                ? null
                                                : UIFormatters
                                                .money(value));
                            }
                        });

        return column;
    }


    public void load(
            RetirementPlan plan,
            Projection projection) {

        currentPlan = plan;
        currentProjection = projection;

        loadAssumptions(plan);

        loadRothConversion(plan);



        if (projection == null
                || projection.getYears().isEmpty()) {

            projectionTable
                    .getItems()
                    .clear();

            projectionRangeLabel
                    .setText(
                            "No projection available.");

            clearMetrics();
            clearCharts();

            return;
        }

        List<ProjectionYear> years =
                projection.getYears();

        projectionTable
                .getItems()
                .setAll(years);

        ProjectionYear first =
                years.get(0);

        ProjectionYear last =
                years.get(
                        years.size() - 1);

        projectionRangeLabel.setText(
                "Projection: "
                        + first.getCalendarYear()
                        + " - "
                        + last.getCalendarYear()
                        + "  |  "
                        + years.size()
                        + " years");

        updateMetrics(years);
        updateCharts(years);
    }


    private void loadAssumptions(
            RetirementPlan plan) {

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        EconomicAssumptions economic =
                assumptions
                        .getEconomicAssumptions();

        investmentReturnField.setText(
                percentForField(
                        economic
                                .getExpectedAnnualInvestmentReturn()));

        inflationField.setText(
                percentForField(
                        economic
                                .getGeneralInflationRate()));

        DeathScenarioAssumptions death =
                assumptions
                        .getDeathScenarioAssumptions();

        deathScenarioComboBox
                .getSelectionModel()
                .select(
                        deathScenarioDisplay(
                                death.getDeathScenario()));

        if (death.getDeathYear() != null) {

            deathYearField.setText(
                    death.getDeathYear()
                            .toString());

        } else {

            deathYearField.clear();
        }

        if (death.getSurvivorClaimingAge() != null) {

            survivorAgeComboBox
                    .getSelectionModel()
                    .select(
                            death.getSurvivorClaimingAge());

        } else {

            survivorAgeComboBox
                    .getSelectionModel()
                    .clearSelection();
        }

        updateDeathScenarioControls();
    }


    private void updateMetrics(
            List<ProjectionYear> years) {

        ProjectionYear first =
                years.get(0);

        ProjectionYear last =
                years.get(
                        years.size() - 1);

        ProjectionYear peak =
                years.stream()
                        .max(
                                java.util.Comparator.comparing(
                                        ProjectionYear::
                                                getEndingInvestableAssets))
                        .orElse(last);

        endingAssetsValue.setText(
                UIFormatters.money(
                        last.getEndingInvestableAssets()));

        endingAssetsDetail.setText(
                "Year "
                        + last.getCalendarYear()
                        + " • Age "
                        + last.getPrimaryPersonAge());

        peakAssetsValue.setText(
                UIFormatters.money(
                        peak.getEndingInvestableAssets()));

        peakAssetsDetail.setText(
                "Year "
                        + peak.getCalendarYear()
                        + " • Age "
                        + peak.getPrimaryPersonAge());

        estateValue.setText(
                UIFormatters.money(
                        last.getAfterTaxEstateValue()));

        estateDetail.setText(
                "Year "
                        + last.getCalendarYear()
                        + " • After estimated taxes");

        BigDecimal averageTaxRate =
                years.stream()
                        .map(
                                ProjectionYear::
                                        getCombinedEffectiveTaxRate)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add)
                        .divide(
                                BigDecimal.valueOf(
                                        years.size()),
                                6,
                                RoundingMode.HALF_UP);

        effectiveTaxRateValue.setText(
                UIFormatters.percent(
                        averageTaxRate));

        taxRateDetail.setText(
                "Average over projection");
    }


    private void updateCharts(
            List<ProjectionYear> years) {

        assetChart.getData().clear();
        compositionChart.getData().clear();

        int firstYear =
                years.get(0)
                        .getCalendarYear();

        int lastYear =
                years.get(years.size() - 1)
                        .getCalendarYear();

        NumberAxis assetXAxis =
                (NumberAxis) assetChart.getXAxis();

        NumberAxis compositionXAxis =
                (NumberAxis) compositionChart.getXAxis();

        assetXAxis.setAutoRanging(false);
        assetXAxis.setLowerBound(firstYear);
        assetXAxis.setUpperBound(lastYear);
        assetXAxis.setTickUnit(5);

        compositionXAxis.setAutoRanging(false);
        compositionXAxis.setLowerBound(firstYear);
        compositionXAxis.setUpperBound(lastYear);
        compositionXAxis.setTickUnit(5);

        XYChart.Series<Number, Number>
                assetSeries =
                new XYChart.Series<>();

        assetSeries.setName(
                "Total Investable Assets");

        XYChart.Series<Number, Number>
                taxDeferredSeries =
                new XYChart.Series<>();

        taxDeferredSeries.setName(
                "Tax Deferred");

        XYChart.Series<Number, Number>
                rothSeries =
                new XYChart.Series<>();

        rothSeries.setName(
                "Roth");

        XYChart.Series<Number, Number>
                taxableSeries =
                new XYChart.Series<>();

        taxableSeries.setName(
                "Taxable / Cash");

        for (ProjectionYear year : years) {

            int calendarYear =
                    year.getCalendarYear();

            assetSeries.getData().add(
                    new XYChart.Data<>(
                            calendarYear,
                            year
                                    .getEndingInvestableAssets()
                                    .doubleValue()));

            taxDeferredSeries.getData().add(
                    new XYChart.Data<>(
                            calendarYear,
                            getEndingBalanceByAssetType(
                                    year,
                                    ProjectionAssetType
                                            .TAX_DEFERRED)
                                    .doubleValue()));

            rothSeries.getData().add(
                    new XYChart.Data<>(
                            calendarYear,
                            getEndingBalanceByAssetType(
                                    year,
                                    ProjectionAssetType
                                            .ROTH)
                                    .doubleValue()));

            BigDecimal taxableCash =
                    getEndingBalanceByAssetType(
                            year,
                            ProjectionAssetType
                                    .TAXABLE)
                            .add(
                                    year
                                            .getUnallocatedCash());

            taxableSeries.getData().add(
                    new XYChart.Data<>(
                            calendarYear,
                            taxableCash.doubleValue()));
        }

        assetChart.getData().add(
                assetSeries);

        compositionChart.getData().addAll(
                taxableSeries,
                rothSeries,
                taxDeferredSeries);
    }


    private BigDecimal getEndingBalanceByAssetType(
            ProjectionYear year,
            ProjectionAssetType assetType) {

        return year
                .getEndingAccountSnapshots()
                .stream()
                .filter(snapshot ->
                        snapshot.getAccount()
                                .getProjectionAssetType()
                                == assetType)
                .map(
                        ProjectedAccountSnapshot::
                                getEndingBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }


    private void clearMetrics() {

        endingAssetsValue.setText("-");
        peakAssetsValue.setText("-");
        estateValue.setText("-");
        effectiveTaxRateValue.setText("-");

        endingAssetsDetail.setText("");
        peakAssetsDetail.setText("");
        estateDetail.setText("");
        taxRateDetail.setText("");
    }


    private void clearCharts() {

        assetChart.getData().clear();
        compositionChart.getData().clear();
    }


    private void applyEconomicAssumptions() {

        if (currentPlan == null
                || economicAssumptionsHandler == null) {

            return;
        }

        try {

            BigDecimal investmentReturn =
                    parsePercent(
                            investmentReturnField
                                    .getText());

            BigDecimal generalInflation =
                    parsePercent(
                            inflationField
                                    .getText());

            PlanningAssumptions current =
                    currentPlan
                            .getPlanningAssumptions();

            EconomicAssumptions existingEconomic =
                    current
                            .getEconomicAssumptions();

            EconomicAssumptions updatedEconomic =
                    new EconomicAssumptions(
                            investmentReturn,
                            generalInflation,
                            existingEconomic
                                    .getHealthcareInflationRate(),
                            existingEconomic
                                    .getSocialSecurityColaRate());

            PlanningAssumptions updated =
                    new PlanningAssumptions(
                            updatedEconomic,
                            current
                                    .getTaxAssumptions(),
                            current
                                    .getWithdrawalAssumptions(),
                            current
                                    .getDeathScenarioAssumptions(),
                            current
                                    .getProjectionLengthYears(),
                            current
                                    .getProjectionStartDate());

            economicAssumptionsHandler.accept(
                    updated);

        } catch (Exception ex) {

            showError(
                    "Please enter valid economic assumption values.");
        }
    }


    private BigDecimal parsePercent(
            String text) {

        String value =
                text
                        .trim()
                        .replace("%", "");

        return new BigDecimal(value)
                .divide(
                        new BigDecimal("100"),
                        8,
                        RoundingMode.HALF_UP);
    }


    private String percentForField(
            BigDecimal decimalRate) {

        return decimalRate
                .multiply(
                        new BigDecimal("100"))
                .setScale(
                        2,
                        RoundingMode.HALF_UP)
                .toPlainString();
    }


    private String deathScenarioDisplay(
            Enum<?> scenario) {

        return switch (scenario.name()) {

            case "PRIMARY_DIES" ->
                    "Primary Dies";

            case "SPOUSE_DIES" ->
                    "Spouse Dies";

            default ->
                    "Both Survive";
        };
    }


    private void showError(
            String message) {

        /*
         * Existing validation/status behavior remains
         * unchanged for now.
         */
        System.err.println(message);
    }


    private static Label createMetricValueLabel() {

        Label label =
                new Label("-");

        /*
         * Font/color now comes from CSS.
         */
        return label;
    }


    public void setOnEconomicAssumptionsApply(
            Consumer<PlanningAssumptions> handler) {

        this.economicAssumptionsHandler =
                handler;
    }


    public void setOnDeathScenarioApply(
            Consumer<PlanningAssumptions> handler) {

        this.deathScenarioHandler =
                handler;
    }


    private void applyRothConversion() {

        if (currentPlan == null
                || rothConversionHandler == null) {

            return;
        }

        try {

            boolean enabled =
                    rothEnabledCheckBox.isSelected();

            RothConversionStrategy strategy =
                    rothStrategyComboBox.getValue();

            if (strategy == null) {

                throw new IllegalArgumentException(
                        "Roth conversion strategy is required.");
            }

            int startYear =
                    Integer.parseInt(
                            rothStartYearField
                                    .getText()
                                    .trim());

            BigDecimal annualAmount =
                    BigDecimal.ZERO;

            /*
             * Only fixed-dollar conversions require
             * an annual amount from the user.
             */
            if (strategy ==
                    RothConversionStrategy.FIXED_AMOUNT) {

                annualAmount =
                        parseMoney(
                                rothAmountField
                                        .getText());
            }

            RothConversionFrequency frequency =
                    rothFrequencyComboBox.getValue();

            RothConversionStopRule stopRule =
                    rothStopRuleComboBox.getValue();

            if (frequency == null) {

                throw new IllegalArgumentException(
                        "Roth conversion frequency is required.");
            }

            RothConversionRequest request =
                    new RothConversionRequest(
                            enabled,
                            startYear,
                            annualAmount,
                            stopRule,
                            strategy,
                            frequency);

            rothConversionHandler.accept(
                    request);

        } catch (Exception ex) {

            ex.printStackTrace();

            showError(
                    "Please enter valid Roth conversion values.");
        }
    }


    public void setOnRothConversionApply(
            Consumer<RothConversionRequest> handler) {

        this.rothConversionHandler =
                handler;
    }


    private BigDecimal parseMoney(
            String text) {

        return new BigDecimal(
                text
                        .replace("$", "")
                        .replace(",", "")
                        .trim());
    }


    public void setOnYearDoubleClick(
            Consumer<ProjectionYear> handler) {

        this.yearDoubleClickHandler =
                handler;
    }

    private void exportProjectionCsv() {

        if (currentProjection == null
                || currentProjection.isEmpty()) {

            showError(
                    "There is no projection available to export.");

            return;
        }

        FileChooser fileChooser =
                new FileChooser();

        fileChooser.setTitle(
                "Export Retirement Projection");

        fileChooser.setInitialFileName(
                "RetirementProjection.csv");

        FileChooser.ExtensionFilter csvFilter =
                new FileChooser.ExtensionFilter(
                        "CSV Files (*.csv)",
                        "*.csv");

        fileChooser.getExtensionFilters().add(
                csvFilter);

        Window window =
                getScene() == null
                        ? null
                        : getScene().getWindow();

        File selectedFile =
                fileChooser.showSaveDialog(window);

        if (selectedFile == null) {
            return;
        }

        Path file =
                selectedFile.toPath();

        /*
         * Make sure the user gets the expected
         * .csv extension.
         */
        if (!file
                .getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".csv")) {

            file =
                    file.resolveSibling(
                            file.getFileName()
                                    .toString()
                                    + ".csv");
        }

        try {

            projectionCsvExporter.export(
                    currentProjection,
                    file);

            showInformation(
                    "Projection exported successfully.",
                    file.toString());

        } catch (IOException ex) {

            ex.printStackTrace();

            showError(
                    "The projection could not be exported.");
        }
    }

    private void showInformation(
            String message,
            String details) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION);

        alert.setTitle(
                "Export Complete");

        alert.setHeaderText(
                message);

        alert.setContentText(
                details);

        alert.showAndWait();
    }

    private void showExportDialog() {

        Alert alert =
                new Alert(Alert.AlertType.NONE);

        alert.setTitle(
                "Export Projection");

        alert.setHeaderText(
                "Choose Export Format");

        alert.setContentText(
                "Select the format for your retirement projection.");

        ButtonType csvButton =
                new ButtonType("CSV");

        ButtonType pdfButton =
                new ButtonType("PDF");

        ButtonType cancelButton =
                new ButtonType(
                        "Cancel",
                        ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(
                csvButton,
                pdfButton,
                cancelButton);

        Optional<ButtonType> result =
                alert.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        if (result.get() == csvButton) {

            exportProjectionCsv();

        } else if (result.get() == pdfButton) {

            exportProjectionPdf();
        }
    }

    private void exportProjectionPdf() {

        if (currentProjection == null
                || currentProjection.isEmpty()) {

            showError(
                    "There is no projection available to export.");

            return;
        }

        FileChooser fileChooser =
                new FileChooser();

        fileChooser.setTitle(
                "Export Retirement Projection");

        fileChooser.setInitialFileName(
                "RetirementProjection.pdf");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "PDF Files (*.pdf)",
                        "*.pdf"));

        Window window =
                getScene() == null
                        ? null
                        : getScene().getWindow();

        File selectedFile =
                fileChooser.showSaveDialog(window);

        if (selectedFile == null) {
            return;
        }

        Path file =
                selectedFile.toPath();

        if (!file
                .getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".pdf")) {

            file =
                    file.resolveSibling(
                            file.getFileName()
                                    .toString()
                                    + ".pdf");
        }

        try {

            projectionPdfExporter.export(
                    currentPlan,
                    currentProjection,
                    file);

            showInformation(
                    "Projection exported successfully.",
                    file.toString());

        } catch (IOException ex) {

            ex.printStackTrace();

            showError(
                    "The projection could not be exported.");
        }
    }

    private void loadRothConversion(
            RetirementPlan plan) {

        RothConversionRequest request =
                plan.getRothConversionRequest();

        if (request == null) {

            rothEnabledCheckBox.setSelected(false);

            rothStrategyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStrategy.FIXED_AMOUNT);

            rothStartYearField.clear();
            rothAmountField.clear();

            rothFrequencyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionFrequency.ONE_TIME);

            rothStopRuleComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStopRule.FIRST_HOUSEHOLD_RMD);

            updateRothControls();

            return;
        }

        rothEnabledCheckBox.setSelected(
                request.isEnabled());

        rothStrategyComboBox
                .getSelectionModel()
                .select(
                        request.getStrategy());

        rothStartYearField.setText(
                Integer.toString(
                        request.getStartYear()));

        rothAmountField.setText(
                request.getAnnualAmount()
                        .toPlainString());

        rothFrequencyComboBox
                .getSelectionModel()
                .select(
                        request.getFrequency());

        if (request.getStopRule() != null) {

            rothStopRuleComboBox
                    .getSelectionModel()
                    .select(
                            request.getStopRule());
        } else {

            rothStopRuleComboBox
                    .getSelectionModel()
                    .clearSelection();
        }

        updateRothControls();
    }
}