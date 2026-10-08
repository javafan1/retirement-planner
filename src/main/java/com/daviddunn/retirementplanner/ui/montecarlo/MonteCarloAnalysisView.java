package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityRequest;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionReadiness;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls collect inputs and render frozen results; financial work belongs to the worker service.
 */
public final class MonteCarloAnalysisView extends VBox implements AutoCloseable {
    private final MonteCarloPdfExportAction pdf = new MonteCarloPdfExportAction("mc-export-pdf", this::canExportPdf, this::preparePdfReport);
    @FunctionalInterface
    public interface RunWork {
        MonteCarloRun run(RetirementPlan plan, MonteCarloSettings settings, Projection reference,
                          AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    @FunctionalInterface
    public interface MortalityWork {
        MonteCarloRun run(RetirementPlan plan, MonteCarloMortalityRequest request,
                         AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    private final ApplicationController controller;
    private final Executor worker;
    private final RunWork work;
    private final MortalityWork mortalityWork;
    private final MonteCarloSession session;
    private final Runnable detach;
    private final ComboBox<Integer> simulations = new ComboBox<>();
    private final ComboBox<MonteCarloMode> mode = new ComboBox<>();
    private final ComboBox<String> inflationMode = new ComboBox<>();
    private final TextField inflationMean = new TextField();
    private final TextField inflationVolatility = new TextField("1.75");
    private final TextField inflationFloor = new TextField("-2.00");
    private final HBox inflationInputs = new HBox(10);
    private final TextField primaryAdjustment = new TextField();
    private final TextField spouseAdjustment = new TextField();
    private final TextField survivorAge = new TextField();
    private final FlowPane longevityInputs = new FlowPane(14, 4);
    private final Label mortalityContext = label("", "mc-muted");
    private RetirementPlan settingsOwner;
    private final TextField expected = new TextField();
    private final TextField volatility = new TextField("12.00");
    private final TextField seed = new TextField("417");
    private final Button run = new Button("Run Monte Carlo Analysis");
    private final Button cancel = new Button("Cancel");
    private final Label planTitle = label("CURRENT PLAN", "mc-section");
    private final Label summary = label("", "mc-muted");
    private final Label transactions = label("Actual Roth/RMD periods appear after analysis.", "mc-muted");
    private final Label status = label("Ready to analyze the current plan.", "mc-status");
    private final Label validation = label("", "mc-validation");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label funding = label("", "mc-probability");
    private final Label fundingDetail = label("", "mc-muted");
    private final Label failures = label("", "mc-muted");
    private final Label referenceNotice = label("", "mc-muted");
    private final Label notice = label(MonteCarloPresentation.CONDITIONAL_NOTICE, "mc-muted");
    private final Label frozenInputs = label("", "mc-muted");
    private final MonteCarloFanChart chart = new MonteCarloFanChart();
    private final TableView<MonteCarloPresentation.Outcome> table = new TableView<>();
    private final TableView<MonteCarloMortalityPresentation.TerminalRow> mortalityTable = new TableView<>();
    private final Label fundingTitle = label("FUNDING PROBABILITY", "mc-section");
    private final Label outcomesTitle = label("ENDING OUTCOMES", "mc-section");
    private final Label terminalDates = label("", "mc-muted");
    private final Label chartNotice = label("Annual bands include simulations completing each year. Hover or focus the chart and use Left/Right, Home/End to inspect years. Roth/RMD periods use the deterministic reference.", "mc-muted");
    private final Label referenceLegend = legend("Deterministic Projection", "mc-reference-legend", "mc-reference-sample", true);
    private final TitledPane details = new TitledPane();
    private Label fixedDetails;
    private final VBox results = new VBox(5);
    private MonteCarloRun displayed;
    private Throwable logged;

    public MonteCarloAnalysisView(ApplicationController controller) {
        this(controller, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "monte-carlo-analysis");
            thread.setDaemon(true);
            return thread;
        }), new MonteCarloRunService()::run);
    }

    public MonteCarloAnalysisView(ApplicationController controller, Executor worker, RunWork work) {
        this(controller, worker, work, new MonteCarloRunService()::runMortality);
    }

    public MonteCarloAnalysisView(ApplicationController controller, Executor worker, RunWork work, MortalityWork mortalityWork) {
        super(7);
        this.controller = controller;
        this.worker = worker;
        this.work = work;
        this.mortalityWork = mortalityWork;
        this.session = new MonteCarloSession(worker, Platform::runLater);
        setId("monte-carlo-analysis");
        setPadding(new Insets(14, 20, 12, 20));
        setMinWidth(0);
        getStyleClass().add("mc-view");
        for (String file : List.of("results-summary.css", "chart-timeline.css", "projection-chart.css", "monte-carlo.css")) {
            getStylesheets().add(getClass().getResource("/css/" + file).toExternalForm());
        }
        simulations.getItems().setAll(1000, 2500, 5000, 10000);
        simulations.setValue(5000);
        mode.getItems().setAll(MonteCarloMode.values());
        mode.setValue(MonteCarloMode.FIXED_LIFESPAN);
        mode.setId("mc-mode");
        InputHelp.install(mode, "Fixed Lifespan: stochastic investment returns with the configured plan lifetime and horizon. "
                + "Longevity-Adjusted: stochastic investment returns with sampled household longevity. These settings are session-only.");
        mode.setAccessibleText("Analysis mode");
        mode.setPrefWidth(200);
        inflationMode.getItems().setAll("Deterministic", "Stochastic");
        inflationMode.setValue("Deterministic");
        inflationMode.setId("mc-inflation-mode");
        inflationMode.setPrefWidth(140);
        inflationMode.setAccessibleText("Inflation mode");
        InputHelp.install(inflationMode, "Deterministic uses the plan's General Inflation; Stochastic samples annual general spending inflation using the inputs below. Healthcare Inflation, Social Security COLA and other specialized assumptions remain deterministic. Session-only; does not change the saved plan.");
        inflationMean.setId("mc-inflation-mean");
        inflationVolatility.setId("mc-inflation-volatility");
        inflationFloor.setId("mc-inflation-floor");
        inflationInputs.setId("mc-inflation-inputs");
        inflationInputs.getChildren().addAll(
                inlineInput("Expected inflation (%)", inflationMean, "Mean of the underlying normal annual spending inflation distribution; initialized from the plan."),
                inlineInput("Volatility (%)", inflationVolatility, "Annual inflation standard deviation. V1 default 1.75% is an editable modeling assumption."),
                inlineInput("Floor (%)", inflationFloor, "Minimum annual inflation. V1 default -2% permits mild deflation. Must not exceed the mean and must exceed -100%."));
        primaryAdjustment.setId("mc-primary-adjustment");
        spouseAdjustment.setId("mc-spouse-adjustment");
        survivorAge.setId("mc-survivor-age");
        survivorAge.setPromptText("Select a whole-year age");
        longevityInputs.setId("mc-longevity-inputs");
        mortalityContext.setId("mc-mortality-context");
        restoreLongevitySettings();
        longevityInputs.getChildren().addAll(
                input("Primary mortality adjustment", primaryAdjustment, MonteCarloMortalityPresentation.ADJUSTMENT_HELP),
                input("Spouse mortality adjustment", spouseAdjustment, MonteCarloMortalityPresentation.ADJUSTMENT_HELP),
                input("Survivor Social Security Claiming Age", survivorAge,
                        "Used when either spouse survives the other in longevity simulations. This analysis assumption "
                                + "does not change either person's regular Social Security claiming age or the saved retirement plan. "
                                + "Enter a whole-year age of at least 60."));
        survivorAge.setPrefWidth(245);
        longevityInputs.setPrefWrapLength(670);
        longevityInputs.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        simulations.setId("mc-simulations");
        expected.setId("mc-return");
        volatility.setId("mc-volatility");
        seed.setId("mc-seed");
        run.setId("mc-run");
        cancel.setId("mc-cancel");
        status.setId("mc-status");
        progress.setId("mc-progress");
        notice.setId("mc-conditional-notice");
        funding.setId("mc-funding");
        fundingTitle.setId("mc-funding-title");
        outcomesTitle.setId("mc-outcomes-title");
        terminalDates.setId("mc-terminal-dates");
        chartNotice.setId("mc-chart-notice");
        validation.setId("mc-validation");
        table.setId("mc-outcomes");
        run.getStyleClass().add("mc-run");
        expected.setText(controller.getCurrentPlan().getPlanningAssumptions()
                .getExpectedAnnualInvestmentReturn().movePointRight(2).stripTrailingZeros().toPlainString());
        var inputs = new FlowPane(14, 6);
        inputs.getChildren().addAll(
                input("Simulations", simulations, "Number of independent investment-return paths tested. More simulations produce more stable estimates but require more processing time. V1 maximum: 10,000."),
                input("Expected return (%)", expected, "Expected return is the arithmetic mean annual return used to generate simulated yearly returns. With volatility, the median compounded outcome will generally differ from a deterministic projection using the same percentage. Range: -99% to 100%."),
                input("Return volatility (%)", volatility, "Annual standard deviation of simulated investment returns, entered as a percentage. Higher values create a wider range of yearly returns. Range: 0% to 100%."),
                input("Random seed", seed, "Controls the generated scenarios. Using the same plan, assumptions and seed reproduces the same simulation paths."),
                longevityInputs,
                new VBox(3, new Label(" "), new HBox(8, run, cancel, pdf.button())));
        progress.setPrefWidth(230);
        var progressRow = new HBox(12, progress, status);
        HBox.setHgrow(status, Priority.ALWAYS);
        var probability = new HBox(20, funding, new VBox(3, fundingDetail, failures));
        probability.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var legend = new FlowPane(18, 2,
                legend("P10–P90", "mc-outer-legend", "mc-outer-band", false),
                legend("P25–P75", "mc-inner-legend", "mc-inner-band", false),
                legend("Median", "mc-median-legend", "mc-median-sample", true),
                referenceLegend);
        var selectedYear = label("", "mc-muted");
        selectedYear.setId("mc-selected-year");
        selectedYear.textProperty().bind(chart.selectedSummaryProperty());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(29);
        table.setPrefHeight(150);
        table.setMinHeight(150);
        table.setMaxHeight(150);
        TableColumn<MonteCarloPresentation.Outcome, String> name = new TableColumn<>("Outcome");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        name.setMinWidth(175);
        table.getColumns().add(name);
        List<String> headers = List.of("Minimum", "P10", "P25", "Median", "P75", "P90", "Maximum");
        for (int index = 0; index < headers.size(); index++) {
            final int position = index;
            TableColumn<MonteCarloPresentation.Outcome, String> column = new TableColumn<>(headers.get(index));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().percentiles()
                    .map(value -> UIFormatters.money(MonteCarloPresentation.quantiles(value).get(position)))
                    .orElse("Unavailable")));
            column.setMinWidth(110);
            column.setStyle("-fx-alignment: CENTER-RIGHT;");
            table.getColumns().add(column);
        }
        table.getColumns().forEach(column -> column.setSortable(false));
        configureMortalityTable();
        results.getChildren().addAll(fundingTitle, probability,
                frozenInputs, label("INVESTABLE ASSETS", "mc-section"), legend, chart, selectedYear,
                chartNotice, referenceNotice, new FlowPane(18, 2, outcomesTitle, terminalDates), table, mortalityTable, notice);
        fixedDetails = label(
                "Annual returns use independent lognormal gross returns matched to the selected arithmetic expected return and volatility.\n"
                        + "Investment returns are randomized; general spending inflation uses the selected mode. Social Security COLA remains deterministic.\n"
                        + "Mortality/death assumptions, claiming elections, Roth strategy and all other plan settings remain those of the current plan.\n"
                        + "Monte Carlo settings are session-only and are not saved into the retirement plan.\n"
                        + "Funding probability measures whether the modeled plan completed the planning horizon without an authoritative funding constraint.\n"
                        + "Funding-constraint shortfalls may involve allocation estimates or owner/account RMD restrictions; they are not necessarily unmet living expenses.\n"
                        + "After-Tax Estate excludes non-investable assets. Lifetime Taxes includes federal and Michigan income taxes.\n"
                        + "Deterministic line and Roth/RMD annotations use the plan's normal projection, not the simulated mean or an optimized strategy.\n"
                        + "Annual sample counts may decline. Ending outcomes include only full-horizon funded simulations.", "mc-muted");
        details.setText("Analysis Details");
        details.setContent(fixedDetails);
        details.setExpanded(false);
        details.setId("mc-analysis-details");
        var modeLabel = new Label("Analysis mode");
        modeLabel.setLabelFor(mode);
        var inflationLabel = new Label("Inflation");
        inflationLabel.setLabelFor(inflationMode);
        InputHelp.link(modeLabel, mode);
        InputHelp.link(inflationLabel, inflationMode);
        var heading = new FlowPane(16, 5, label("Monte Carlo Retirement Analysis", "mc-title"), modeLabel, mode,
                inflationLabel, inflationMode, inflationInputs);
        heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        getChildren().addAll(heading,
                label("Tests the current retirement plan across simulated investment-return paths.", "mc-muted"),
                planTitle, summary, transactions, inputs, mortalityContext, validation, progressRow, results, details);
        run.setOnAction(event -> start());
        cancel.setOnAction(event -> session.cancel());
        simulations.valueProperty().addListener((observable, previous, value) -> inputsChanged());
        mode.valueProperty().addListener((observable, previous, value) -> inputsChanged());
        inflationMode.valueProperty().addListener((observable, previous, value) -> inputsChanged());
        survivorAge.textProperty().addListener((observable, previous, value) -> inputsChanged());
        for (var field : List.of(expected, volatility, seed, inflationMean, inflationVolatility, inflationFloor)) {
            field.textProperty().addListener((observable, previous, value) -> inputsChanged());
        }
        for (var field : List.of(primaryAdjustment, spouseAdjustment)) {
            field.textProperty().addListener((observable, previous, value) -> {
                shareLongevitySettings();
                inputsChanged();
            });
        }
        session.onChanged(this::refresh);
        detach = controller.addSourcePlanRevisionListener(() -> {
            if (settingsOwner != controller.getCurrentPlan()) {
                restoreLongevitySettings();
            }
            session.invalidate();
            refresh();
        });
        refresh();
    }

    private void inputsChanged() {
        session.invalidate();
        validation.setText("");
        refresh();
    }

    private void start() {
        if (session.busy()) {
            return;
        }
        try {
            var settings = new MonteCarloInputs(simulations.getValue(), expected.getText(), volatility.getText(), seed.getText())
                    .settings("Stochastic".equals(inflationMode.getValue()), inflationMean.getText(),
                            inflationVolatility.getText(), inflationFloor.getText());
            if (!ProjectionReadiness.isReady(controller.getCurrentPlan())) {
                throw new IllegalArgumentException("Set birth dates for the people present and a projection start date before running analysis.");
            }
            // Capture on FX before the worker starts; later source edits cannot alter this request.
            var snapshot = new RetirementPlanScenarioCopyService().copy(controller.getCurrentPlan());
            if (mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED) {
                MonteCarloMortalityRequest request;
                if (snapshot.getHousehold().hasSpouse()) {
                    int age = MonteCarloMortalityPresentation.survivorClaimingAge(survivorAge.getText());
                    var longevity = MonteCarloMortalityPresentation.settings(snapshot, primaryAdjustment.getText(), spouseAdjustment.getText());
                    request = new MonteCarloMortalityRequest(snapshot, settings, longevity, age);
                } else {
                    request = MonteCarloMortalityRequest.individual(snapshot, settings,
                            MonteCarloMortalityPresentation.adjustment(primaryAdjustment.getText(), "Primary"));
                }
                validation.setText("");
                session.start((updates, cancellation) -> mortalityWork.run(snapshot, request, updates, cancellation));
                return;
            }
            Projection cached = controller.peekCurrentProjection();
            Projection reference = null;
            if (cached != null) {
                reference = new Projection();
                cached.getYears().forEach(reference::addYear);
            }
            final Projection frozenReference = reference;
            validation.setText("");
            session.start((updates, cancellation) -> work.run(snapshot, settings, frozenReference, updates, cancellation));
        } catch (RuntimeException exception) {
            validation.setText(exception.getMessage());
            validation.setManaged(true);
            validation.setVisible(true);
        }
    }

    private void refresh() {
        pdf.refresh();
        boolean busy = session.busy();
        run.setDisable(busy);
        cancel.setDisable(session.state() != MonteCarloSession.State.RUNNING);
        simulations.setDisable(busy);
        expected.setDisable(busy);
        volatility.setDisable(busy);
        seed.setDisable(busy);
        mode.setDisable(busy);
        inflationMode.setDisable(busy);
        inflationInputs.setDisable(busy);
        show(inflationInputs, "Stochastic".equals(inflationMode.getValue()));
        primaryAdjustment.setDisable(busy);
        spouseAdjustment.setDisable(busy);
        survivorAge.setDisable(busy);
        boolean couple = controller.getCurrentPlan().getHousehold().hasSpouse();
        if (spouseAdjustment.getParent() != null) show(spouseAdjustment.getParent(), couple);
        if (survivorAge.getParent() != null) show(survivorAge.getParent(), couple);
        pdf.button().setTooltip(new Tooltip(couple ? "Export the completed frozen result. Run again if the result is stale."
                : "Export the completed frozen result. Run again if stale."));
        show(longevityInputs, mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED);
        show(mortalityContext, mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED);
        updateMortalityContext();
        progress.setVisible(busy);
        progress.setManaged(busy);
        var update = session.progress();
        progress.setProgress(update == null ? -1 : update.fractionComplete());
        String text = switch (session.state()) {
            case IDLE -> "Ready to analyze the current plan.";
            case RUNNING -> update == null ? (mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED
                    ? "Preparing market and lifetime scenarios…" : "Preparing current-plan reference…")
                    : String.format("Running Monte Carlo Analysis…  %,d / %,d simulations  ·  %d%%",
                    update.completedWork(), update.totalWork(), update.wholePercent());
            case CANCELLING -> "Cancelling after the current simulation…";
            case CANCELLED -> "Analysis cancelled. No partial result was published.";
            case FAILED -> "Analysis failed. " + session.failure().getMessage();
            case COMPLETED -> "Analysis complete.";
            case CLOSED -> "";
        };
        if (session.stale()) {
            text = "Inputs or plan changed — run analysis again. " + text;
        }
        status.setText(text);
        if (session.failure() != null && logged != session.failure()) {
            logged = session.failure();
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "Monte Carlo analysis failed", logged);
        }
        boolean hasResult = session.result() != null;
        results.setVisible(hasResult);
        results.setManaged(hasResult);
        validation.setVisible(!validation.getText().isBlank());
        validation.setManaged(!validation.getText().isBlank());
        if (hasResult) {
            var completed = session.result();
            planTitle.setText(session.stale() ? "ANALYZED PLAN · STALE RESULT" : "CURRENT PLAN");
            boolean mortality = completed.mode() == MonteCarloMode.LONGEVITY_ADJUSTED;
            summary.setText(mortality ? "Longevity-Adjusted result · " + completed.people().primary().name()
                    + (completed.people().hasSpouse() ? " / " + completed.people().spouse().name() : "") + " · "
                    + completed.mortalityResult().lastReportingYear().map(last -> "Sampled financial years "
                            + completed.firstYear() + "–" + last).orElse("All sampled lifetimes end at opening")
                    : MonteCarloPresentation.people(completed.people(), completed.lastYear()));
            transactions.setText("Roth Conversions " + MonteCarloPresentation.periods(completed.fan().context().rothPeriods())
                    + " · RMDs " + MonteCarloPresentation.periods(completed.fan().context().rmdPeriods()));
            show(transactions, !mortality);
            if (displayed != completed) {
                displayed = completed;
                configureResultMode(mortality);
                if (mortality) {
                    renderMortality(completed);
                } else {
                    renderFixed(completed);
                }
            }
        } else {
            displayed = null;
            planTitle.setText("CURRENT PLAN");
            var plan = controller.getCurrentPlan();
            int last = plan.getPlanningAssumptions().getProjectionStartDate().getYear()
                    + plan.getPlanningAssumptions().getProjectionLengthYears() - 1;
            summary.setText(MonteCarloPresentation.people(BreakEvenPlanSummary.from(plan.getHousehold()), last));
            transactions.setText("Actual Roth/RMD periods appear after analysis.");
            show(transactions, mode.getValue() == MonteCarloMode.FIXED_LIFESPAN);
            details.setContent(mode.getValue() == MonteCarloMode.FIXED_LIFESPAN ? fixedDetails
                    : label(MonteCarloMortalityPresentation.ANNUAL_NOTICE + "\n"
                            + MonteCarloMortalityPresentation.lifetimeText(MonteCarloMortalityPresentation.NOMINAL_NOTICE, couple), "mc-muted"));
        }
    }

    private void renderFixed(MonteCarloRun completed) {
        var result = completed.result();
        details.setContent(label(fixedDetails.getText() + "\n"
                + MonteCarloPresentation.inflationDetails(completed.settings()), "mc-muted"));
        funding.setText(MonteCarloPresentation.fundingPercent(result));
        fundingDetail.setText(MonteCarloPresentation.fundingDetail(result));
        failures.setText(MonteCarloPresentation.failureDetail(result));
        failures.setVisible(result.fundingFailureCount() > 0);
        failures.setManaged(result.fundingFailureCount() > 0);
        chart.load(completed.fan());
        table.getItems().setAll(MonteCarloPresentation.outcomes(result));
        notice.setText((result.completedCount() == 0 ? "No simulations completed the full horizon. " : "")
                + MonteCarloPresentation.CONDITIONAL_NOTICE);
        var settings = result.settings();
        frozenInputs.setText("Run inputs: " + settings.simulationCount() + " simulations · arithmetic mean "
                + UIFormatters.percent(settings.expectedReturn()) + " · volatility "
                + UIFormatters.percent(settings.returnVolatility()) + " · seed " + settings.seed() + " · " + MonteCarloPresentation.inflationSummary(settings));
        referenceNotice.setText(completed.referenceIncomplete()
                ? "Deterministic projection encountered a funding constraint; its line and Roth/RMD periods show only completed reference years."
                : "");
        referenceNotice.setVisible(completed.referenceIncomplete());
        referenceNotice.setManaged(completed.referenceIncomplete());
    }

    private void restoreLongevitySettings() {
        settingsOwner = controller.getCurrentPlan();
        inflationMean.setText(settingsOwner.getPlanningAssumptions().getGeneralInflationRate()
                .movePointRight(2).stripTrailingZeros().toPlainString());
        inflationMode.setValue("Deterministic");
        inflationVolatility.setText("1.75");
        inflationFloor.setText("-2.00");
        var settings = controller.getLongevitySessionSettings();
        primaryAdjustment.setText(settings.primaryAdjustment().factor().toPlainString());
        spouseAdjustment.setText(settings.spouseAdjustment().factor().toPlainString());
        var age = settingsOwner.getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge();
        survivorAge.setText(age == null ? "" : age.toString());
    }

    private void shareLongevitySettings() {
        try {
            if (!controller.getCurrentPlan().getHousehold().hasSpouse()) {
                var previous = controller.getLongevitySessionSettings();
                controller.setLongevitySessionSettings(new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings(
                        previous.conditioningDate(), MonteCarloMortalityPresentation.adjustment(primaryAdjustment.getText(), "Primary"),
                        previous.spouseAdjustment()));
                return;
            }
            var parsed = MonteCarloMortalityPresentation.settings(controller.getCurrentPlan(),
                    primaryAdjustment.getText(), spouseAdjustment.getText());
            // Preserve the other analyzer's independent conditioning date; MC conditions at plan start.
            controller.setLongevitySessionSettings(new com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings(
                    controller.getLongevitySessionSettings().conditioningDate(), parsed.primaryAdjustment(), parsed.spouseAdjustment()));
        } catch (IllegalArgumentException ignored) {
            // Intermediate edits are validated by Run; only valid settings are shared.
        }
    }

    private void updateMortalityContext() {
        var plan = controller.getCurrentPlan();
        var people = BreakEvenPlanSummary.from(plan.getHousehold());
        var primary = people.primary();

        mortalityContext.setText(primary.name() + " mortality: "
                + mortalityCategory(primary.mortalityCategory())
                + (people.hasSpouse() ? " · " + people.spouse().name() + " mortality: "
                + mortalityCategory(people.spouse().mortalityCategory()) : "")
                + " · Conditioning: " + plan.getPlanningAssumptions().getProjectionStartDate());
    }

    private static String mortalityCategory(com.daviddunn.retirementplanner.domain.model.MortalityCategory category) {
        return category == null ? "Not set" : switch (category) {
            case MALE -> "Male";
            case FEMALE -> "Female";
        };
    }

    private void configureMortalityTable() {
        mortalityTable.setId("mc-lifetime-outcomes");
        mortalityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        mortalityTable.setFixedCellSize(23);
        mortalityTable.setMinHeight(190);
        mortalityTable.setPrefHeight(190);
        mortalityTable.setMaxHeight(190);
        TableColumn<MonteCarloMortalityPresentation.TerminalRow, String> name = new TableColumn<>("Percentile");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().percentile()));
        mortalityTable.getColumns().add(name);
        var headers = List.of("Investable Assets", "Total Net Worth", "After-Tax Estate", "Lifetime Taxes");
        for (int i = 0; i < headers.size(); i++) {
            int index = i;
            TableColumn<MonteCarloMortalityPresentation.TerminalRow, String> column = new TableColumn<>(headers.get(i));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().values().get(index)));
            column.setStyle("-fx-alignment: CENTER-RIGHT;");
            column.setMinWidth(150);
            mortalityTable.getColumns().add(column);
        }
        mortalityTable.getColumns().forEach(column -> column.setSortable(false));
    }

    private void configureResultMode(boolean mortality) {
        fundingTitle.setText(mortality ? "LIFETIME FUNDING PROBABILITY" : "FUNDING PROBABILITY");
        funding.setTooltip(mortality ? new Tooltip(MonteCarloMortalityPresentation.lifetimeText(MonteCarloMortalityPresentation.FUNDING_HELP, displayed.people().hasSpouse())) : null);
        outcomesTitle.setText(mortality ? "LIFETIME OUTCOMES — FUNDED SIMULATIONS" : "ENDING OUTCOMES");
        chartNotice.setText(mortality ? MonteCarloMortalityPresentation.ANNUAL_NOTICE
                : "Annual bands include simulations completing each year. Hover or focus the chart and use Left/Right, Home/End to inspect years. Roth/RMD periods use the deterministic reference.");
        show(referenceLegend, !mortality);
        show(table, !mortality);
        show(mortalityTable, mortality);
        show(terminalDates, mortality);
        chart.setPrefHeight(mortality ? 330 : 370);
        // Accommodate the two-line mortality readout without shrinking the chart or terminal table.
        results.setSpacing(mortality ? 3 : 5);
        if (!mortality) {
            details.setContent(fixedDetails);
        }
    }

    private void renderMortality(MonteCarloRun completed) {
        var result = completed.mortalityResult();
        funding.setText(MonteCarloPresentation.fundingPercent(result.fundingProbability(),
                result.fundingFailureCount(), result.completedCount()));
        fundingDetail.setText(MonteCarloMortalityPresentation.fundingDetail(result));
        failures.setText(String.format("%,d simulations encountered a funding constraint.", result.fundingFailureCount()));
        show(failures, result.fundingFailureCount() > 0);
        chart.load(completed.fan());
        mortalityTable.getItems().setAll(MonteCarloMortalityPresentation.terminalRows(result));
        show(mortalityTable, result.completedCount() > 0);
        terminalDates.setText(MonteCarloMortalityPresentation.terminalDates(result));
        notice.setText(MonteCarloMortalityPresentation.lifetimeText(result.completedCount() == 0 ? MonteCarloMortalityPresentation.NO_TERMINALS
                : MonteCarloMortalityPresentation.NOMINAL_NOTICE, result.request().hasSpouse()));
        var settings = completed.settings();
        frozenInputs.setText("Run inputs: " + settings.simulationCount() + " simulations · arithmetic mean "
                + UIFormatters.percent(settings.expectedReturn()) + " · volatility "
                + UIFormatters.percent(settings.returnVolatility()) + " · seed " + settings.seed() + " · " + MonteCarloPresentation.inflationSummary(settings));
        referenceNotice.setText(result.annualResults().isEmpty() ? "No annual financial rows: all sampled lifetimes end at opening." : "");
        show(referenceNotice, result.annualResults().isEmpty());
        var content = new VBox(6);
        for (var section : MonteCarloMortalityPresentation.details(completed)) {
            content.getChildren().add(new VBox(2, label(section.title(), "mc-section"), label(section.text(), "mc-muted")));
        }
        content.getChildren().add(label(MonteCarloPresentation.inflationDetails(completed.settings()), "mc-muted"));
        details.setContent(content);
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    public MonteCarloSession session() {
        return session;
    }

    public boolean canExportPdf() {
        return session != null && session.state() == MonteCarloSession.State.COMPLETED && !session.stale() && session.result() != null;
    }

    public com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed Monte Carlo result is required for PDF export.");
        return MonteCarloPdfReportAdapter.from(session.result());
    }

    public void exportPdf(java.nio.file.Path destination) throws java.io.IOException {
        new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(preparePdfReport(), destination);
    }

    private static VBox input(String name, Control control, String help) {
        var label = new Label(name);
        InputHelp.install(control, help);
        InputHelp.link(label, control);
        control.setAccessibleText(name);
        control.setPrefWidth(name.equals("Random seed") ? 175 : 150);
        return new VBox(3, label, control);
    }

    private static HBox inlineInput(String name, TextField control, String help) {
        var label = new Label(name);
        control.setAccessibleText(name);
        InputHelp.install(control, help);
        InputHelp.link(label, control);
        control.setPrefWidth(66);
        var row = new HBox(5, label, control);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Label legend(String text, String style, String sampleStyle, boolean line) {
        var label = label(text, style);
        javafx.scene.shape.Shape sample = line
                ? new javafx.scene.shape.Line(0, 0, 26, 0)
                : new javafx.scene.shape.Rectangle(26, 12);
        sample.getStyleClass().add(sampleStyle);
        label.setGraphic(sample);
        label.setGraphicTextGap(6);
        return label;
    }

    @Override
    public void close() {
        session.close();
        detach.run();
        if (worker instanceof ExecutorService service) {
            service.shutdown();
        }
    }
}
