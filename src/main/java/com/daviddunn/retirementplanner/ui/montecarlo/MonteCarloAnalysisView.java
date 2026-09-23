package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings;
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
    @FunctionalInterface
    public interface RunWork {
        MonteCarloRun run(RetirementPlan plan, MonteCarloSettings settings, Projection reference,
                          AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    private final ApplicationController controller;
    private final Executor worker;
    private final RunWork work;
    private final MonteCarloSession session;
    private final Runnable detach;
    private final ComboBox<Integer> simulations = new ComboBox<>();
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
        super(7);
        this.controller = controller;
        this.worker = worker;
        this.work = work;
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
        table.setId("mc-outcomes");
        run.getStyleClass().add("mc-run");
        expected.setText(controller.getCurrentPlan().getPlanningAssumptions()
                .getExpectedAnnualInvestmentReturn().movePointRight(2).stripTrailingZeros().toPlainString());
        var inputs = new FlowPane(14, 6);
        inputs.getChildren().addAll(
                input("Simulations", simulations, "Number of independent investment-return paths tested. More simulations produce more stable estimates but require more processing time. V1 maximum: 10,000."),
                input("Expected return (%)", expected, "Expected return is the arithmetic mean annual return used to generate simulated yearly returns. With volatility, the median compounded outcome will generally differ from a deterministic projection using the same percentage. Range: -99% to 100%."),
                input("Return volatility (%)", volatility, "Measures year-to-year variation in simulated investment returns. Higher values create a wider range of possible outcomes. Range: 0% to 100%."),
                input("Random seed", seed, "Controls the generated scenarios. Using the same plan, assumptions and seed reproduces the same simulation paths."),
                new VBox(3, new Label(" "), new HBox(8, run, cancel)));
        progress.setPrefWidth(230);
        var progressRow = new HBox(12, progress, status);
        HBox.setHgrow(status, Priority.ALWAYS);
        var probability = new HBox(20, funding, new VBox(3, fundingDetail, failures));
        probability.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        var legend = new FlowPane(18, 2,
                legend("P10–P90", "mc-outer-legend", "mc-outer-band", false),
                legend("P25–P75", "mc-inner-legend", "mc-inner-band", false),
                legend("Median", "mc-median-legend", "mc-median-sample", true),
                legend("Deterministic Projection", "mc-reference-legend", "mc-reference-sample", true));
        var selectedYear = label("", "mc-muted");
        selectedYear.setId("mc-selected-year");
        selectedYear.textProperty().bind(chart.selectedDetailProperty().map(text -> text.replace("\n", " · ")));
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
        results.getChildren().addAll(label("FUNDING PROBABILITY", "mc-section"), probability,
                frozenInputs, label("INVESTABLE ASSETS", "mc-section"), legend, chart, selectedYear,
                label("Annual bands include simulations completing each year. Hover or focus the chart and use Left/Right, Home/End to inspect years. Roth/RMD periods use the deterministic reference.", "mc-muted"),
                referenceNotice, label("ENDING OUTCOMES", "mc-section"), table, notice);
        var details = new TitledPane("Analysis Details", label(
                "Annual returns use independent lognormal gross returns matched to the selected arithmetic expected return and volatility.\n"
                        + "Investment returns are randomized; inflation and Social Security COLA remain deterministic.\n"
                        + "Mortality/death assumptions, claiming elections, Roth strategy and all other plan settings remain those of the current plan.\n"
                        + "Monte Carlo settings are session-only and are not saved into the retirement plan.\n"
                        + "Funding probability measures whether the modeled plan completed the planning horizon without an authoritative funding constraint.\n"
                        + "Funding-constraint shortfalls may involve allocation estimates or owner/account RMD restrictions; they are not necessarily unmet living expenses.\n"
                        + "After-Tax Estate excludes non-investable assets. Lifetime Taxes includes federal and Michigan income taxes.\n"
                        + "Deterministic line and Roth/RMD annotations use the plan's normal projection, not the simulated mean or an optimized strategy.\n"
                        + "Annual sample counts may decline. Ending outcomes include only full-horizon funded simulations.", "mc-muted"));
        details.setExpanded(false);
        details.setId("mc-analysis-details");
        getChildren().addAll(label("Monte Carlo Retirement Analysis", "mc-title"),
                label("Tests the current retirement plan across simulated investment-return paths.", "mc-muted"),
                planTitle, summary, transactions, inputs, validation, progressRow, results, details);
        run.setOnAction(event -> start());
        cancel.setOnAction(event -> session.cancel());
        simulations.valueProperty().addListener((observable, previous, value) -> inputsChanged());
        for (var field : List.of(expected, volatility, seed)) {
            field.textProperty().addListener((observable, previous, value) -> inputsChanged());
        }
        session.onChanged(this::refresh);
        detach = controller.addSourcePlanRevisionListener(() -> {
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
            var settings = new MonteCarloInputs(simulations.getValue(), expected.getText(), volatility.getText(), seed.getText()).settings();
            if (!ProjectionReadiness.isReady(controller.getCurrentPlan())) {
                throw new IllegalArgumentException("Set both birth dates and a projection start date before running analysis.");
            }
            // Capture on FX before the worker starts; later source edits cannot alter this request.
            var snapshot = new RetirementPlanScenarioCopyService().copy(controller.getCurrentPlan());
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
        boolean busy = session.busy();
        run.setDisable(busy);
        cancel.setDisable(session.state() != MonteCarloSession.State.RUNNING);
        simulations.setDisable(busy);
        expected.setDisable(busy);
        volatility.setDisable(busy);
        seed.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
        var update = session.progress();
        progress.setProgress(update == null ? -1 : update.fractionComplete());
        String text = switch (session.state()) {
            case IDLE -> "Ready to analyze the current plan.";
            case RUNNING -> update == null ? "Preparing current-plan reference…"
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
            summary.setText(MonteCarloPresentation.people(completed.people(), completed.lastYear()));
            transactions.setText("Roth Conversions " + MonteCarloPresentation.periods(completed.fan().context().rothPeriods())
                    + " · RMDs " + MonteCarloPresentation.periods(completed.fan().context().rmdPeriods()));
            if (displayed != completed) {
                displayed = completed;
                var result = completed.result();
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
                        + UIFormatters.percent(settings.returnVolatility()) + " · seed " + settings.seed());
                referenceNotice.setText(completed.referenceIncomplete()
                        ? "Deterministic projection encountered a funding constraint; its line and Roth/RMD periods show only completed reference years."
                        : "");
                referenceNotice.setVisible(completed.referenceIncomplete());
                referenceNotice.setManaged(completed.referenceIncomplete());
            }
        } else {
            displayed = null;
            planTitle.setText("CURRENT PLAN");
            var plan = controller.getCurrentPlan();
            int last = plan.getPlanningAssumptions().getProjectionStartDate().getYear()
                    + plan.getPlanningAssumptions().getProjectionLengthYears() - 1;
            summary.setText(MonteCarloPresentation.people(BreakEvenPlanSummary.from(plan.getHousehold()), last));
            transactions.setText("Actual Roth/RMD periods appear after analysis.");
        }
    }

    public MonteCarloSession session() {
        return session;
    }

    private static VBox input(String name, Control control, String help) {
        var label = new Label(name);
        label.setLabelFor(control);
        control.setTooltip(new Tooltip(help));
        control.setAccessibleText(name);
        control.setPrefWidth(name.equals("Random seed") ? 175 : 150);
        return new VBox(3, label, control);
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
