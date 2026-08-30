package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.app.export.ProjectionPdfExporter;
import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;
import com.daviddunn.retirementplanner.app.export.ProjectionCsvExporter;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitCalculator;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitStartDateCalculator;
import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.domain.baseline.ProjectionComparison;

import javafx.geometry.HPos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.util.converter.NumberStringConverter;
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
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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

    private final ApplicationController controller;

    private final TextField investmentReturnField =
            new TextField();

    private final TextField inflationField =
            new TextField();

    private final TextField futureFederalMarginalRateChangeField =
            new TextField();

    private final TextField futureFederalMarginalRateEffectiveYearField =
            new TextField();

    private final Button applyEconomicButton =
            new Button("Apply Economic Assumptions");

    private final Button applyRothButton =
            new Button("Apply Roth Conversion");

    private final Button applyDeathButton =
            new Button("Apply Death Scenario");

    private final Button applySocialSecurityButton =
            new Button("Apply Social Security");

    private final CheckBox rothEnabledCheckBox =
            new CheckBox();

    private final TextField rothStartYearField =
            new TextField();

    private final TextField rothAmountField =
            new TextField();

    private final Label rothAmountLabel =
            new Label("Amount");

    private final TextField rothTargetTaxableIncomeField =
            new TextField();

    private final Label rothTargetTaxableIncomeLabel =
            new Label("Target Taxable Income");

    private final ComboBox<RothConversionFrequency>
            rothFrequencyComboBox =
            new ComboBox<>();

    private final ComboBox<RothConversionStopRule>
            rothStopRuleComboBox =
            new ComboBox<>();

    private final ComboBox<RothConversionStrategy>
            rothStrategyComboBox =
            new ComboBox<>();

    private final VBox socialSecurityContainer =
            new VBox(8);

    private final List<SocialSecurityRow>
            socialSecurityRows =
            new ArrayList<>();

    private final ComboBox<String> deathScenarioComboBox =
            new ComboBox<>();

    private final TextField deathYearField =
            new TextField();

    private final ComboBox<Integer> survivorAgeComboBox =
            new ComboBox<>();

    private TableView<ProjectionYear> projectionTable =
            new TableView<>();

    private ProjectionComparison currentBaselineComparison;

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

    private final Label nonInvestableAssetsValue =
            createMetricValueLabel();

    private final Label netWorthValue =
            createMetricValueLabel();

    private final Label nonInvestableAssetsDetail =
            new Label();

    private final Label netWorthDetail =
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

    private Consumer<List<SocialSecurityUpdate>>
            socialSecurityHandler;

    private Consumer<ProjectionYear>
            yearDoubleClickHandler;

    private List<NonInvestableAssetProjection>
            nonInvestableAssetProjections =
            List.of();

    private final ProjectionCsvExporter projectionCsvExporter =
            new ProjectionCsvExporter();

    private final ProjectionPdfExporter projectionPdfExporter =
            new ProjectionPdfExporter();

    private Projection currentProjection;

    private VBox baselineComparisonBox;

    private final Label baselineComparisonYear =
            new Label();

    private final Label growthBaselineLabel =
            new Label();

    private final Label growthCurrentLabel =
            new Label();

    private final Label growthChangeLabel =
            new Label();

    private final Label incomeBaselineLabel =
            new Label();

    private final Label incomeCurrentLabel =
            new Label();

    private final Label incomeChangeLabel =
            new Label();

    private final Label totalTaxesBaselineLabel =
            new Label();

    private final Label totalTaxesCurrentLabel =
            new Label();

    private final Label totalTaxesChangeLabel =
            new Label();

    private final Label peakTaxBaselineLabel =
            new Label();

    private final Label peakTaxCurrentLabel =
            new Label();

    private final Label peakTaxChangeLabel =
            new Label();

    private final Label investableBaselineLabel =
            new Label("—");

    private final Label investableCurrentLabel =
            new Label("—");

    private final Label investableChangeLabel =
            new Label("—");

    private final Label nonInvestableBaselineLabel =
            new Label("—");

    private final Label nonInvestableCurrentLabel =
            new Label("—");

    private final Label nonInvestableChangeLabel =
            new Label("—");

    private final Label netWorthBaselineLabel =
            new Label("—");

    private final Label netWorthCurrentLabel =
            new Label("—");

    private final Label netWorthChangeLabel =
            new Label("—");

    private final Label estateBaselineLabel =
            new Label("—");

    private final Label estateCurrentLabel =
            new Label("—");

    private final Label estateChangeLabel =
            new Label("—");

    private final Label taxBaselineLabel =
            new Label("—");

    private final Label taxCurrentLabel =
            new Label("—");

    private final Label taxChangeLabel =
            new Label("—");

    private final Label investablePercentLabel =
            new Label();

    private final Label nonInvestablePercentLabel =
            new Label();

    private final Label netWorthPercentLabel =
            new Label();

    private final Label estatePercentLabel =
            new Label();

    public ResultsSummaryView(
            ApplicationController controller) {

        this.controller =
                Objects.requireNonNull(
                        controller);



        projectionTable =
                new TableView<>();

        projectionTable.setFixedCellSize(30);

        projectionTable.setColumnResizePolicy(
                TableView
                        .CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

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
                "Investable Asset Composition");

        compositionChart.getStyleClass().add(
                "results-chart");

        compositionChart.setLegendVisible(true);
        compositionChart.setLegendSide(Side.RIGHT);
        //compositionChart.setLegend(legend);

        compositionChart.setPrefHeight(190);
        compositionChart.setMinHeight(170);

        baselineComparisonBox =
                createBaselineComparisonSection();

        baselineComparisonBox.setVisible(false);
        baselineComparisonBox.setManaged(false);

        projectionTable.setPlaceholder(
                new Label(
                        "No projection available."));

        projectionTable.getStyleClass().add(
                "projection-table");

        projectionTable.setPrefHeight(430);
        projectionTable.setMinHeight(350);
        projectionTable.setMaxHeight(500);

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
        setupProjectionSelection();
        if (!projectionTable
                .getItems()
                .isEmpty()) {

            projectionTable
                    .getSelectionModel()
                    .select(0);
        }

        applyEconomicButton.setOnAction(
                event -> applyEconomicAssumptions());

        applyRothButton.setOnAction(
                event -> applyRothConversion());

        applyDeathButton.setOnAction(
                event -> applyDeathScenario());

        applySocialSecurityButton.setOnAction(
                event -> applySocialSecurity());

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

        applySocialSecurityButton.getStyleClass().add(
                "apply-button");

        setTop(
                createHeader());

        setCenter(
                createMainContent());

        VBox assumptionsPanel =
                createAssumptionPanel();

        setRight(
                assumptionsPanel);

        BorderPane.setMargin(
                assumptionsPanel,
                new Insets(
                        0,
                        0,
                        0,
                        12));

        BorderPane.setAlignment(
                assumptionsPanel,
                Pos.TOP_LEFT);
    }


//    public void setBaselineComparison(
//            ProjectionComparison comparison) {
//
//        this.currentBaselineComparison =
//                comparison;
//
//        updateBaselineComparisonView();
//    }

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
                        RothConversionStrategy.FILL_24_PERCENT_BRACKET,
                        RothConversionStrategy
                                .CUSTOM_TAXABLE_INCOME_TARGET);

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

                            case CUSTOM_TAXABLE_INCOME_TARGET ->
                                    "Custom Taxable Income Target";
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

                            case "Custom Taxable Income Target" ->
                                    RothConversionStrategy
                                            .CUSTOM_TAXABLE_INCOME_TARGET;

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

        rothTargetTaxableIncomeField.setPromptText(
                "Target Taxable Income");

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

        boolean customTarget =
                strategy ==
                        RothConversionStrategy
                                .CUSTOM_TAXABLE_INCOME_TARGET;

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

        rothAmountLabel.setVisible(
                fixedAmount);

        rothAmountLabel.setManaged(
                fixedAmount);

        rothAmountField.setVisible(
                fixedAmount);

        rothAmountField.setManaged(
                fixedAmount);

        rothTargetTaxableIncomeField.setDisable(
                !enabled || !customTarget);

        rothTargetTaxableIncomeLabel.setVisible(
                customTarget);

        rothTargetTaxableIncomeLabel.setManaged(
                customTarget);

        rothTargetTaxableIncomeField.setVisible(
                customTarget);

        rothTargetTaxableIncomeField.setManaged(
                customTarget);
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
                new VBox(10);

        content.getChildren().add(
                createMetricCards());

        content.getChildren().add(
                baselineComparisonBox);

        VBox projectionSection =
                createProjectionSection();

        content.getChildren().add(
                projectionSection);

        HBox charts =
                new HBox(
                        10,
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

        return content;
    }
    private VBox createBaselineComparisonSection() {

        VBox section =
                new VBox(8);

        section.getStyleClass().add(
                "baseline-comparison");

        baselineComparisonYear.setText(
                "Baseline Comparison");

        baselineComparisonYear.getStyleClass().add(
                "section-title");

        HBox cards =
                new HBox(10);
        cards.getChildren().addAll(

                createComparisonMetricCard(
                        "Investment Growth",
                        "metric-green",
                        growthBaselineLabel,
                        growthCurrentLabel,
                        growthChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Total Income",
                        "metric-blue",
                        incomeBaselineLabel,
                        incomeCurrentLabel,
                        incomeChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Total Taxes",
                        "metric-orange",
                        totalTaxesBaselineLabel,
                        totalTaxesCurrentLabel,
                        totalTaxesChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Peak Annual Tax",
                        "metric-orange",
                        peakTaxBaselineLabel,
                        peakTaxCurrentLabel,
                        peakTaxChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Investable Assets",
                        "metric-blue",
                        investableBaselineLabel,
                        investableCurrentLabel,
                        investableChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Total Net Worth",
                        "metric-blue",
                        netWorthBaselineLabel,
                        netWorthCurrentLabel,
                        netWorthChangeLabel,
                        true),

                createComparisonMetricCard(
                        "Investable After-Tax Estate",
                        "metric-purple",
                        estateBaselineLabel,
                        estateCurrentLabel,
                        estateChangeLabel,
                        true));

        for (Node card :
                cards.getChildren()) {

            HBox.setHgrow(
                    card,
                    Priority.ALWAYS);
        }

        section.getChildren().addAll(
                baselineComparisonYear,
                cards);

        return section;
    }

    private VBox createComparisonMetricCard(
            String title,
            String styleClass,
            Label baselineLabel,
            Label currentLabel,
            Label changeLabel,
            boolean showPercentChange) {

        VBox card =
                new VBox(5);

        card.getStyleClass().add(
                "comparison-metric-card");

        Label titleLabel =
                new Label(title);

        titleLabel.getStyleClass().add(
                "comparison-card-title");

        HBox baselineRow =
                createComparisonValueRow(
                        "Baseline",
                        baselineLabel);

        HBox currentRow =
                createComparisonValueRow(
                        "Current",
                        currentLabel);

        Label changeTitle =
                new Label("Change");

        changeTitle.getStyleClass().add(
                "comparison-change-title");

        changeLabel.getStyleClass().add(
                "comparison-card-change");

        card.getChildren().addAll(
                titleLabel,
                baselineRow,
                currentRow,
                changeTitle,
                changeLabel);

        return card;
    }

    private HBox createComparisonValueRow(
            String label,
            Label value) {

        Label description =
                new Label(label);

        description.getStyleClass().add(
                "comparison-value-label");

        HBox row =
                new HBox(5);

        row.setAlignment(
                Pos.CENTER_LEFT);

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS);

        value.getStyleClass().add(
                "comparison-card-value");

        row.getChildren().addAll(
                description,
                spacer,
                value);

        return row;
    }

    private HBox createMetricCards() {

        HBox cards =
                new HBox(10);

        cards.getChildren().addAll(



//                createMetricCard(
//                        "Peak Investable Assets",
//                        FontAwesomeSolid.SHIELD_ALT,
//                        "metric-green",
//                        "metric-icon-green",
//                        peakAssetsValue,
//                        peakAssetsDetail),

                createMetricCard(
                        "Average Effective Tax Rate",
                        FontAwesomeSolid.PERCENT,
                        "metric-orange",
                        "metric-icon-orange",
                        effectiveTaxRateValue,
                        taxRateDetail),




                createMetricCard(
                        "Ending Investable Assets",
                        FontAwesomeSolid.CHART_LINE,
                        "metric-blue",
                        "metric-icon-blue",
                        endingAssetsValue,
                        endingAssetsDetail),



                createMetricCard(
                        "Home Equity/Other Assets",
                        FontAwesomeSolid.HOME,
                        "metric-orange",
                        "metric-icon-orange",
                        nonInvestableAssetsValue,
                        nonInvestableAssetsDetail),

                createMetricCard(
                        "Total Net Worth",
                        FontAwesomeSolid.DOLLAR_SIGN,
                        "metric-blue",
                        "metric-icon-blue",
                        netWorthValue,
                        netWorthDetail),

        createMetricCard(
                "After-Tax Estate Heir Value",
                FontAwesomeSolid.USERS,
                "metric-purple",
                "metric-icon-purple",
                estateValue,
                estateDetail));

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

        VBox section =
                new VBox(
                        8,
                        heading,
                        projectionTable);

        section.getStyleClass().add(
                "summary-section");

        section.setPadding(
                new Insets(12));

        VBox.setVgrow(
                projectionTable,
                Priority.ALWAYS);

        return section;
    }

    private VBox createAssumptionPanel() {

        /*
         * Fixed outer panel.
         *
         * The KEY ASSUMPTIONS heading remains visible while
         * the assumption cards below it scroll vertically.
         */
        VBox panel =
                new VBox(12);

        panel.setPrefWidth(320);
        panel.setMinWidth(300);

        Label heading =
                new Label(
                        "KEY ASSUMPTIONS");

        heading.getStyleClass().add(
                "assumptions-title");

        /*
         * Container holding everything that should scroll.
         */
        VBox assumptionContent =
                new VBox(12);

        assumptionContent.getChildren().addAll(
                createEconomicPanel(),
                createRothPanel(),
                createSocialSecurityPanel(),
                createDeathPanel());

        Label note =
                new Label(
                        "Changes apply to the current plan. "
                                + "The projection is recalculated when "
                                + "an Apply button is used.");

        note.setWrapText(true);

        note.getStyleClass().add(
                "assumption-note");

        assumptionContent.getChildren().add(
                note);

        /*
         * Scrollable assumptions area.
         */
        ScrollPane scrollPane =
                new ScrollPane(
                        assumptionContent);

        scrollPane.setFitToWidth(true);

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);

        scrollPane.setPannable(true);

        /*
         * Don't let the ScrollPane impose its own preferred
         * height. The right-hand panel should consume the
         * available height supplied by the BorderPane.
         */
        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS);

        panel.getChildren().addAll(
                heading,
                scrollPane);

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS);

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

        Label futureFederalMarginalRateChangeLabel =
                new Label("Future Federal Tax Rate Change");

        futureFederalMarginalRateChangeLabel.getStyleClass().add(
                "assumption-label");

        futureFederalMarginalRateChangeLabel.setWrapText(true);

        grid.add(
                futureFederalMarginalRateChangeLabel,
                0,
                2);

        futureFederalMarginalRateChangeField.getStyleClass().add(
                "assumption-field");

        futureFederalMarginalRateChangeField.setPromptText(
                "Percentage points");

        futureFederalMarginalRateChangeField.setAlignment(
                Pos.CENTER_RIGHT);

        grid.add(
                futureFederalMarginalRateChangeField,
                1,
                2);

        Label futureFederalMarginalRateEffectiveYearLabel =
                new Label("Effective Federal Tax Change Year");

        futureFederalMarginalRateEffectiveYearLabel.getStyleClass().add(
                "assumption-label");

        futureFederalMarginalRateEffectiveYearLabel.setWrapText(true);

        grid.add(
                futureFederalMarginalRateEffectiveYearLabel,
                0,
                3);

        futureFederalMarginalRateEffectiveYearField.getStyleClass().add(
                "assumption-field");

        futureFederalMarginalRateEffectiveYearField.setPromptText(
                "YYYY");

        futureFederalMarginalRateEffectiveYearField.setAlignment(
                Pos.CENTER_RIGHT);

        grid.add(
                futureFederalMarginalRateEffectiveYearField,
                1,
                3);

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
                createRothConversionGrid();

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

        rothAmountLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                rothAmountLabel,
                0,
                3);

        rothAmountField.getStyleClass().add(
                "assumption-field");

        grid.add(
                rothAmountField,
                1,
                3);

        rothTargetTaxableIncomeLabel.getStyleClass().add(
                "assumption-label");

        rothTargetTaxableIncomeLabel.setWrapText(
                true);

        grid.add(
                rothTargetTaxableIncomeLabel,
                0,
                4);

        rothTargetTaxableIncomeField.getStyleClass().add(
                "assumption-field");

        grid.add(
                rothTargetTaxableIncomeField,
                1,
                4);

        Label frequencyLabel =
                new Label("Frequency");

        frequencyLabel.getStyleClass().add(
                "assumption-label");

        grid.add(
                frequencyLabel,
                0,
                5);

        grid.add(
                rothFrequencyComboBox,
                1,
                5);

//        Label stopRuleLabel =
//                new Label("Stop Rule");
//
//        stopRuleLabel.getStyleClass().add(
//                "assumption-label");

//        grid.add(
//                stopRuleLabel,
//                0,
//                5);
//
//        grid.add(
//                rothStopRuleComboBox,
//                1,
//                5);

        rothStrategyComboBox.setMaxWidth(
                Double.MAX_VALUE);

        rothFrequencyComboBox.setMaxWidth(
                Double.MAX_VALUE);

        rothTargetTaxableIncomeField.setMaxWidth(
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

        labelColumn.setMinWidth(160);

        labelColumn.setPrefWidth(190);

        valueColumn.setHgrow(
                Priority.ALWAYS);

        valueColumn.setMinWidth(110);

        grid.getColumnConstraints().addAll(
                labelColumn,
                valueColumn);

        return grid;
    }


    private GridPane createRothConversionGrid() {

        GridPane grid =
                new GridPane();

        grid.setHgap(8);
        grid.setVgap(8);

        ColumnConstraints labelColumn =
                new ColumnConstraints();

        ColumnConstraints valueColumn =
                new ColumnConstraints();

        labelColumn.setMinWidth(80);
        labelColumn.setPrefWidth(95);
        labelColumn.setMaxWidth(105);

        valueColumn.setHgrow(
                Priority.ALWAYS);

        valueColumn.setMinWidth(165);

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
                new Insets(8));

        box.setPrefHeight(210);
        box.setMinHeight(180);
        box.setMaxHeight(240);

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
                new TableColumn<>("Primary Age");

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
                        "Income",
                        ProjectionYear::getGuaranteedIncome);


        TableColumn<ProjectionYear, BigDecimal>
                rothConvColumn =
                moneyColumn(
                        "Roth Conv",
                        ProjectionYear::getRothConversion);

        TableColumn<ProjectionYear, BigDecimal>
                RMDColumn =
                moneyColumn(
                        "RMD",
                        ProjectionYear::getRequiredMinimumDistribution);


        TableColumn<ProjectionYear, BigDecimal>
                combinedTaxColumn =
                moneyColumn(
                        "Taxes",
                        ProjectionYear::
                                getTotalIncomeTax);

        TableColumn<ProjectionYear, BigDecimal>
                taxableIncomeColumn =
                moneyColumn(
                        "Taxable Income",
                        ProjectionYear::
                                getFederalTaxableIncome);


        TableColumn<ProjectionYear, BigDecimal>
                effectiveTaxRateColumn =
                percentColumn(
                        "Eff. Tax Rate",
                        ProjectionYear::
                                getCombinedEffectiveTaxRate);


        TableColumn<ProjectionYear, BigDecimal>
                nonInvestableAssetsColumn =
                moneyColumn(
                        "Non-Investable Assets",
                        year ->
                                getNonInvestableAssetValue(
                                        year.getCalendarYear()));

        TableColumn<ProjectionYear, BigDecimal>
                totalEstateColumn =
                moneyColumn(
                        "Total Estate",
                        this::getTotalEstateValue);

        TableColumn<ProjectionYear, BigDecimal>
                endingAssetsColumn =
                moneyColumn(
                        "Investable Assets",
                        ProjectionYear::
                                getEndingInvestableAssets);

        TableColumn<ProjectionYear, BigDecimal>
                netWorthColumn =
                moneyColumn(
                        "Net Worth",
                        this::getNetWorth);

        projectionTable.getColumns().addAll(
                yearColumn,
                ageColumn,
                beginningAssetsColumn,
               // netCashFlowColumn,
                growthColumn,
                combinedTaxColumn,
                effectiveTaxRateColumn,
                rothConvColumn,
                taxableIncomeColumn,
                RMDColumn,
                nonInvestableAssetsColumn,
                endingAssetsColumn,
                totalEstateColumn,
                netWorthColumn);

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
                                                : compactMoney(value));
                            }
                        });

        return column;
    }


    private TableColumn<ProjectionYear, BigDecimal>
    percentColumn(
            String title,
            java.util.function.Function<ProjectionYear, BigDecimal> getter) {

        TableColumn<ProjectionYear, BigDecimal>
                column =
                new TableColumn<>(title);

        column.setCellValueFactory(
                data ->
                        new ReadOnlyObjectWrapper<>(
                                getter.apply(
                                        data.getValue())));

        column.setCellFactory(
                col ->
                        new TableCell<>() {

                            @Override
                            protected void updateItem(
                                    BigDecimal value,
                                    boolean empty) {

                                super.updateItem(
                                        value,
                                        empty);

                                if (empty || value == null) {
                                    setText(null);
                                } else {
                                    setText(
                                            UIFormatters.percent(
                                                    value));
                                }
                            }
                        });

        return column;
    }

    private void setupProjectionSelection() {

        projectionTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable,
                         oldYear,
                         selectedYear) -> {

                            if (selectedYear != null) {
                                updateBaselineComparison();
                            }
                        });
    }
    private void updateBaselineComparison() {

        if (currentPlan == null
                || currentProjection == null
                || currentPlan.getBaseline() == null) {

            baselineComparisonBox.setVisible(false);
            baselineComparisonBox.setManaged(false);

            return;
        }

        ProjectionComparison comparison =
                controller.compareProjections();

        baselineComparisonBox.setVisible(true);
        baselineComparisonBox.setManaged(true);

        updateBaselineComparisonValues(
                comparison);
    }

    private void updateBaselineComparisonValues(
            ProjectionComparison comparison) {

        baselineComparisonYear.setText(
                "Baseline Comparison — "
                        + comparison.getCalendarYear());

        updateMoneyComparison(
                growthBaselineLabel, growthCurrentLabel, growthChangeLabel,
                comparison.getBaselineInvestmentGrowth(),
                comparison.getCurrentInvestmentGrowth(),
                comparison.getInvestmentGrowthChange(), false);

        updateMoneyComparison(
                incomeBaselineLabel, incomeCurrentLabel, incomeChangeLabel,
                comparison.getBaselineTotalIncome(),
                comparison.getCurrentTotalIncome(),
                comparison.getTotalIncomeChange(), false);

        updateMoneyComparison(
                totalTaxesBaselineLabel, totalTaxesCurrentLabel, totalTaxesChangeLabel,
                comparison.getBaselineTotalTaxes(),
                comparison.getCurrentTotalTaxes(),
                comparison.getTotalTaxesChange(), true);

        updateMoneyComparison(
                peakTaxBaselineLabel, peakTaxCurrentLabel, peakTaxChangeLabel,
                comparison.getBaselinePeakAnnualTax(),
                comparison.getCurrentPeakAnnualTax(),
                comparison.getPeakAnnualTaxChange(), true);

        investableBaselineLabel.setText(
                compactMoney(
                        comparison
                                .getBaselineEndingInvestableAssets()));

        investableCurrentLabel.setText(
                compactMoney(
                        comparison
                                .getCurrentEndingInvestableAssets()));

        setChangeLabel(
                investableChangeLabel,
                comparison
                        .getBaselineEndingInvestableAssets(),
                comparison
                        .getEndingInvestableAssetsChange(),
                false);

        investablePercentLabel.setText(
                "("
                        + formatPercentChange(
                        comparison
                                .getBaselineEndingInvestableAssets(),
                        comparison
                                .getEndingInvestableAssetsChange())
                        + ")");

        netWorthBaselineLabel.setText(
                compactMoney(
                        comparison.getBaselineNetWorth()));

        netWorthCurrentLabel.setText(
                compactMoney(
                        comparison.getCurrentNetWorth()));

        setChangeLabel(
                netWorthChangeLabel,
                comparison
                        .getBaselineNetWorth(),
                comparison
                        .getNetWorthChange(),
                false);

        netWorthPercentLabel.setText(
                "("
                        + formatPercentChange(
                        comparison.getBaselineNetWorth(),
                        comparison.getNetWorthChange())
                        + ")");

        estateBaselineLabel.setText(
                compactMoney(
                        comparison.getBaselineAfterTaxEstate()));

        estateCurrentLabel.setText(
                compactMoney(
                        comparison.getCurrentAfterTaxEstate()));

        setChangeLabel(
                estateChangeLabel,
                comparison
                        .getBaselineAfterTaxEstate(),
                comparison
                        .getAfterTaxEstateChange(),
                false);

    }

    private void updateMoneyComparison(
            Label baselineLabel,
            Label currentLabel,
            Label changeLabel,
            BigDecimal baseline,
            BigDecimal current,
            BigDecimal change,
            boolean lowerIsBetter) {

        baselineLabel.setText(compactMoney(baseline));
        currentLabel.setText(compactMoney(current));
        setChangeLabel(
                changeLabel,
                baseline,
                change,
                lowerIsBetter);
    }

    private void setTaxChangeLabel(
            Label label,
            BigDecimal change) {

        label.setText(
                formatChange(
                        change,
                        true));

        label.getStyleClass().removeAll(
                "comparison-positive",
                "comparison-negative",
                "comparison-neutral");

        int result =
                change.compareTo(
                        BigDecimal.ZERO);

        if (result == 0) {

            label.getStyleClass().add(
                    "comparison-neutral");

        } else {

            label.getStyleClass().add(
                    result < 0
                            ? "comparison-positive"
                            : "comparison-negative");
        }
    }

    private void setChangeLabel(
            Label label,
            BigDecimal baseline,
            BigDecimal change,
            boolean lowerIsBetter) {

        String changeText =
                formatChange(
                        change,
                        false);

        String percentText =
                formatPercentChange(
                        baseline,
                        change);

        if (baseline.compareTo(BigDecimal.ZERO) != 0) {

            changeText +=
                    " ("
                            + percentText
                            + ")";
        }

        label.setText(changeText);

        label.getStyleClass().removeAll(
                "comparison-positive",
                "comparison-negative",
                "comparison-neutral");

        int result =
                change.compareTo(
                        BigDecimal.ZERO);

        if (result == 0) {

            label.getStyleClass().add(
                    "comparison-neutral");

        } else {

            boolean favorable =
                    lowerIsBetter
                            ? result < 0
                            : result > 0;

            label.getStyleClass().add(
                    favorable
                            ? "comparison-positive"
                            : "comparison-negative");
        }
    }



    private String formatChange(
            BigDecimal change,
            boolean percentage) {

        if (change.compareTo(
                BigDecimal.ZERO) > 0) {

            return "+"
                    + (percentage
                    ? UIFormatters.percent(change)
                    : compactMoney(change));
        }

        if (change.compareTo(
                BigDecimal.ZERO) < 0) {

            return "-"
                    + (percentage
                    ? UIFormatters.percent(
                    change.abs())
                    : compactMoney(
                    change.abs()));
        }

        return percentage
                ? UIFormatters.percent(
                BigDecimal.ZERO)
                : compactMoney(
                BigDecimal.ZERO);
    }

    public void load(
            RetirementPlan plan,
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableAssetProjections) {

        currentPlan = plan;
        currentProjection = projection;

        loadAssumptions(plan);

        loadRothConversion(plan);

        loadSocialSecurity(plan);

        this.nonInvestableAssetProjections =
                nonInvestableAssetProjections != null
                        ? nonInvestableAssetProjections
                        : List.of();


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

            baselineComparisonBox.setVisible(false);
            baselineComparisonBox.setManaged(false);

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

        projectionTable
                .getSelectionModel()
                .selectLast();
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

        TaxAssumptions tax = assumptions.getTaxAssumptions();

        BigDecimal futureFederalMarginalRateAdjustment =
                tax.getFutureFederalMarginalRateAdjustment();

        futureFederalMarginalRateChangeField.setText(
                futureFederalMarginalRateAdjustment != null
                        ? percentForField(
                        futureFederalMarginalRateAdjustment)
                        : "");

        Integer futureFederalMarginalRateEffectiveYear =
                tax.getFutureFederalMarginalRateEffectiveYear();

        futureFederalMarginalRateEffectiveYearField.setText(
                futureFederalMarginalRateEffectiveYear != null
                        ? futureFederalMarginalRateEffectiveYear.toString()
                        : "");

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
                compactMoney(
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
                compactMoney(
                        getTotalEstateValue(last)));

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

        NonInvestableAssetProjection
                lastNonInvestableProjection =
                nonInvestableAssetProjections
                        .stream()
                        .filter(projection ->
                                projection.getCalendarYear()
                                        == last.getCalendarYear())
                        .findFirst()
                        .orElse(null);

        BigDecimal endingNonInvestableAssets =
                lastNonInvestableProjection != null
                        ? lastNonInvestableProjection
                        .getTotalValue()
                        : BigDecimal.ZERO;

        BigDecimal totalNetWorth =
                last.getEndingInvestableAssets()
                        .add(endingNonInvestableAssets);

        nonInvestableAssetsValue.setText(
              compactMoney(
                        endingNonInvestableAssets));

        nonInvestableAssetsDetail.setText(
                "Year "
                        + last.getCalendarYear());

        netWorthValue.setText(
                compactMoney(
                        totalNetWorth));

        netWorthDetail.setText(
                "Year "
                        + last.getCalendarYear() +
                " • Investable + Non-Investable");
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
        assetXAxis.setTickLabelFormatter(
                createYearAxisFormatter());

        compositionXAxis.setAutoRanging(false);
        compositionXAxis.setLowerBound(firstYear);
        compositionXAxis.setUpperBound(lastYear);
        compositionXAxis.setTickUnit(5);
        compositionXAxis.setTickLabelFormatter(
                createYearAxisFormatter());

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

    private StringConverter<Number> createYearAxisFormatter() {

        return new StringConverter<Number>() {

            @Override
            public String toString(Number value) {

                if (value == null) {
                    return "";
                }

                return String.valueOf(
                        value.intValue());
            }

            @Override
            public Number fromString(String string) {

                return Integer.parseInt(
                        string);
            }
        };
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

        nonInvestableAssetsValue.setText("-");
        netWorthValue.setText("-");

        nonInvestableAssetsDetail.setText("");
        netWorthDetail.setText("");

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

            FutureFederalTaxRateChangeInput futureFederalRateChange =
                    FutureFederalTaxRateChangeInput.parse(
                            futureFederalMarginalRateChangeField
                                    .getText(),
                            futureFederalMarginalRateEffectiveYearField
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

            TaxAssumptions existingTax =
                    current.getTaxAssumptions();

            TaxAssumptions updatedTax =
                    new TaxAssumptions(
                            existingTax.getFederalTaxBracketGrowthRate(),
                            existingTax.getStandardDeductionGrowthRate(),
                            existingTax.getStateIncomeTaxRate(),
                            existingTax.getLocalIncomeTaxRate(),
                            existingTax.getFilingStatus(),
                            existingTax
                                    .getEstimatedHeirTaxRateOnTaxDeferredAssets(),
                            futureFederalRateChange.adjustment(),
                            futureFederalRateChange.effectiveYear());

            PlanningAssumptions updated =
                    new PlanningAssumptions(
                            updatedEconomic,
                            updatedTax,
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
                    ex.getMessage() != null
                            ? ex.getMessage()
                            : "Please enter valid assumption values.");
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

            /*
             * A plan with no Roth conversion request
             * intentionally loads with blank fields.
             * Disabling the feature must therefore not
             * require or parse those fields.
             */
            if (!enabled) {

                rothConversionHandler.accept(
                        null);

                return;
            }

            RothConversionStrategy strategy =
                    rothStrategyComboBox.getValue();

            if (strategy == null) {

                throw new IllegalArgumentException(
                        "Roth conversion strategy is required.");
            }

            String startYearText =
                    rothStartYearField
                            .getText()
                            .trim();

            if (startYearText.isEmpty()) {

                throw new IllegalArgumentException(
                        "Roth conversion start year is required.");
            }

            int startYear =
                    Integer.parseInt(
                            startYearText);

            BigDecimal annualAmount =
                    BigDecimal.ZERO;

            BigDecimal customTargetTaxableIncome =
                    null;

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

            if (strategy ==
                    RothConversionStrategy
                            .CUSTOM_TAXABLE_INCOME_TARGET) {

                String targetText =
                        rothTargetTaxableIncomeField
                                .getText()
                                .trim();

                if (targetText.isEmpty()) {

                    throw new IllegalArgumentException(
                            "Target taxable income is required.");
                }

                customTargetTaxableIncome =
                        parseMoney(targetText);

                if (customTargetTaxableIncome.signum() < 0) {

                    throw new IllegalArgumentException(
                            "Target taxable income cannot be negative.");
                }
            }

            RothConversionFrequency frequency =
                    rothFrequencyComboBox.getValue();

            RothConversionStopRule stopRule =
                    rothStopRuleComboBox.getValue();

            if (frequency == null) {

                throw new IllegalArgumentException(
                        "Roth conversion frequency is required.");
            }

            if (stopRule == null) {

                throw new IllegalArgumentException(
                        "Roth conversion stop rule is required.");
            }

            RothConversionRequest request =
                    new RothConversionRequest(
                            enabled,
                            startYear,
                            annualAmount,
                            stopRule,
                            strategy,
                            frequency,
                            customTargetTaxableIncome);

            rothConversionHandler.accept(
                    request);

        } catch (NumberFormatException ex) {

            showError(
                    "Roth conversion year and monetary values must be valid.");

        } catch (IllegalArgumentException ex) {

            showError(
                    ex.getMessage() != null
                            ? ex.getMessage()
                            : "Please enter valid Roth conversion values.");

        } catch (RuntimeException ex) {

            ex.printStackTrace();

            showError(
                    "Roth conversion changes could not be applied.");
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


    public void setOnSocialSecurityApply(
            Consumer<List<SocialSecurityUpdate>> handler) {

        this.socialSecurityHandler =
                handler;
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
                    this.nonInvestableAssetProjections,
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
                    nonInvestableAssetProjections,
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
            rothTargetTaxableIncomeField.clear();

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

        if (request.getStrategy()
                == RothConversionStrategy.FIXED_AMOUNT) {

            rothAmountField.setText(
                    request.getAnnualAmount()
                            .toPlainString());

        } else {

            rothAmountField.clear();
        }

        if (request.getStrategy()
                == RothConversionStrategy
                .CUSTOM_TAXABLE_INCOME_TARGET) {

            rothTargetTaxableIncomeField.setText(
                    request
                            .getCustomTargetTaxableIncome()
                            .toPlainString());

        } else {

            rothTargetTaxableIncomeField.clear();
        }

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

    private VBox createSocialSecurityPanel() {

        Label heading =
                createPanelHeading(
                        "SOCIAL SECURITY",
                        FontAwesomeSolid.USER_CLOCK,
                        "assumption-title-blue");

        socialSecurityContainer
                .getStyleClass()
                .add("social-security-content");

        VBox box =
                new VBox(
                        10,
                        heading,
                        socialSecurityContainer,
                        applySocialSecurityButton);

        box.getStyleClass().add(
                "assumption-card");

        applySocialSecurityButton.setMaxWidth(
                Double.MAX_VALUE);

        applySocialSecurityButton.getStyleClass().add(
                "social-security-apply-button");

        return box;
    }

    private void loadSocialSecurity(
            RetirementPlan plan) {

        socialSecurityContainer
                .getChildren()
                .clear();

        socialSecurityRows.clear();

        if (plan == null) {
            return;
        }

        addSocialSecuritySource(
                plan.getHousehold()
                        .getPrimaryPerson());

        if (plan.getHousehold().getSpouse() != null) {

            addSocialSecuritySource(
                    plan.getHousehold()
                            .getSpouse());
        }
    }

    private void addSocialSecuritySource(
            Person person) {

        if (person == null) {
            return;
        }

        person.getIncomeSources()
                .stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .findFirst()
                .ifPresent(
                        socialSecurity ->
                                createSocialSecurityRow(
                                        person,
                                        socialSecurity));
    }

    private static final class SocialSecurityRow {

        private final Person person;
        private final SocialSecurityIncome source;
        private final ComboBox<Integer> claimingAgeCombo;
        private final Label benefitLabel;

        private SocialSecurityRow(
                Person person,
                SocialSecurityIncome source,
                ComboBox<Integer> claimingAgeCombo,
                Label benefitLabel) {

            this.person = person;
            this.source = source;
            this.claimingAgeCombo = claimingAgeCombo;
            this.benefitLabel = benefitLabel;
        }
    }

    public static final class SocialSecurityUpdate {

        private final Person person;
        private final SocialSecurityIncome source;

        public SocialSecurityUpdate(
                Person person,
                SocialSecurityIncome source) {

            this.person = person;
            this.source = source;
        }

        public Person getPerson() {
            return person;
        }

        public SocialSecurityIncome getSource() {
            return source;
        }
    }

    private void createSocialSecurityRow(
            Person person,
            SocialSecurityIncome socialSecurity) {
        Label personLabel =
                new Label(
                        person.getFirstName()
                                + " — Claiming Age");

        personLabel.getStyleClass().add(
                "assumption-person-label");

        ComboBox<Integer> ageCombo =
                new ComboBox<>();

        ageCombo.getItems().addAll(
                62,
                63,
                64,
                65,
                66,
                67,
                68,
                69,
                70);





        ageCombo.setPrefWidth(70);
        ageCombo.setMaxWidth(70);

        ageCombo.setValue(
                socialSecurity.getClaimingAge());

        Label benefitLabel =
                new Label();

        benefitLabel.getStyleClass().add(
                "assumption-value");

        updateSocialSecurityPreview(
                person,
                socialSecurity,
                ageCombo,
                benefitLabel);

        ageCombo.valueProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) -> {

                            if (newValue == null) {
                                return;
                            }

                            updateSocialSecurityPreview(
                                    person,
                                    socialSecurity,
                                    ageCombo,
                                    benefitLabel);
                        });

        GridPane grid =
                createTwoColumnGrid();

        grid.add(
                personLabel,
                0,
                0);

        grid.add(
                ageCombo,
                1,
                0);

        Label benefitLabelTitle =
                new Label("Monthly Benefit");

        benefitLabelTitle.getStyleClass().add(
                "assumption-label");

        grid.add(
                benefitLabelTitle,
                0,
                1);

        grid.add(
                benefitLabel,
                1,
                1);

        socialSecurityContainer
                .getChildren()
                .add(grid);

        socialSecurityRows.add(
                new SocialSecurityRow(
                        person,
                        socialSecurity,
                        ageCombo,
                        benefitLabel));

    }

    private void updateSocialSecurityPreview(
            Person person,
            SocialSecurityIncome socialSecurity,
            ComboBox<Integer> ageCombo,
            Label benefitLabel) {

        Integer claimingAge =
                ageCombo.getValue();

        if (claimingAge == null) {
            benefitLabel.setText("-");
            return;
        }

        LocalDate claimDate =
                SocialSecurityBenefitStartDateCalculator
                        .calculate(person, claimingAge)
                        .orElse(null);

        if (claimDate == null) {
            benefitLabel.setText("-");
            return;
        }

        SocialSecurityIncome preview =
                new SocialSecurityIncome(
                        socialSecurity.getName(),
                        socialSecurity.getOwnership(),
                        claimDate,
                        socialSecurity.getEndDate(),
                        socialSecurity
                                .getFullRetirementMonthlyBenefit(),
                        claimingAge,
                        socialSecurity.getAnnualColaRate(),
                        socialSecurity.getBenefitValuationYear());

        BigDecimal monthlyBenefit =
                preview.getProjectedMonthlyBenefit(
                        person,
                        claimDate,
                        currentPlan
                                .getPlanningAssumptions()
                                .getSocialSecurityColaRate());

        benefitLabel.setText(
                UIFormatters.money(monthlyBenefit));
    }

    private void applySocialSecurity() {

        if (currentPlan == null ||
                socialSecurityHandler == null) {

            return;
        }

        try {

            List<SocialSecurityUpdate> updatedSources =
                    new ArrayList<>();

            for (SocialSecurityRow row :
                    socialSecurityRows) {

                Integer claimingAge =
                        row.claimingAgeCombo.getValue();

                if (claimingAge == null) {
                    continue;
                }

                SocialSecurityIncome current =
                        row.source;

                LocalDate newStartDate =
                        SocialSecurityBenefitStartDateCalculator
                                .calculate(
                                        row.person,
                                        claimingAge)
                                .orElse(null);

                if (newStartDate == null) {
                    continue;
                }

                SocialSecurityIncome updated =
                        new SocialSecurityIncome(
                                current.getName(),
                                current.getOwnership(),
                                newStartDate,
                                current.getEndDate(),
                                current.getFullRetirementMonthlyBenefit(),
                                claimingAge,
                                current.getAnnualColaRate(),
                                current.getBenefitValuationYear());

                updatedSources.add(
                        new SocialSecurityUpdate(
                                row.person,
                                updated));
            }

            socialSecurityHandler.accept(
                    updatedSources);

        } catch (Exception ex) {

            ex.printStackTrace();

            showError(
                    "Unable to apply Social Security changes.");
        }
    }

    private BigDecimal getNonInvestableAssetValue(
            int calendarYear) {

        return nonInvestableAssetProjections
                .stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(NonInvestableAssetProjection::
                        getTotalValue)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getTotalEstateValue(
            ProjectionYear projectionYear) {

        BigDecimal afterTaxEstateValue =
                projectionYear.getAfterTaxEstateValue();

        BigDecimal nonInvestableValue =
                getNonInvestableAssetValue(
                        projectionYear.getCalendarYear());

        return afterTaxEstateValue
                .add(nonInvestableValue);
    }

    private BigDecimal getNetWorth(
            ProjectionYear projectionYear) {

        BigDecimal investableAssets =
                projectionYear.getEndingInvestableAssets();

        BigDecimal nonInvestableAssets =
                getNonInvestableAssetValue(
                        projectionYear.getCalendarYear());

        return investableAssets
                .add(nonInvestableAssets);
    }

    private String compactMoney(
            BigDecimal value) {

        if (value == null) {
            return null;
        }

        BigDecimal absolute =
                value.abs();

        if (absolute.compareTo(
                new BigDecimal("1000000")) >= 0) {

            return value
                    .divide(
                            new BigDecimal("1000000"),
                            1,
                            RoundingMode.HALF_UP)
                    .toPlainString()
                    + "M";
        }

        if (absolute.compareTo(
                new BigDecimal("1000")) >= 0) {

            return value
                    .divide(
                            new BigDecimal("1000"),
                            0,
                            RoundingMode.HALF_UP)
                    .toPlainString()
                    + "K";
        }

        return value
                .setScale(
                        0,
                        RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String formatPercentChange(
            BigDecimal baseline,
            BigDecimal change) {

        if (baseline.compareTo(
                BigDecimal.ZERO) == 0) {

            return "N/A";
        }

        BigDecimal percentChange =
                change
                        .divide(
                                baseline,
                                6,
                                RoundingMode.HALF_UP)
                        .multiply(
                                BigDecimal.valueOf(100));

        String sign =
                percentChange.compareTo(
                        BigDecimal.ZERO) > 0
                        ? "+"
                        : "";

        return sign
                + percentChange
                .setScale(
                        1,
                        RoundingMode.HALF_UP)
                .toPlainString()
                + "%";
    }

}
