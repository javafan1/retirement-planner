package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonService;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyResult;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchCalculator;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityCompleteStrategySearchRequest;


import com.daviddunn.retirementplanner.domain.model.Person;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Resizable, read-only Social Security strategy analysis window. */
public final class SocialSecurityStrategyAnalyzerDialog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private RetirementPlan plan;
    private final Stage stage = new Stage();
    private final ComboBox<SocialSecurityMortalityCategory> primaryCategory = new ComboBox<>();
    private final ComboBox<SocialSecurityMortalityCategory> spouseCategory = new ComboBox<>();
    private final TextField primaryMortalityAdjustment = new TextField("1.00");
    private final TextField spouseMortalityAdjustment = new TextField("1.00");
    private final TextField discountRate = new TextField("1.0");
    private final DatePicker pvDate;
    private final Button runButton = new Button("Run Social Security Analysis");
    private final AnalysisProgressView socialSecurityProgress = new AnalysisProgressView();
    private final Label status = new Label("Choose mortality categories, then run analysis.");
    private final Label stale = new Label();
    private final VBox recommendation = new VBox(8);
    private final GridPane claimingGrid = new GridPane();
    private final Label gridDetails = new Label("Select a retirement-grid cell.");
    private final TableView<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> strategies =
            new TableView<>();
    private final Label strategyDetails = new Label("Select a complete strategy.");
    private final TextArea assumptions = new TextArea();
    private final Spinner<Integer> integratedCandidateCount = new Spinner<>(1, 20, 10);
    private final Button integratedRunButton = new Button("Run Quick Comparison");
    private final AnalysisProgressView integratedProgress = new AnalysisProgressView();
    private final Label integratedStatus = new Label(
            "Run Social Security analysis first, then run the integrated comparison.");
    private final Label integratedStale = new Label();
    private final VBox integratedBaseline = new VBox(5);
    private final TableView<IntegratedSocialSecurityComparisonPresentation.Row> integratedTable =
            new TableView<>();
    private final TextArea integratedDetails = new TextArea();
    private final TextArea integratedMethodology = new TextArea();
    private final Button exhaustiveRunButton = new Button("Run Exhaustive Search");
    private final Button exhaustiveCancelButton = new Button("Cancel");
    private final AnalysisProgressView exhaustiveProgress = new AnalysisProgressView();
    private final Label exhaustiveStatus = new Label(
            "Run exhaustive search to evaluate the complete tested strategy universe.");
    private final Label exhaustiveStale = new Label();
    private final VBox exhaustiveSummary = new VBox(5);
    private final Label exhaustiveSocialSecurityReference = new Label();
    private final TableView<ExhaustiveIntegratedSearchPresentation.Group> exhaustiveTable =
            new TableView<>();
    private final TextArea exhaustiveDetails = new TextArea();
    private final SocialSecurityAnalyzerJobController jobs;
    private Runnable detachPlanListener = () -> { };
    private final java.util.List<Runnable> detachInputListeners = new java.util.ArrayList<>();
    private final Button socialSecurityCancelButton = new Button("Cancel");
    private final Button integratedCancelButton = new Button("Cancel");
    private SocialSecurityStrategyAnalyzerPresentation presentation;
    private IntegratedSocialSecurityComparisonPresentation integratedPresentation;
    private ExhaustiveIntegratedSearchPresentation exhaustivePresentation;
    private boolean socialSecurityResultCurrent;

    public SocialSecurityStrategyAnalyzerDialog(Window owner, RetirementPlan plan) {
        this(owner, plan, new SocialSecurityAnalyzerJobController(
                com.daviddunn.retirementplanner.ui.MainApplication.analysisJobs()));
    }

    SocialSecurityStrategyAnalyzerDialog(Window owner, RetirementPlan plan,
            SocialSecurityAnalyzerJobController jobs) {
        this.plan = plan;
        this.jobs = jobs;
        pvDate = new DatePicker(plan.getPlanningAssumptions().getProjectionStartDate());
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Social Security Strategy Analyzer");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setWidth(1180);
        stage.setHeight(820);
        stage.setScene(new Scene(content()));
        stage.setOnCloseRequest(event -> close());
        stage.setOnHidden(event -> close());
        jobs.onChanged(this::refreshJobState);
        refreshJobState();
    }

    public SocialSecurityStrategyAnalyzerDialog(Window owner,
            com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        this(owner, source.getCurrentPlan());
        detachPlanListener = source.addSourcePlanRevisionListener(() -> {
            plan = source.getCurrentPlan();
            jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
            markStale();
            if (exhaustivePresentation != null) {
                exhaustiveStale.setText("Plan changed - run exhaustive search again.");
            }
        });
    }
    public void show() {
        stage.showAndWait();
    }

    private BorderPane content() {
        primaryCategory.getItems().setAll(SocialSecurityMortalityCategory.values());
        spouseCategory.getItems().setAll(SocialSecurityMortalityCategory.values());
        stale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        integratedStale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        exhaustiveStale.setStyle("-fx-text-fill: #9a6700; -fx-font-weight: bold;");
        integratedRunButton.setDisable(true);
        exhaustiveCancelButton.setVisible(false);
        exhaustiveCancelButton.setManaged(false);
        runButton.setOnAction(event -> runAnalysis());
        integratedRunButton.setOnAction(event -> runIntegratedAnalysis());
        exhaustiveRunButton.setOnAction(event -> runExhaustiveSearch());
        exhaustiveCancelButton.setOnAction(event -> jobs.cancel());
        socialSecurityCancelButton.setOnAction(event -> jobs.cancel());
        integratedCancelButton.setOnAction(event -> jobs.cancel());
        observe(primaryCategory.valueProperty(), this::markStale);
        observe(spouseCategory.valueProperty(), this::markStale);
        observe(primaryMortalityAdjustment.textProperty(), this::markStale);
        observe(spouseMortalityAdjustment.textProperty(), this::markStale);
        observe(discountRate.textProperty(), this::markStale);
        observe(pvDate.valueProperty(), this::markStale);
        observe(integratedCandidateCount.valueProperty(), () -> {
            jobs.invalidate(SocialSecurityAnalyzerJobController.Change.QUICK_CANDIDATES);
            markIntegratedStale("Candidate count changed - run integrated analysis again.");
        });

        VBox header = new VBox(8,
                new Label("Social Security Strategy Analyzer"),
                householdSummary(),
                heading("Longevity and Valuation Assumptions"),
                wrappedLabel("These are shared analyzer assumptions. Each analysis mode explains "
                        + "how it uses them. The valuation date also sets the Social Security "
                        + "analysis and mortality base date."),
                inputs());
        header.setPadding(new Insets(12));

        TabPane modes = new TabPane(
                tab("Social Security Only", socialSecurityContent()),
                tab("Integrated Retirement Plan", integratedContent()));
        BorderPane root = new BorderPane(modes);
        root.setTop(header);
        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> { close(); stage.close(); });
        Label scope = new Label(
                "Read-only analysis. No strategy is applied to or saved in the active retirement plan.");
        HBox footer = new HBox(12, scope, closeButton);
        HBox.setHgrow(scope, Priority.ALWAYS);
        footer.setPadding(new Insets(10));
        root.setBottom(footer);
        return root;
    }

    private VBox socialSecurityContent() {
        recommendation.setPadding(new Insets(16));
        recommendation.getChildren().add(new Label(
                "Run analysis to identify the highest expected-PV tested strategy."));
        configureStrategyTable();
        assumptions.setEditable(false);
        assumptions.setWrapText(true);

        ScrollPane gridScroll = new ScrollPane(new VBox(10, claimingGrid, gridDetails));
        gridScroll.setFitToWidth(true);
        VBox strategyBox = new VBox(10, strategies, strategyDetails);
        VBox.setVgrow(strategies, Priority.ALWAYS);
        TabPane socialSecurityTabs = new TabPane(
                tab("Recommendation", new ScrollPane(recommendation)),
                tab("Claiming Grid", gridScroll),
                tab("Top Strategies", strategyBox),
                tab("Assumptions", assumptions));
        VBox content = new VBox(8,
                wrappedLabel("Uses the longevity assumptions above to weight Social Security benefits."),
                new HBox(10, runButton, socialSecurityCancelButton, status), socialSecurityProgress, stale, socialSecurityTabs);
        content.setPadding(new Insets(12));
        VBox.setVgrow(socialSecurityTabs, Priority.ALWAYS);
        return content;
    }

    private VBox integratedContent() {
        // Keep deterministic content separate for a future Longevity-Weighted mode.
        return deterministicIntegratedContent();
    }

    private VBox deterministicIntegratedContent() {
        TabPane tabs = new TabPane(
                tab("Quick Comparison", quickComparisonContent()),
                tab("Exhaustive Search", exhaustiveSearchContent()));
        VBox content = new VBox(8, heading("Deterministic Integrated Retirement Plan"),
                wrappedLabel("Deterministic integrated analysis uses the retirement plan's configured "
                        + "death scenario for full-plan outcomes. It does not use the longevity "
                        + "assumptions above to weight full retirement-plan projections."), tabs);
        content.setPadding(new Insets(12));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return content;
    }

    private VBox quickComparisonContent() {
        configureIntegratedTable();
        integratedDetails.setEditable(false);
        integratedDetails.setWrapText(true);
        integratedDetails.setPrefRowCount(12);
        integratedMethodology.setEditable(false);
        integratedMethodology.setWrapText(true);
        integratedMethodology.setPrefRowCount(7);
        integratedMethodology.setText(
                "Social Security Only ranks complete strategies by mortality-weighted expected "
                        + "Social Security present value. Integrated Retirement Plan evaluates a "
                        + "small analyzer-ordered set through the current plan's deterministic death, "
                        + "tax, withdrawal, Roth, RMD, Medicare, portfolio, and estate assumptions.\n\n"
                        + "Mortality categories, mortality adjustments, and the real discount rate affect "
                        + "strategy generation and SS Expected PV. They do not make the full-plan projection "
                        + "mortality-weighted and do not discount its future-dollar metrics.\n\n"
                        + "If the active plan changes while this window remains open, rerun the "
                        + "analyzer before relying on the comparison.");
        integratedBaseline.getChildren().setAll(new Label(
                "The current-plan baseline will appear after integrated analysis."));

        Label explanation = new Label(
                "Compares top Social Security-only strategies using deterministic full-plan outcomes. "
                        + "Rows remain in Social Security analyzer order; no integrated winner is selected.");
        explanation.setWrapText(true);
        HBox actions = new HBox(10,
                new Label("Integrated candidates (1-20):"), integratedCandidateCount,
                integratedRunButton, integratedCancelButton, integratedStatus);
        VBox tableBox = new VBox(8, heading("Candidate Comparison"), integratedTable);
        VBox.setVgrow(integratedTable, Priority.ALWAYS);
        SplitPane details = new SplitPane(
                new VBox(8, heading("Current Plan Baseline"), integratedBaseline),
                new VBox(8, heading("Selected Candidate"), integratedDetails));
        details.setDividerPositions(0.45);
        VBox content = new VBox(10,
                explanation,
                wrappedLabel("Candidate selection and Social Security Expected PV use the longevity "
                        + "assumptions above. Integrated retirement-plan outcome columns remain deterministic."),
                actions, integratedProgress, integratedStale,
                details, tableBox, heading("Methodology and Assumptions"), integratedMethodology);
        content.setPadding(new Insets(12));
        VBox.setVgrow(tableBox, Priority.ALWAYS);
        return content;
    }

    private VBox exhaustiveSearchContent() {
        configureExhaustiveTable();
        exhaustiveDetails.setEditable(false);
        exhaustiveDetails.setWrapText(true);
        exhaustiveDetails.setPrefRowCount(12);
        exhaustiveSummary.getChildren().setAll(new Label(
                "Current-plan and exhaustive-search summary will appear after a successful run."));
        Label explanation = new Label(
                "Exhaustive Search evaluates all tested claiming strategies against the "
                        + "deterministic full retirement plan. Longevity assumptions above are not "
                        + "used in this search. The ranking "
                        + "shown is deterministic After-Tax Estate Ranking under current projection "
                        + "assumptions. It is not a universal recommendation.");
        explanation.setWrapText(true);
        Label methodology = new Label(
                "This deterministic future-dollar projection uses the plan's configured death, tax, "
                        + "Roth, RMD, Medicare, withdrawal, portfolio, and estate assumptions. It is "
                        + "independent of SS-only mortality-weighted ranking and does not apply a strategy.");
        methodology.setWrapText(true);
        HBox actions = new HBox(10, exhaustiveRunButton, exhaustiveCancelButton, exhaustiveStatus);
        VBox tableBox = new VBox(8, heading("Grouped Top Tested Outcomes"), exhaustiveTable);
        VBox.setVgrow(exhaustiveTable, Priority.ALWAYS);
        SplitPane details = new SplitPane(
                new VBox(8, heading("Search Summary and Current Plan"), exhaustiveSummary),
                new VBox(8, heading("Selected Outcome"), exhaustiveDetails));
        details.setDividerPositions(0.45);
        VBox content = new VBox(10, explanation, actions, exhaustiveProgress, exhaustiveStale,
                details, tableBox, heading("Methodology"), methodology);
        VBox.setVgrow(tableBox, Priority.ALWAYS);
        return content;
    }

    private GridPane householdSummary() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);
        Person primary = plan.getHousehold().getPrimaryPerson();
        Person spouse = plan.getHousehold().getSpouse();
        grid.addRow(0, new Label("Primary:"),
                new Label(personText(primary)));
        grid.addRow(1, new Label("Spouse:"),
                new Label(personText(spouse)));
        return grid;
    }

    private String personText(Person person) {
        if (person == null) {
            return "Not configured";
        }
        String text = person.getFullName() + " — DOB "
                + (person.getBirthDate() == null ? "missing" : DATE.format(person.getBirthDate()));
        return person.getIncomeSources().stream()
                .filter(SocialSecurityIncome.class::isInstance)
                .map(SocialSecurityIncome.class::cast)
                .findFirst()
                .map(source -> text
                        + " — FRA monthly benefit "
                        + money(source.getFullRetirementMonthlyBenefit())
                        + " (valuation year " + source.getBenefitValuationYear() + ")"
                        + " — current claim " + DATE.format(source.getStartDate()))
                .orElse(text + " — Social Security source missing");
    }

    private GridPane inputs() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0,
                new Label("Primary mortality category:"), primaryCategory,
                new Label("Spouse mortality category:"), spouseCategory);
        grid.addRow(1,
                new Label("Primary mortality adjustment:"), primaryMortalityAdjustment,
                new Label("Spouse mortality adjustment:"), spouseMortalityAdjustment);
        Label help = new Label(
                "1.00x uses standard SSA mortality; above 1.00x increases modeled mortality and below 1.00x decreases it. "
                        + "Planning assumption only, not a medical or actuarial assessment.");
        help.setWrapText(true);
        grid.add(help, 0, 2, 4, 1);
        grid.addRow(3,
                new Label("Real discount rate (%):"), discountRate,
                new Label("Valuation date:"), pvDate);
        return grid;
    }

    private void runAnalysis() {
        try {
            BigDecimal rate = new BigDecimal(discountRate.getText().trim())
                    .movePointLeft(2);
            SocialSecurityMortalityAdjustment primaryAdjustment = adjustment(
                    primaryMortalityAdjustment.getText(), "Primary");
            SocialSecurityMortalityAdjustment spouseAdjustment = adjustment(
                    spouseMortalityAdjustment.getText(), "Spouse");
            SocialSecurityStrategyAnalysisContext context =
                    new SocialSecurityStrategyAnalysisRequestFactory().create(
                            plan,
                            primaryCategory.getValue(),
                            spouseCategory.getValue(),
                            primaryAdjustment,
                            spouseAdjustment,
                            rate,
                            pvDate.getValue());
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                    (progress, cancellation) -> new RunResult(context,
                            new SocialSecuritySurvivorClaimingOptimizationCalculator()
                                    .calculate(context.request(), progress, cancellation)), run -> {
                presentation = SocialSecurityStrategyAnalyzerPresentation.from(run.result());
                render(run.context(), presentation);
                socialSecurityResultCurrent = true;
                jobs.invalidate(SocialSecurityAnalyzerJobController.Change.SOCIAL_SECURITY_RESULT);
                refreshExhaustiveSocialSecurityReference();
                stale.setText("");
                markIntegratedStale("Social Security analysis changed - run integrated analysis again.");
                setAnalysisBusy(false);
                status.setText("Analysis complete.");
            }, this::showError);
            if (!accepted) {
                status.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void render(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecurityStrategyAnalyzerPresentation model) {
        SocialSecuritySurvivorClaimingOptimizationCell top =
                model.rankedStrategies().getFirst().cell();
        recommendation.getChildren().setAll(
                heading("Highest Expected-PV Tested Strategy"),
                new Label("Based on tested claiming ages, survivor choices, SSA 2022 period mortality, and the displayed assumptions."),
                new Label(strategyText(top)),
                new Label("Expected PV: " + money(top.expectedPresentValue())),
                new Label("Expected real lifetime benefits: " + money(top.expectedRealBenefits())),
                new Label("Expected nominal lifetime benefits: " + money(top.expectedNominalBenefits())));
        strategies.getItems().setAll(model.rankedStrategies());
        renderGrid(model);
        assumptions.setText(assumptionText(context, model.result()));
    }

    private void renderGrid(SocialSecurityStrategyAnalyzerPresentation model) {
        claimingGrid.getChildren().clear();
        claimingGrid.setHgap(6);
        claimingGrid.setVgap(6);
        claimingGrid.add(new Label("Primary \\ Spouse"), 0, 0);
        List<Integer> spouseAges = model.result().retirementGrid().spouseClaimAges();
        for (int column = 0; column < spouseAges.size(); column++) {
            claimingGrid.add(new Label("Age " + spouseAges.get(column)), column + 1, 0);
        }
        List<Integer> primaryAges = model.result().retirementGrid().primaryClaimAges();
        for (int row = 0; row < primaryAges.size(); row++) {
            int primaryAge = primaryAges.get(row);
            claimingGrid.add(new Label("Age " + primaryAge), 0, row + 1);
            for (int column = 0; column < spouseAges.size(); column++) {
                int spouseAge = spouseAges.get(column);
                SocialSecurityMortalityWeightedClaimingGridCell cell = model.result()
                        .retirementGrid().cellFor(primaryAge, spouseAge).orElseThrow();
                Button button = new Button(money(cell.expectedPresentValue()));
                boolean retained = model.advancedToStageTwo(primaryAge, spouseAge);
                boolean highest = model.result().retirementGrid()
                        .highestExpectedPresentValue().highestCells().contains(cell);
                button.setText((highest ? "★ " : retained ? "▶ " : "") + button.getText());
                button.setTooltip(new Tooltip(
                        "Expected PV; ★ highest Stage 1, ▶ advanced to Stage 2"));
                button.setOnAction(event -> gridDetails.setText(
                        gridDetail(model, cell, retained)));
                claimingGrid.add(button, column + 1, row + 1);
            }
        }
    }

    private String gridDetail(
            SocialSecurityStrategyAnalyzerPresentation model,
            SocialSecurityMortalityWeightedClaimingGridCell cell,
            boolean retained) {
        String detail = "Primary age " + cell.primaryClaimAge()
                + ", spouse age " + cell.spouseClaimAge()
                + " | Expected PV " + money(cell.expectedPresentValue())
                + " | Real " + money(cell.expectedRealBenefits())
                + " | Nominal " + money(cell.expectedNominalBenefits())
                + " | " + (retained ? "Advanced to Stage 2" : "Not retained for Stage 2");
        return model.bestForRetirement(cell.primaryClaimAge(), cell.spouseClaimAge())
                .map(best -> detail + " | Best expanded PV " + money(best.expectedPresentValue()))
                .orElse(detail);
    }

    private void runIntegratedAnalysis() {
        try {
            if (presentation == null || !socialSecurityResultCurrent) {
                throw new IllegalStateException(
                        "Run Social Security analysis with the current inputs before integrated analysis.");
            }
            List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected =
                    new IntegratedSocialSecurityCandidateSelector().select(
                            presentation.rankedStrategies(), integratedCandidateCount.getValue());
            if (selected.isEmpty()) {
                throw new IllegalStateException(
                        "No complete Social Security strategies are available for integrated analysis.");
            }
            List<SocialSecuritySurvivorClaimingOptimizationCell> cells = selected.stream()
                    .map(SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::cell)
                    .toList();
            var input = SocialSecurityAnalyzerInputs.quick(plan, selected);
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.QUICK,
                    (progress, cancellation) -> IntegratedSocialSecurityComparisonPresentation.from(
                            new IntegratedSocialSecurityStrategyComparisonService()
                                    .compareAnalyzerCandidates(input.plan(), cells, progress, cancellation), input.selected()), model -> {
                integratedPresentation = model;
                renderIntegrated(model);
                integratedStale.setText("");
                integratedStatus.setText(integratedCompletionText(model));
            }, this::showError);
            if (!accepted) {
                integratedStatus.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }

    private void runExhaustiveSearch() {
        try {
            var request = SocialSecurityAnalyzerInputs.exhaustive(plan);
            boolean accepted = jobs.start(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                    (progress, cancellation) -> {
                        long started = System.nanoTime();
                        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator()
                                .calculate(request, progress, cancellation);
                        return ExhaustiveIntegratedSearchPresentation.from(result,
                                Duration.ofNanos(System.nanoTime() - started), 20);
                    }, model -> {
                exhaustivePresentation = model;
                renderExhaustive(model);
                exhaustiveStale.setText("");
                exhaustiveStatus.setText("Exhaustive integrated search complete - "
                        + model.result().totalStrategyCount() + " strategies evaluated; " + model.result().failedStrategyCount() + " unavailable.");
            }, this::showError);
            if (!accepted) {
                exhaustiveStatus.setText("Another Social Security analysis is running or cleaning up.");
            }
        } catch (RuntimeException exception) {
            showError(exception);
        }
    }
    private void configureExhaustiveTable() {
        exhaustiveTable.getColumns().add(exhaustiveColumn("Estate Rank", group ->
                Integer.toString(group.representative().afterTaxEstateRank().orElseThrow()), 85));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Primary Retirement", group ->
                retirementElection(group.representative().strategy().primaryRetirementAge(),
                        group.representative().strategy().primaryRetirementClaimDate()), 165));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Spouse Retirement", group ->
                retirementElection(group.representative().strategy().spouseRetirementAge(),
                        group.representative().strategy().spouseRetirementClaimDate()), 165));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Primary Survivor", group ->
                survivorElection(group.representative().strategy().primarySurvivorElection()), 180));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Spouse Survivor", group ->
                survivorElection(group.representative().strategy().spouseSurvivorElection()), 180));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Lifetime SS", group ->
                money(group.representative().metrics().orElseThrow()
                        .lifetimeHouseholdSocialSecurity()), 125));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Total Taxes", group ->
                money(group.representative().metrics().orElseThrow().totalTaxes()), 120));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Portfolio Withdrawals", group ->
                money(group.representative().metrics().orElseThrow()
                        .lifetimePortfolioWithdrawals()), 155));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Ending Investable Assets", group ->
                money(group.representative().metrics().orElseThrow()
                        .endingInvestableAssets()), 165));
        exhaustiveTable.getColumns().add(exhaustiveColumn("After-Tax Estate", group ->
                money(group.representative().metrics().orElseThrow().afterTaxEstate()), 135));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Delta Estate vs Current", group ->
                signedMoney(group.representative().differencesFromCurrentPlan().orElseThrow()
                        .afterTaxEstate()), 155));
        exhaustiveTable.getColumns().add(exhaustiveColumn("Equivalent Strategies", group ->
                Integer.toString(group.equivalentStrategyCount()), 135));
        exhaustiveTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        exhaustiveTable.setPlaceholder(new Label("Run exhaustive search to display tested outcomes."));
        exhaustiveTable.getSelectionModel().selectedItemProperty().addListener((o, a, group) -> {
            if (group != null) {
                exhaustiveDetails.setText(exhaustiveDetail(group));
            }
        });
    }

    private TableColumn<ExhaustiveIntegratedSearchPresentation.Group, String> exhaustiveColumn(
            String title,
            java.util.function.Function<ExhaustiveIntegratedSearchPresentation.Group, String> value,
            double width) {
        TableColumn<ExhaustiveIntegratedSearchPresentation.Group, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private void renderExhaustive(ExhaustiveIntegratedSearchPresentation model) {
        var result = model.result();
        ProjectionMetrics current = result.currentPlanBaseline().metrics();
        var highest = result.rankedSuccessfulEntries().isEmpty()
                ? null : result.rankedSuccessfulEntries().getFirst();
        BigDecimal gap = highest == null ? BigDecimal.ZERO
                : highest.metrics().orElseThrow().afterTaxEstate().subtract(current.afterTaxEstate());
        String gapPercent = current.afterTaxEstate().signum() == 0
                ? "not available"
                : gap.multiply(new BigDecimal("100"))
                        .divide(current.afterTaxEstate(), 2, java.math.RoundingMode.HALF_UP)
                        .toPlainString() + "%";
        String currentRank = result.currentPlanRank().isPresent()
                ? Integer.toString(result.currentPlanRank().getAsInt()) : "Unavailable";
        exhaustiveSummary.getChildren().setAll(
                new Label(strategySummary(result.currentPlanBaseline().evaluatedStrategy())),
                new Label("Current survivor policy: persisted deterministic projection policy"),
                new Label("Current after-tax estate: " + money(current.afterTaxEstate())),
                new Label("Deterministic after-tax-estate metric position: " + currentRank),
                new Label("Highest tested after-tax estate: "
                        + (highest == null ? "Unavailable"
                        : money(highest.metrics().orElseThrow().afterTaxEstate()))),
                new Label("Gap from highest tested: " + signedMoney(gap)
                        + " (" + gapPercent + ")"),
                new Label("Successful: " + result.successfulStrategyCount()
                        + " | Failed: " + result.failedStrategyCount()
                        + " | Runtime: " + formatDuration(model.runtime())),
                exhaustiveSocialSecurityReference);
        refreshExhaustiveSocialSecurityReference();
        exhaustiveTable.getItems().setAll(model.groups());
        if (model.groups().isEmpty()) {
            exhaustiveDetails.setText("No successful exhaustive-search outcomes were returned.");
        } else {
            exhaustiveTable.getSelectionModel().selectFirst();
            exhaustiveDetails.setText(exhaustiveDetail(model.groups().getFirst()));
        }
    }

    private String socialSecurityCrossReference(ExhaustiveIntegratedSearchPresentation model) {
        if (presentation == null || !socialSecurityResultCurrent
                || presentation.rankedStrategies().isEmpty()) {
            return "SS-only recommendation cross-reference: run current SS-only analysis first.";
        }
        var recommended = presentation.rankedStrategies().getFirst().cell();
        var match = model.result().entryFor(recommended.strategy());
        if (match.isEmpty() || !match.orElseThrow().successful()) {
            return "SS-only recommendation is outside the current exhaustive integrated search universe.";
        }
        var entry = match.orElseThrow();
        BigDecimal highestEstate = model.result().rankedSuccessfulEntries().getFirst()
                .metrics().orElseThrow().afterTaxEstate();
        BigDecimal estate = entry.metrics().orElseThrow().afterTaxEstate();
        return "SS-only recommendation cross-reference: Expected PV "
                + money(recommended.expectedPresentValue())
                + ", deterministic after-tax estate " + money(estate)
                + ", estate rank " + entry.afterTaxEstateRank().orElseThrow()
                + ", gap from highest tested " + signedMoney(estate.subtract(highestEstate)) + ".";
    }

    private String exhaustiveDetail(ExhaustiveIntegratedSearchPresentation.Group group) {
        var entry = group.representative();
        ProjectionMetrics current = exhaustivePresentation.result().currentPlanBaseline().metrics();
        ProjectionMetrics candidate = entry.metrics().orElseThrow();
        ProjectionMetrics delta = entry.differencesFromCurrentPlan().orElseThrow();
        return "Deterministic After-Tax Estate Rank: "
                + entry.afterTaxEstateRank().orElseThrow()
                + "\nEquivalent tested strategies: " + group.equivalentStrategyCount()
                + "\n" + strategySummary(entry.strategy())
                + "\n\nIntegrated financial result (future-dollar deterministic projection)"
                + metricLine("Investment growth", current.totalInvestmentGrowth(),
                        candidate.totalInvestmentGrowth(), delta.totalInvestmentGrowth())
                + metricLine("Total guaranteed income", current.totalIncome(),
                        candidate.totalIncome(), delta.totalIncome())
                + metricLine("Total taxes", current.totalTaxes(), candidate.totalTaxes(), delta.totalTaxes())
                + metricLine("Peak annual tax", current.peakAnnualTax(), candidate.peakAnnualTax(), delta.peakAnnualTax())
                + metricLine("Ending investable assets", current.endingInvestableAssets(), candidate.endingInvestableAssets(), delta.endingInvestableAssets())
                + metricLine("Ending net worth", current.endingNetWorth(), candidate.endingNetWorth(), delta.endingNetWorth())
                + metricLine("After-tax estate", current.afterTaxEstate(), candidate.afterTaxEstate(), delta.afterTaxEstate())
                + metricLine("Portfolio withdrawals", current.lifetimePortfolioWithdrawals(), candidate.lifetimePortfolioWithdrawals(), delta.lifetimePortfolioWithdrawals())
                + metricLine("Roth conversions", current.lifetimeRothConversions(), candidate.lifetimeRothConversions(), delta.lifetimeRothConversions())
                + metricLine("RMDs", current.lifetimeRequiredMinimumDistributions(), candidate.lifetimeRequiredMinimumDistributions(), delta.lifetimeRequiredMinimumDistributions())
                + metricLine("Medicare premiums", current.lifetimeMedicarePremiums(), candidate.lifetimeMedicarePremiums(), delta.lifetimeMedicarePremiums())
                + metricLine("Household Social Security", current.lifetimeHouseholdSocialSecurity(), candidate.lifetimeHouseholdSocialSecurity(), delta.lifetimeHouseholdSocialSecurity())
                + "\nPrimary Social Security: " + money(candidate.lifetimePrimarySocialSecurity())
                + "\nSpouse Social Security: " + money(candidate.lifetimeSpouseSocialSecurity())
                + "\nProjection detail retained: " + entry.retainedDetail().isPresent();
    }

    private static String formatDuration(Duration duration) {
        return String.format(java.util.Locale.US, "%.2f seconds", duration.toMillis() / 1000.0d);
    }

    private void configureIntegratedTable() {
        integratedTable.getColumns().add(integratedColumn("SS Rank", row ->
                Integer.toString(row.socialSecurityRank()), 65));
        integratedTable.getColumns().add(integratedColumn("Primary Retirement", row ->
                retirementElection(row.entry().strategy().primaryRetirementAge(),
                        row.entry().strategy().primaryRetirementClaimDate()), 165));
        integratedTable.getColumns().add(integratedColumn("Spouse Retirement", row ->
                retirementElection(row.entry().strategy().spouseRetirementAge(),
                        row.entry().strategy().spouseRetirementClaimDate()), 165));
        integratedTable.getColumns().add(integratedColumn("Primary Survivor", row ->
                survivorElection(row.entry().strategy().primarySurvivorElection()), 180));
        integratedTable.getColumns().add(integratedColumn("Spouse Survivor", row ->
                survivorElection(row.entry().strategy().spouseSurvivorElection()), 180));
        integratedTable.getColumns().add(integratedColumn("SS Expected PV", row ->
                row.entry().socialSecurityOnlyExpectedValue()
                        .map(value -> money(value.expectedPresentValue())).orElse("Unavailable"), 130));
        integratedTable.getColumns().add(integratedColumn("Lifetime SS", row ->
                metric(row.entry(), ProjectionMetrics::lifetimeHouseholdSocialSecurity), 125));
        integratedTable.getColumns().add(integratedColumn("Total Taxes", row ->
                metric(row.entry(), ProjectionMetrics::totalTaxes), 120));
        integratedTable.getColumns().add(integratedColumn("Portfolio Withdrawals", row ->
                metric(row.entry(), ProjectionMetrics::lifetimePortfolioWithdrawals), 155));
        integratedTable.getColumns().add(integratedColumn("Ending Investable Assets", row ->
                metric(row.entry(), ProjectionMetrics::endingInvestableAssets), 165));
        integratedTable.getColumns().add(integratedColumn("After-Tax Estate", row ->
                metric(row.entry(), ProjectionMetrics::afterTaxEstate), 135));
        integratedTable.getColumns().add(integratedColumn("Delta Estate vs Current", row ->
                difference(row.entry(), ProjectionMetrics::afterTaxEstate), 155));
        integratedTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        integratedTable.setPlaceholder(new Label(
                "Run integrated analysis to compare complete Social Security strategies."));
        integratedTable.getSelectionModel().selectedItemProperty().addListener((o, a, row) -> {
            if (row != null) {
                integratedDetails.setText(integratedDetail(row));
            }
        });
    }

    private TableColumn<IntegratedSocialSecurityComparisonPresentation.Row, String> integratedColumn(
            String title,
            java.util.function.Function<IntegratedSocialSecurityComparisonPresentation.Row, String> value,
            double width) {
        TableColumn<IntegratedSocialSecurityComparisonPresentation.Row, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        return column;
    }

    private void renderIntegrated(IntegratedSocialSecurityComparisonPresentation model) {
        IntegratedSocialSecurityStrategyResult baseline = model.comparison().currentPlanBaseline();
        ProjectionMetrics metrics = baseline.metrics();
        integratedBaseline.getChildren().setAll(
                new Label(strategySummary(baseline.evaluatedStrategy())),
                new Label("Survivor policy: current persisted projection policy"),
                new Label("Ending investable assets: " + money(metrics.endingInvestableAssets())),
                new Label("Ending net worth: " + money(metrics.endingNetWorth())),
                new Label("After-tax estate: " + money(metrics.afterTaxEstate())),
                new Label("Total taxes: " + money(metrics.totalTaxes())),
                new Label("Portfolio withdrawals: " + money(metrics.lifetimePortfolioWithdrawals())),
                new Label("Lifetime household Social Security: "
                        + money(metrics.lifetimeHouseholdSocialSecurity())));
        integratedTable.getItems().setAll(model.rows());
        integratedDetails.setText(model.rows().isEmpty()
                ? "No unique candidates were returned."
                : integratedDetail(model.rows().getFirst()));
        if (!model.rows().isEmpty()) {
            integratedTable.getSelectionModel().selectFirst();
        }
    }

    private String integratedDetail(IntegratedSocialSecurityComparisonPresentation.Row row) {
        IntegratedSocialSecurityStrategyComparisonEntry entry = row.entry();
        String elections = "Social Security rank: " + row.socialSecurityRank()
                + "\n" + strategySummary(entry.strategy());
        if (!entry.successful()) {
            return elections + "\n\nUnavailable: " + entry.failure().orElseThrow().message();
        }
        ProjectionMetrics current = integratedPresentation.comparison()
                .currentPlanBaseline().metrics();
        ProjectionMetrics candidate = entry.integratedResult().orElseThrow().metrics();
        ProjectionMetrics delta = entry.differencesFromCurrentPlan().orElseThrow();
        return elections
                + "\n\nSS-only analysis"
                + "\nExpected PV: " + entry.socialSecurityOnlyExpectedValue()
                        .map(value -> money(value.expectedPresentValue())).orElse("Unavailable")
                + "\n\nIntegrated financial result (future-dollar deterministic projection)"
                + metricLine("Investment growth", current.totalInvestmentGrowth(),
                        candidate.totalInvestmentGrowth(), delta.totalInvestmentGrowth())
                + metricLine("Total guaranteed income", current.totalIncome(),
                        candidate.totalIncome(), delta.totalIncome())
                + metricLine("Total taxes", current.totalTaxes(), candidate.totalTaxes(), delta.totalTaxes())
                + metricLine("Peak annual tax", current.peakAnnualTax(),
                        candidate.peakAnnualTax(), delta.peakAnnualTax())
                + metricLine("Ending investable assets", current.endingInvestableAssets(),
                        candidate.endingInvestableAssets(), delta.endingInvestableAssets())
                + metricLine("Ending net worth", current.endingNetWorth(),
                        candidate.endingNetWorth(), delta.endingNetWorth())
                + metricLine("After-tax estate", current.afterTaxEstate(),
                        candidate.afterTaxEstate(), delta.afterTaxEstate())
                + metricLine("Portfolio withdrawals", current.lifetimePortfolioWithdrawals(),
                        candidate.lifetimePortfolioWithdrawals(), delta.lifetimePortfolioWithdrawals())
                + metricLine("Roth conversions", current.lifetimeRothConversions(),
                        candidate.lifetimeRothConversions(), delta.lifetimeRothConversions())
                + metricLine("RMDs", current.lifetimeRequiredMinimumDistributions(),
                        candidate.lifetimeRequiredMinimumDistributions(),
                        delta.lifetimeRequiredMinimumDistributions())
                + metricLine("Medicare premiums", current.lifetimeMedicarePremiums(),
                        candidate.lifetimeMedicarePremiums(), delta.lifetimeMedicarePremiums())
                + metricLine("Household Social Security", current.lifetimeHouseholdSocialSecurity(),
                        candidate.lifetimeHouseholdSocialSecurity(),
                        delta.lifetimeHouseholdSocialSecurity())
                + "\nPrimary Social Security: " + money(candidate.lifetimePrimarySocialSecurity())
                + "\nSpouse Social Security: " + money(candidate.lifetimeSpouseSocialSecurity());
    }

    private void configureStrategyTable() {
        strategies.getColumns().add(column("Rank", row -> Integer.toString(row.rank())));
        strategies.getColumns().add(column("Primary Ret.", row ->
                "Age " + row.cell().strategy().primaryRetirementAge()));
        strategies.getColumns().add(column("Spouse Ret.", row ->
                "Age " + row.cell().strategy().spouseRetirementAge()));
        strategies.getColumns().add(column("Primary Survivor", row ->
                row.cell().strategy().primarySurvivorElection().label()));
        strategies.getColumns().add(column("Spouse Survivor", row ->
                row.cell().strategy().spouseSurvivorElection().label()));
        strategies.getColumns().add(column("Expected PV", row ->
                money(row.cell().expectedPresentValue())));
        strategies.getColumns().add(column("Expected Real", row ->
                money(row.cell().expectedRealBenefits())));
        strategies.getColumns().add(column("Expected Nominal", row ->
                money(row.cell().expectedNominalBenefits())));
        strategies.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        strategies.getSelectionModel().selectedItemProperty().addListener((o, a, row) -> {
            if (row != null) {
                strategyDetails.setText(strategyText(row.cell())
                        + "\nExpected primary selected: "
                        + money(row.cell().expectedPrimarySelectedBenefits())
                        + " | Expected spouse selected: "
                        + money(row.cell().expectedSpouseSelectedBenefits()));
            }
        });
    }

    private TableColumn<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> column(
            String title,
            java.util.function.Function<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> value) {
        TableColumn<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy, String> column =
                new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        return column;
    }

    private String strategyText(SocialSecuritySurvivorClaimingOptimizationCell cell) {
        SocialSecurityHouseholdClaimingStrategy strategy = cell.strategy();
        return "Primary retirement: Age " + strategy.primaryRetirementAge()
                + " — " + DATE.format(strategy.primaryRetirementClaimDate())
                + "\nPrimary survivor: " + strategy.primarySurvivorElection().label()
                + " — " + DATE.format(strategy.primarySurvivorElection().claimDate())
                + "\nSpouse retirement: Age " + strategy.spouseRetirementAge()
                + " — " + DATE.format(strategy.spouseRetirementClaimDate())
                + "\nSpouse survivor: " + strategy.spouseSurvivorElection().label()
                + " — " + DATE.format(strategy.spouseSurvivorElection().claimDate());
    }

    private static String retirementElection(int age, LocalDate date) {
        return "Age " + age + " - " + DATE.format(date);
    }

    private static String survivorElection(SocialSecuritySurvivorClaimingCandidate candidate) {
        return candidate.label() + " - " + DATE.format(candidate.claimDate());
    }

    private static String strategySummary(SocialSecurityHouseholdClaimingStrategy strategy) {
        return "Primary retirement: "
                + retirementElection(strategy.primaryRetirementAge(),
                        strategy.primaryRetirementClaimDate())
                + "\nSpouse retirement: "
                + retirementElection(strategy.spouseRetirementAge(),
                        strategy.spouseRetirementClaimDate())
                + "\nPrimary survivor: "
                + survivorElection(strategy.primarySurvivorElection())
                + "\nSpouse survivor: "
                + survivorElection(strategy.spouseSurvivorElection());
    }

    private static String metric(
            IntegratedSocialSecurityStrategyComparisonEntry entry,
            java.util.function.Function<ProjectionMetrics, BigDecimal> value) {
        return entry.integratedResult()
                .map(result -> money(value.apply(result.metrics())))
                .orElse("Unavailable");
    }

    private static String difference(
            IntegratedSocialSecurityStrategyComparisonEntry entry,
            java.util.function.Function<ProjectionMetrics, BigDecimal> value) {
        return entry.differencesFromCurrentPlan()
                .map(metrics -> signedMoney(value.apply(metrics)))
                .orElse("Unavailable");
    }

    private static String metricLine(
            String label,
            BigDecimal current,
            BigDecimal candidate,
            BigDecimal difference) {
        return "\n" + label + ": Current " + money(current)
                + " | Candidate " + money(candidate)
                + " | Difference " + signedMoney(difference);
    }

    private static String integratedCompletionText(
            IntegratedSocialSecurityComparisonPresentation model) {
        String text = model.successfulCount() + " strategies evaluated";
        if (model.failureCount() > 0) {
            text += ", " + model.failureCount() + " unavailable";
        }
        return text + ".";
    }

    private String assumptionText(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result) {
        SocialSecurityMortalityTableMetadata metadata = context.mortalityMetadata();
        return "Mortality table: " + metadata.displayName()
                + "\nSource: " + metadata.sourceName() + " — " + metadata.sourceVersion()
                + "\nType: " + metadata.tableType()
                + "\nPrimary category: " + context.primaryMortalityCategory()
                + " x " + factor(context.primaryMortalityAdjustment())
                + "\nSpouse category: " + context.spouseMortalityCategory()
                + " x " + factor(context.spouseMortalityAdjustment())
                + "\nIndependence assumption: primary and spouse mortality are independent"
                + "\nSocial Security COLA: " + percent(
                        context.request().retirementGridRequest().baseStrategy().socialSecurityColaRate())
                + "\nReal discount rate: " + percent(
                        context.request().retirementGridRequest().realDiscountRate())
                + "\nValuation date: " + DATE.format(
                        context.request().retirementGridRequest().presentValueBaseDate())
                + "\nRetirement claims tested: whole ages 62–70"
                + "\nSurvivor claims tested: age 60, whole years, plus exact survivor FRA"
                + "\nStage-1 cutoff: top " + context.request().settings().topRetirementCandidates()
                + " expected-PV cells plus cutoff ties"
                + "\nRanking objective: mortality-weighted expected present value"
                + "\nStage-1 strategies: " + result.retirementGrid().cells().size()
                + "\nStage-2 strategies: " + result.stageTwoStrategyCount()
                + "\nMortality scenarios: " + result.retirementGrid().jointMortalityScenarios().size()
                + "\nStage-2 deterministic evaluations: "
                + result.stageTwoDeterministicEvaluationCount();
    }

    private void markStale() {
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.ASSUMPTIONS);
        if (presentation != null) {
            socialSecurityResultCurrent = false;
            refreshExhaustiveSocialSecurityReference();
            stale.setText("Inputs changed - run Social Security analysis again.");
            integratedRunButton.setDisable(true);
            markIntegratedStale(
                    "Social Security inputs changed - rerun Social Security analysis and Quick Comparison.");
        }
    }

    private void markIntegratedStale(String message) {
        if (integratedPresentation != null) {
            integratedStale.setText(message);
        }
    }

    private void refreshExhaustiveSocialSecurityReference() {
        if (exhaustivePresentation != null) {
            exhaustiveSocialSecurityReference.setText(socialSecurityCrossReference(exhaustivePresentation));
        }
    }

    private void setAnalysisBusy(boolean disabled) {
        runButton.setDisable(disabled);
        integratedRunButton.setDisable(disabled || !socialSecurityResultCurrent);
        exhaustiveRunButton.setDisable(disabled);
        integratedCandidateCount.setDisable(disabled);
        primaryCategory.setDisable(disabled);
        spouseCategory.setDisable(disabled);
        primaryMortalityAdjustment.setDisable(disabled);
        spouseMortalityAdjustment.setDisable(disabled);
        discountRate.setDisable(disabled);
        pvDate.setDisable(disabled);
    }

    private void showError(Throwable throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Social Security Analysis");
        alert.setHeaderText("Unable to run Social Security strategy analysis");
        alert.setContentText(SocialSecurityAnalysisFailurePresentation.message(throwable));
        alert.showAndWait();
    }

    private void refreshJobState() {
        var state = jobs.state();
        boolean busy = state != SocialSecurityAnalyzerJobController.State.IDLE;
        setAnalysisBusy(busy);
        for (Button button : List.of(socialSecurityCancelButton, integratedCancelButton, exhaustiveCancelButton)) {
            button.setVisible(busy);
            button.setManaged(busy);
            button.setDisable(state != SocialSecurityAnalyzerJobController.State.RUNNING);
        }
        socialSecurityProgress.hide();
        integratedProgress.hide();
        exhaustiveProgress.hide();
        if (jobs.mode() == null) {
            return;
        }
        Label label = switch (jobs.mode()) {
            case SOCIAL_SECURITY -> status;
            case QUICK -> integratedStatus;
            case EXHAUSTIVE, WEIGHTED -> exhaustiveStatus;
        };
        label.setText(jobs.status());
        if (busy) {
            AnalysisProgressView view = switch (jobs.mode()) {
                case SOCIAL_SECURITY -> socialSecurityProgress;
                case QUICK -> integratedProgress;
                case EXHAUSTIVE, WEIGHTED -> exhaustiveProgress;
            };
            view.show(jobs.progress());
        }
    }

    private <T> void observe(javafx.beans.value.ObservableValue<T> value, Runnable action) {
        javafx.beans.value.ChangeListener<T> listener = (observable, oldValue, newValue) -> action.run();
        value.addListener(listener);
        detachInputListeners.add(() -> value.removeListener(listener));
    }

    private void close() {
        jobs.close();
        detachPlanListener.run();
        detachPlanListener = () -> { };
        detachInputListeners.forEach(Runnable::run);
        detachInputListeners.clear();
        socialSecurityProgress.hide();
        integratedProgress.hide();
        exhaustiveProgress.hide();
    }
    private static Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        return label;
    }

    private static Label wrappedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static String money(BigDecimal value) {
        return UIFormatters.money(value);
    }

    private static String signedMoney(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + money(value);
    }

    private static String percent(BigDecimal value) {
        return value.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }

    static SocialSecurityMortalityAdjustment adjustment(
            String text,
            String owner) {
        final BigDecimal factor;
        try {
            factor = new BigDecimal(text.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    owner + " mortality adjustment must be a number from 0.50 to 3.00.");
        }
        if (factor.compareTo(new BigDecimal("0.50")) < 0
                || factor.compareTo(new BigDecimal("3.00")) > 0) {
            throw new IllegalArgumentException(
                    owner + " mortality adjustment must be from 0.50 to 3.00.");
        }
        return SocialSecurityMortalityAdjustment.of(factor);
    }

    private static String factor(SocialSecurityMortalityAdjustment adjustment) {
        return adjustment.factor().setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString() + "x";
    }

    private record RunResult(
            SocialSecurityStrategyAnalysisContext context,
            SocialSecuritySurvivorClaimingOptimizationResult result) {
    }
}
