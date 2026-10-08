package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.breakeven.BreakEvenAnalysisDialog;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Year;
import java.util.*;
import java.util.function.Function;

/** One-dimensional presentation using the existing deterministic search and job lifecycle. */
final class SinglePersonIntegratedView extends VBox {
    final Button run = new Button("Run Nine Claiming Strategies");
    final Button cancel = new Button("Cancel");
    final Button breakEven = new Button("Break-Even vs Current Plan");
    final TextField deathYear = new TextField();
    final TableView<IntegratedSocialSecurityCompleteStrategySearchEntry> table = new TableView<>();
    final TextArea details = new TextArea();
    final Label status = new Label();
    final Label summary = new Label();
    private final AnalysisProgressView progress = new AnalysisProgressView();
    private final SocialSecurityAnalyzerJobController jobs;
    private RetirementPlan plan;
    private SinglePersonIntegratedAnalysis completed;
    private boolean current;
    private IndividualReportContext reportContext;
    private final IndividualPdfExportAction pdf = new IndividualPdfExportAction("single-deterministic-pdf",
            "social-security-deterministic.pdf", this::canExportPdf, this::preparePdfReport);

    boolean canExportPdf() { return current && completed != null && reportContext != null
            && jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE; }
    com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed analysis is required.");
        var selected = table.getSelectionModel().getSelectedItem();
        return IndividualAnalyzerReportAdapter.deterministic(reportContext, completed,
                selected == null ? completed.result().currentPlanBaseline().evaluatedStrategy().primaryRetirementAge()
                        : selected.strategy().primaryRetirementAge());
    }
    final SinglePersonMortalityView ssView;
    final SinglePersonMortalityView weightedView;

    SinglePersonIntegratedView(RetirementPlan plan, SocialSecurityAnalyzerJobController jobs) {
        super(10);
        this.plan = plan;
        this.jobs = jobs;
        setPadding(new Insets(14));
        Label title = label("Integrated Retirement Plan — Deterministic Claiming Analysis");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label explanation = label("Evaluates primary claiming ages 62–70 through the full retirement plan. "
                + "Ranked by deterministic After-Tax Estate over the configured plan horizon. "
                + "This is not mortality-weighted Social Security Only optimization or a recommendation.");
        deathYear.setPromptText("Optional year");
        deathYear.setPrefColumnCount(10);
        deathYear.setMaxWidth(200);
        deathYear.setAccessibleText("Primary death year for this analysis");
        InputHelp.install(deathYear, "Optional primary death year for this deterministic analysis only. Death is modeled January 1, "
                + "ending own Social Security with no survivor benefit. Blank uses survival through the configured horizon. The saved plan is unchanged.");
        Label deathLabel = new Label("Primary death year:");
        InputHelp.link(deathLabel, deathYear);
        InputHelp.install(run, "Evaluate all nine primary claiming ages through the existing deterministic retirement projection. No strategy is applied to the saved plan.");
        InputHelp.install(breakEven, "Compare the selected completed strategy with the frozen current-plan strategy. Uses existing annual results without rerunning projections.");
        cancel.setOnAction(e -> jobs.cancel());
        run.setOnAction(e -> start());
        deathYear.textProperty().addListener((o, a, b) -> invalidate(plan));
        table.setId("single-claiming-strategies");
        table.setAccessibleText("Nine primary claiming strategies ranked by deterministic After-Tax Estate");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(29);
        table.setPrefHeight(300);
        table.setMinHeight(300);
        column("Rank", e -> e.afterTaxEstateRank().isPresent() ? Integer.toString(e.afterTaxEstateRank().getAsInt()) : "Failed");
        column("Claiming Age", e -> Integer.toString(e.strategy().primaryRetirementAge()));
        column("Status", this::indicators);
        column("After-Tax Estate", e -> e.metrics().map(m -> money(m.afterTaxEstate())).orElse("Unavailable"));
        column("Difference from Current", e -> e.differencesFromCurrentPlan().map(m -> money(m.afterTaxEstate())).orElse("Unavailable"));
        column("Difference from Maximum", e -> e.metrics().map(m -> completed.result().rankedSuccessfulEntries().isEmpty()
                ? "Unavailable" : money(m.afterTaxEstate().subtract(completed.result().rankedSuccessfulEntries().getFirst()
                        .metrics().orElseThrow().afterTaxEstate()))).orElse("Unavailable"));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> select(b));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(5);
        details.setAccessibleText("Selected deterministic strategy metrics");
        breakEven.setDisable(true);
        breakEven.setOnAction(e -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (!current || selected == null || completed == null) return;
            var comparison = completed.breakEvenByClaimingAge().get(selected.strategy().primaryRetirementAge());
            if (comparison != null) {
                var context = new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenContext(
                        com.daviddunn.retirementplanner.app.breakeven.BreakEvenContextFactory.events(comparison), Map.of(),
                        "The single-person survival overlay is deferred to reporting integration. This comparison uses deterministic completed projections.");
                var dialog = new BreakEvenAnalysisDialog(comparison, context);
                dialog.setHeaderText("Selected claiming age " + selected.strategy().primaryRetirementAge()
                        + " (Current) versus frozen plan age " + completed.result().currentPlanBaseline()
                        .evaluatedStrategy().primaryRetirementAge() + " (Baseline)");
                dialog.initOwner(getScene().getWindow());
                dialog.showAndWait();
            }
        });
        var deterministic = new VBox(10);
        deterministic.getChildren().addAll(title, explanation, new HBox(10, deathLabel, deathYear, run, cancel, pdf.button()),
                status, progress, summary, table, details, breakEven,
                label("Read-only analysis. PDF export uses the completed frozen result."));
        ssView = new SinglePersonMortalityView(plan, jobs, SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        weightedView = new SinglePersonMortalityView(plan, jobs, SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        var tabs = new TabPane(new Tab("Deterministic Integrated", deterministic),
                new Tab("Social Security Only", ssView), new Tab("Longevity-Weighted Integrated", weightedView));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        getChildren().add(tabs);
        jobs.onChanged(this::refresh);
        refresh();
    }

    void invalidate(RetirementPlan changed) {
        plan = changed;
        ssView.invalidate(changed);
        weightedView.invalidate(changed);
        current = false;
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
        status.setText("Inputs changed — run deterministic analysis again.");
        refreshControls();
    }

    void start() {
        try {
            if (plan.getHousehold().hasSpouse()) throw new IllegalArgumentException("Household composition changed; reopen the analyzer.");
            var frozen = new RetirementPlanScenarioCopyService().copy(plan);
            var captured = IndividualReportContext.capture(frozen);
            var base = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(frozen);
            Optional<Year> death = deathYear.getText().isBlank() ? Optional.empty()
                    : Optional.of(Year.of(Integer.parseInt(deathYear.getText().trim())));
            var request = new IntegratedSocialSecurityCompleteStrategySearchRequest(frozen, base.primaryRetirementAges(),
                    List.of(), List.of(), List.of(), base.rankingMeasure(), 9, death);
            current = false;
            boolean started = jobs.start(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                    (p, c) -> SinglePersonIntegratedAnalysis.calculate(request, p, c), result -> { reportContext = captured; render(result); },
                    failure -> status.setText("Analysis failed: " + failure.getMessage()));
            if (!started) status.setText("Another analysis is running. Try again when it finishes.");
            refreshControls();
        } catch (RuntimeException invalid) {
            current = false;
            status.setText("Check inputs: " + invalid.getMessage());
            refreshControls();
        }
    }

    void render(SinglePersonIntegratedAnalysis result) {
        completed = result;
        current = true;
        summary.setText(result.primaryName() + " · " + result.startYear() + "–" + result.endYear()
                + " · Current claiming age " + result.result().currentPlanBaseline().evaluatedStrategy().primaryRetirementAge()
                + result.primaryDeathYear().map(y -> " · Primary death January 1, " + y).orElse(" · No death assumed within horizon"));
        var rows = new ArrayList<>(result.result().rankedSuccessfulEntries());
        result.result().entries().stream().filter(e -> e.failure().isPresent()).forEach(rows::add);
        table.getItems().setAll(rows);
        table.getSelectionModel().selectFirst();
        status.setText("Completed " + result.result().entries().size() + " claiming strategies; current plan evaluated separately.");
        refreshControls();
    }

    private String indicators(IntegratedSocialSecurityCompleteStrategySearchEntry entry) {
        if (completed == null) return "";
        boolean isCurrent = entry.strategy().equals(completed.result().currentPlanBaseline().evaluatedStrategy());
        boolean maximum = entry.afterTaxEstateRank().orElse(0) == 1;
        return (isCurrent ? "Current" : "") + (isCurrent && maximum ? " · " : "") + (maximum ? "Maximum estate" : "");
    }

    private void select(IntegratedSocialSecurityCompleteStrategySearchEntry entry) {
        if (entry == null) details.clear();
        else details.setText(entry.metrics().map(SinglePersonIntegratedView::metrics)
                .orElseGet(() -> entry.failure().map(f -> f.message()).orElse("Unavailable")));
        refreshControls();
    }

    static String metrics(ProjectionMetrics m) {
        return "Investment Growth " + money(m.totalInvestmentGrowth()) + " · Total Income " + money(m.totalIncome())
                + " · Total Taxes " + money(m.totalTaxes()) + " · Peak Annual Tax " + money(m.peakAnnualTax())
                + "\nInvestable Assets " + money(m.endingInvestableAssets()) + " · Net Worth " + money(m.endingNetWorth())
                + " · After-Tax Estate " + money(m.afterTaxEstate())
                + "\nSocial Security " + money(m.lifetimeHouseholdSocialSecurity()) + " · Roth Conversions "
                + money(m.lifetimeRothConversions()) + " · Portfolio Withdrawals " + money(m.lifetimePortfolioWithdrawals());
    }

    private void refresh() {
        ssView.refresh();
        weightedView.refresh();
        boolean busy = jobs.state() == SocialSecurityAnalyzerJobController.State.RUNNING
                || jobs.state() == SocialSecurityAnalyzerJobController.State.CANCELLING;
        if (busy) progress.show(jobs.progress()); else progress.hide();
        if (!jobs.status().isBlank()) status.setText(jobs.status());
        refreshControls();
    }

    private void refreshControls() {
        boolean busy = jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE;
        run.setDisable(busy || plan.getHousehold().hasSpouse());
        cancel.setDisable(!busy);
        pdf.refresh();
        deathYear.setDisable(busy);
        var selected = table.getSelectionModel().getSelectedItem();
        breakEven.setDisable(busy || !current || completed == null || selected == null
                || !completed.breakEvenByClaimingAge().containsKey(selected.strategy().primaryRetirementAge()));
    }

    private void column(String title, Function<IntegratedSocialSecurityCompleteStrategySearchEntry, String> value) {
        var column = new TableColumn<IntegratedSocialSecurityCompleteStrategySearchEntry, String>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setSortable(false);
        table.getColumns().add(column);
    }

    private static Label label(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static String money(BigDecimal value) {
        var format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setMaximumFractionDigits(0);
        return format.format(value);
    }
}
