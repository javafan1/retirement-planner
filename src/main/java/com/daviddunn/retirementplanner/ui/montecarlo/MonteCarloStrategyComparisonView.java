package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloStrategyComparisonPresentation.*;

/** Session-only controls and rendering; captures on FX and runs the existing analyzer on a worker. */
public final class MonteCarloStrategyComparisonView extends VBox implements AutoCloseable {
    private final MonteCarloPdfExportAction pdf = new MonteCarloPdfExportAction("mcc-export-pdf", this::canExportPdf, this::preparePdfReport);
    @FunctionalInterface
    public interface Work {
        MonteCarloStrategyComparisonRun run(MonteCarloStrategyComparisonRunService.Prepared input,
                AnalysisProgressListener progress, AnalysisCancellationToken cancellation);
    }

    private final ApplicationController controller;
    private final Executor worker;
    private final Work work;
    private final MonteCarloStrategyComparisonSession session;
    private final Runnable detach;
    private final ComboBox<Integer> count = new ComboBox<>();
    private final ComboBox<MonteCarloMode> mode = new ComboBox<>();
    private final ComboBox<String> inflation = new ComboBox<>();
    private final TextField expected = new TextField();
    private final TextField volatility = new TextField("12.00");
    private final TextField seed = new TextField("417");
    private final TextField inflationMean = new TextField();
    private final TextField inflationVolatility = new TextField("1.75");
    private final TextField inflationFloor = new TextField("-2.00");
    private final TextField primary = new TextField();
    private final TextField spouse = new TextField();
    private final TextField survivorA = new TextField();
    private final TextField survivorB = new TextField();
    private final FlowPane controls = new FlowPane(14, 5);
    private final FlowPane inflationControls = new FlowPane(14, 5);
    private final FlowPane longevityControls = new FlowPane(14, 5);
    private final Label context = label("", "mc-muted", "mcc-context");
    private final Button run = new Button("Run Strategy Comparison");
    private final Button cancel = new Button("Cancel");
    private final Label status = label("Ready to compare.", "mc-status", "mcc-status");
    private final Label validation = label("", "mc-validation", "mcc-validation");
    private final ProgressBar progress = new ProgressBar();
    private final VBox results = new VBox(2);
    private final Label identity = label("", "mc-section", "mcc-identity");
    private final Label fundingA = label("", "mcc-funding", "mcc-funding-a");
    private final Label fundingB = label("", "mcc-funding", "mcc-funding-b");
    private final Label fundingDifference = label("", "mcc-funding", "mcc-funding-difference");
    private final Label states = label("", "mc-muted", "mcc-states");
    private final Label denominator = label("", "mc-status", "mcc-denominator");
    private final Label terminalNotice = label("", "mc-muted", "mcc-terminal-notice");
    private final TableView<Metric> table = new TableView<>();
    private final Label quantiles = label("", "mc-status", "mcc-quantiles");
    private final Label relations = label("", "mc-muted", "mcc-relations");
    private final Label direction = label("", "mc-muted", "mcc-direction");
    private final MonteCarloDifferenceChart chart = new MonteCarloDifferenceChart();
    private final TitledPane details = new TitledPane();
    private MonteCarloStrategyComparisonRun displayed;

    public MonteCarloStrategyComparisonView(ApplicationController controller) {
        this(controller, Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "monte-carlo-comparison"); thread.setDaemon(true); return thread;
        }), new MonteCarloStrategyComparisonRunService()::run);
    }

    public MonteCarloStrategyComparisonView(ApplicationController controller, Executor worker, Work work) {
        super(3);
        this.controller = controller;
        this.worker = worker;
        this.work = work;
        session = new MonteCarloStrategyComparisonSession(worker, Platform::runLater);
        setId("monte-carlo-strategy-comparison");
        setPadding(new Insets(12, 20, 12, 20));
        setMinWidth(0);
        getStyleClass().add("mc-view");
        getStylesheets().add(getClass().getResource("/css/monte-carlo.css").toExternalForm());
        count.getItems().setAll(1000, 2500, 5000, 10000); count.setValue(5000);
        mode.getItems().setAll(MonteCarloMode.values()); mode.setValue(MonteCarloMode.FIXED_LIFESPAN);
        inflation.getItems().setAll("Deterministic", "Stochastic"); inflation.setValue("Deterministic");
        var plan = controller.getCurrentPlan();
        var p = plan.getPlanningAssumptions();
        expected.setText(p.getExpectedAnnualInvestmentReturn().movePointRight(2).toPlainString());
        inflationMean.setText(p.getGeneralInflationRate().movePointRight(2).toPlainString());
        primary.setText(controller.getLongevitySessionSettings().primaryAdjustment().factor().toPlainString());
        spouse.setText(controller.getLongevitySessionSettings().spouseAdjustment().factor().toPlainString());
        survivorA.setText(age(p));
        survivorB.setText(plan.getBaseline() == null ? "" : age(plan.getBaseline().getSnapshot().getPlanningAssumptions()));
        controls.getChildren().addAll(input("Simulations", count, "count", "One paired world is one simulation. Maximum 10,000."),
                input("Expected return (%)", expected, "return", "Arithmetic mean annual return, entered as a percentage and shared by both strategies. With volatility, median compounded outcomes generally differ from a deterministic projection using the same percentage. Session-only assumption."),
                input("Return volatility (%)", volatility, "volatility", "Annual return standard deviation."),
                input("Seed", seed, "seed", "Same seed and captured inputs reproduce the same worlds."),
                input("Mode", mode, "mode", "Fixed uses the Current Plan horizon and shared configured deaths; longevity samples shared lifetimes."),
                input("General inflation", inflation, "inflation", "Stochastic inflation uses the same annual path for both strategies; deterministic uses each plan's assumption."));
        inflationControls.getChildren().addAll(input("Inflation mean (%)", inflationMean, "inflation-mean", "Underlying normal mean; initialized from Current Plan."),
                input("Inflation volatility (%)", inflationVolatility, "inflation-volatility", "Annual standard deviation of the underlying normal general spending inflation distribution, entered as a percentage. Used only for Stochastic inflation; healthcare inflation remains deterministic."),
                input("Inflation floor (%)", inflationFloor, "inflation-floor", "Minimum annual general spending inflation percentage. Draws below this floor are raised to it, which can change the realized mean. Used only for Stochastic inflation; must exceed -100% and not exceed the mean."));
        longevityControls.getChildren().addAll(input("Primary mortality factor", primary, "primary", MonteCarloMortalityPresentation.ADJUSTMENT_HELP),
                input("Spouse mortality factor", spouse, "spouse", MonteCarloMortalityPresentation.ADJUSTMENT_HELP),
                input("Current survivor SS age", survivorA, "survivor-a", "Whole-year survivor-benefit claiming age, at least 60, for either surviving person in Current Plan longevity simulations. Separate from retirement claiming age; initialized from its saved election and kept only for this analysis."),
                input("Baseline survivor SS age", survivorB, "survivor-b", "Whole-year survivor-benefit claiming age, at least 60, for either surviving person in Saved Baseline longevity simulations. Separate from retirement claiming age; initialized from its saved election and kept only for this analysis."));
        run.setId("mcc-run"); run.getStyleClass().add("mc-run");
        cancel.setId("mcc-cancel"); progress.setId("mcc-progress"); progress.setPrefWidth(160);
        var actions = new FlowPane(12, 4, run, cancel, pdf.button(), progress, status, context);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        table.setId("mcc-metrics");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(26); table.setMinHeight(130); table.setPrefHeight(130); table.setMaxHeight(130);
        column("Metric", Metric::name, 245);
        column("Median A − B", Metric::median, 140);
        column("Mean A − B", Metric::mean, 140);
        column("P(Current > Baseline)", Metric::greater, 180);
        column("Comparable samples", m -> String.format("%,d", m.summary().sampleCount()), 140);
        table.setAccessibleText("Paired financial differences; Probability Current Plan exceeds Saved Baseline; select a metric for percentiles and relation probabilities");
        table.getSelectionModel().selectedItemProperty().addListener((o, before, value) -> metric(value));
        var help = new FlowPane(12, 2);
        for (String name : List.of("P10", "P25", "Median", "P75", "P90")) {
            var label = new Label(name + " ⓘ"); label.setTooltip(new Tooltip(percentileHelp(name)));
            label.setAccessibleText(percentileHelp(name)); help.getChildren().add(label);
        }
        var selected = label("", "mc-status", "mcc-selected-year");
        selected.textProperty().bind(chart.selectedYearProperty());
        var warning = label("", "mc-muted", "mcc-small-sample");
        warning.textProperty().bind(chart.warningProperty());
        warning.visibleProperty().bind(warning.textProperty().isNotEmpty());
        warning.managedProperty().bind(warning.visibleProperty());
        var metricCard = new VBox(3, table, new FlowPane(16, 2, quantiles, help), new FlowPane(16, 2, relations, direction));
        var funding = new HBox(28, fundingColumn("Current Plan", fundingA),
                fundingColumn("Saved Baseline", fundingB), fundingColumn("Difference · Current − Baseline", fundingDifference));
        funding.setId("mcc-funding");
        results.getChildren().addAll(identity, section("1  FUNDING RELIABILITY"), funding, states,
                section("2  FINANCIAL OUTCOME DIFFERENCES"), denominator, terminalNotice, metricCard,
                section("3  INVESTABLE ASSETS DIFFERENCE OVER TIME"), label(ANNUAL, "mc-muted", null),
                label(AXIS + "  Bands: P10–P90 / P25–P75; solid line: Median. Hover, click, or use Left/Right and Home/End.", "mc-muted", null),
                chart, selected, warning);
        details.setText("Analysis Details"); details.setId("mcc-details"); details.setExpanded(false);
        details.setContent(label("Run a comparison to capture analysis details.", "mc-muted", null));
        inflationControls.setPrefWrapLength(500);
        longevityControls.setPrefWrapLength(700);
        var optionalInputs = new FlowPane(16, 4, inflationControls, longevityControls);
        optionalInputs.visibleProperty().bind(inflationControls.visibleProperty().or(longevityControls.visibleProperty()));
        optionalInputs.managedProperty().bind(optionalInputs.visibleProperty());
        getChildren().addAll(label("Monte Carlo Strategy Comparison", "mc-title", null),
                label(PAIRED, "mc-muted", null), controls, optionalInputs,
                validation, actions, results, details);
        run.setOnAction(event -> start()); cancel.setOnAction(event -> session.cancel());
        for (var field : List.of(expected, volatility, seed, inflationMean, inflationVolatility, inflationFloor, primary, spouse, survivorA, survivorB)) {
            field.textProperty().addListener((o, before, after) -> edited());
        }
        count.valueProperty().addListener((o, before, after) -> edited());
        mode.valueProperty().addListener((o, before, after) -> edited());
        inflation.valueProperty().addListener((o, before, after) -> edited());
        session.onChanged(this::refresh);
        detach = controller.addSourcePlanRevisionListener(() -> { session.invalidate(); refresh(); });
        refresh();
    }

    private static String age(com.daviddunn.retirementplanner.domain.model.PlanningAssumptions p) {
        var age = p.getDeathScenarioAssumptions().getSurvivorClaimingAge(); return age == null ? "" : age.toString();
    }

    private void edited() { validation.setText(""); session.invalidate(); refresh(); }

    private void start() {
        if (session.busy()) return;
        try {
            var settings = new MonteCarloInputs(count.getValue(), expected.getText(), volatility.getText(), seed.getText())
                    .settings("Stochastic".equals(inflation.getValue()), inflationMean.getText(), inflationVolatility.getText(), inflationFloor.getText());
            var captured = MonteCarloStrategyComparisonRunService.capture(controller.getCurrentPlan(), settings, mode.getValue(),
                    primary.getText(), spouse.getText(), survivorA.getText(), survivorB.getText());
            validation.setText("");
            session.start((updates, token) -> work.run(captured, updates, token));
        } catch (RuntimeException exception) { validation.setText(exception.getMessage()); refresh(); }
    }

    private void refresh() {
        pdf.refresh();
        boolean busy = session.busy();
        controls.setDisable(busy); inflationControls.setDisable(busy); longevityControls.setDisable(busy);
        boolean available = MonteCarloStrategyComparisonRunService.available(controller.getCurrentPlan());
        run.setDisable(busy || !available);
        cancel.setDisable(session.state() != MonteCarloStrategyComparisonSession.State.RUNNING);
        show(inflationControls, "Stochastic".equals(inflation.getValue()));
        show(longevityControls, mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED);
        var plan = controller.getCurrentPlan();
        context.setText(!available ? MonteCarloStrategyComparisonRunService.MISSING_BASELINE
                : mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED
                ? "Mortality categories (from plan): " + plan.getHousehold().getPrimaryPerson().getMortalityCategory()
                    + " / " + plan.getHousehold().getSpouse().getMortalityCategory() + " · Conditioning date: "
                    + plan.getPlanningAssumptions().getProjectionStartDate() + " (projection start). Survivor ages shown apply only to this run."
                : "Fixed comparison uses the Current Plan horizon. Both strategies must share the start date, household demographics and configured death timing.");
        show(validation, !validation.getText().isBlank());
        var update = session.progress();
        progress.setProgress(update == null ? -1 : update.fractionComplete()); show(progress, busy);
        String message = switch (session.state()) {
            case IDLE -> "Ready to compare.";
            case RUNNING -> update == null ? "Preparing paired comparison…" : String.format("Comparing strategies — simulation %,d of %,d", update.completedWork(), update.totalWork());
            case CANCELLING -> "Cancelling comparison…";
            case CANCELLED -> "Comparison cancelled. No partial result was published.";
            case FAILED -> "Comparison failed. " + session.failure().getMessage();
            case COMPLETED -> "Comparison complete.";
            case CLOSED -> "";
        };
        status.setText((session.stale() ? "STALE — inputs, Current Plan or Saved Baseline changed. Run again. " : "") + message);
        show(results, session.result() != null);
        if (session.result() != null && session.result() != displayed) {
            displayed = session.result(); render(displayed);
        }
    }

    private void render(MonteCarloStrategyComparisonRun completed) {
        var result = completed.result(); var s = result.summary(); var settings = result.request().assumptions().settings();
        identity.setText(String.format("Strategy A — %s     Strategy B — %s     Paired simulations: %,d     Seed: %d     Mode: %s",
                result.request().strategyA().label(), result.request().strategyB().label(), settings.simulationCount(), settings.seed(), completed.mode()));
        var paired = s.pairedStates();
        fundingA.setText(MonteCarloPresentation.fundingPercent(paired.aFundingProbability(), paired.aFailedCount(), paired.aCompletedCount()));
        fundingB.setText(MonteCarloPresentation.fundingPercent(paired.bFundingProbability(), paired.bFailedCount(), paired.bCompletedCount()));
        fundingDifference.setText(difference(paired.fundingProbabilityDifference()));
        states.setText(states(paired)); denominator.setText(denominator(s));
        terminalNotice.setText(completed.mode() == MonteCarloMode.LONGEVITY_ADJUSTED ? NOMINAL
                : "Terminal differences use the common Current Plan horizon. Values are nominal future dollars; taxes are lifetime totals.");
        table.getItems().setAll(metrics(s)); table.getSelectionModel().selectFirst();
        chart.load(s.annualResults());
        details.setContent(label(completed.details() + "\nComparable terminal samples: " + s.pairedStates().bothCompleted(), "mc-muted", null));
    }

    private void metric(Metric m) {
        if (m == null) return;
        quantiles.setText(m.name() + " · " + m.quantiles()); relations.setText(m.relations()); direction.setText(m.direction());
    }

    private void column(String title, Function<Metric, String> value, double minimum) {
        var column = new TableColumn<Metric, String>(title); column.setMinWidth(minimum);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setSortable(false); table.getColumns().add(column);
    }

    private static VBox input(String name, Control control, String id, String help) {
        control.setId("mcc-" + id); control.setAccessibleText(name); InputHelp.install(control, help);
        control.setPrefWidth(control instanceof ComboBox ? 180 : 150);
        var label = new Label(name); InputHelp.link(label, control); return new VBox(2, label, control);
    }
    private static VBox fundingColumn(String title, Label value) {
        var column = new VBox(1, label(title, "mc-muted", null), value);
        column.getStyleClass().add("mcc-funding-column");
        return column;
    }
    private static Label section(String text) {
        var heading = label(text, "mc-section", null);
        heading.getStyleClass().add("mcc-section"); heading.setMaxWidth(Double.MAX_VALUE);
        return heading;
    }
    private static Label label(String text, String style, String id) {
        var label = new Label(text); label.getStyleClass().add(style); label.setId(id); label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); return label;
    }
    private static void show(Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
    public MonteCarloStrategyComparisonSession session() { return session; }
    public boolean canExportPdf() {
        return session != null && session.state() == MonteCarloStrategyComparisonSession.State.COMPLETED && !session.stale() && session.result() != null;
    }
    public com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed paired result is required for PDF export.");
        return MonteCarloPdfReportAdapter.from(session.result());
    }
    public void exportPdf(java.nio.file.Path destination) throws java.io.IOException {
        new com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter().export(preparePdfReport(), destination);
    }
    @Override public void close() {
        session.close(); detach.run(); if (worker instanceof ExecutorService service) service.shutdown();
    }
}
