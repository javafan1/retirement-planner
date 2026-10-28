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
    SocialSecurityParameterBinding parameters;
    private com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration.Search acceptedProfile;
    final TableView<IntegratedSocialSecurityCompleteStrategySearchEntry> table = new TableView<>();
    final TextArea details = new TextArea();
    final Label status = new Label();
    final Label summary = new Label();
    private final Label inheritedPlan = AnalyzerConfigurationLayout.note("");
    private final AnalysisProgressView progress = new AnalysisProgressView();
    private final SocialSecurityAnalyzerJobController jobs;
    private RetirementPlan plan;
    private SinglePersonIntegratedAnalysis completed;
    private boolean current;
    private String acceptedInputFingerprint;
    java.util.function.Function<IntegratedSocialSecurityCompleteStrategySearchRequest,
            SocialSecurityAnalyzerJobController.Work<SinglePersonIntegratedAnalysis>> calculation = request -> (p, c) -> SinglePersonIntegratedAnalysis.calculate(request, p, c);
    private IndividualReportContext reportContext;
    private com.daviddunn.retirementplanner.ui.controller.ApplicationController source;
    WeightedResultPersistence persistence;
    private final IndividualPdfExportAction pdf = new IndividualPdfExportAction("single-deterministic-pdf",
            "social-security-deterministic.pdf", this::canExportPdf, this::preparePdfReport);

    boolean canExportPdf() { return (persistence == null || persistence.resultMetadata() == null ? current : persistence.exportable()) && completed != null && reportContext != null
            && jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE; }
    com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed analysis is required.");
        var selected = table.getSelectionModel().getSelectedItem();
        var report = IndividualAnalyzerReportAdapter.deterministic(reportContext, completed,
                selected == null ? completed.result().currentPlanBaseline().evaluatedStrategy().primaryRetirementAge()
                        : selected.strategy().primaryRetirementAge());
        return persistence != null && persistence.exportable() ? persistence.report(report) : report;
    }
    final SinglePersonMortalityView ssView;
    final SinglePersonMortalityView weightedView;
    final AnalyzerNavigationView navigation;

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
                + "ending own Social Security with no survivor benefit. Blank uses survival through the configured horizon. Financial plan assumptions are unchanged.");
        Label deathLabel = new Label("Primary death year:");
        InputHelp.link(deathLabel, deathYear);
        InputHelp.install(run, "Evaluate all nine primary claiming ages through the existing deterministic retirement projection. No strategy is applied to the saved plan.");
        InputHelp.install(breakEven, "Compare the selected completed strategy with the frozen current-plan strategy. Uses existing annual results without rerunning projections.");
        cancel.setOnAction(e -> jobs.cancel());
        run.setOnAction(e -> start());
        deathYear.textProperty().addListener((o, a, b) -> { if (parameters == null || !parameters.applying()) {
            if (parameters != null) parameters.edited(); invalidate(plan);
        } });
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
        status.setWrapText(true);
        status.setMinHeight(Region.USE_PREF_SIZE);
        deterministic.getChildren().addAll(title, explanation,
                AnalyzerConfigurationLayout.heading("From Retirement Plan"), inheritedPlan,
                AnalyzerConfigurationLayout.heading("Inputs for Next Run"),
                AnalyzerConfigurationLayout.note("Optional analyzer death year; financial plan assumptions are unchanged."),
                AnalyzerConfigurationLayout.field(deathLabel, deathYear),
                AnalyzerConfigurationLayout.heading("Run / Cancel / Progress"), AnalyzerConfigurationLayout.flow(run, cancel),
                status, progress, AnalyzerConfigurationLayout.heading("Completed Results"), pdf.button(),
                AnalyzerConfigurationLayout.note("Completed Result Assumptions and metrics below belong to the frozen completed run."), summary, table, details, breakEven,
                label("Read-only analysis. PDF export uses the completed frozen result."));
        ssView = new SinglePersonMortalityView(plan, jobs, SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY);
        weightedView = new SinglePersonMortalityView(plan, jobs, SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        navigation = new AnalyzerNavigationView(null, List.of(
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                        "Deterministic Integrated", "Nine claiming strategies through the configured retirement plan.", deterministic),
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                        "Social Security Only", "Nine claiming strategies ranked by expected Social Security present value.", ssView),
                new AnalyzerNavigationView.Choice(SocialSecurityAnalyzerJobController.Mode.WEIGHTED,
                        "Longevity-Weighted Integrated", "Nine claiming strategies ranked by expected PV after-tax estate.", weightedView)));
        getChildren().add(navigation);
        VBox.setVgrow(navigation, Priority.ALWAYS);
        setMinSize(0, 0);
        jobs.onChanged(this::refresh);
        refresh();
    }

    void invalidate(RetirementPlan changed) {
        boolean different = plan != changed;
        if (different) acceptedInputFingerprint = null;
        plan = changed;
        if (different && parameters != null) parameters.planChanged();
        ssView.invalidate(changed);
        weightedView.invalidate(changed);
        current = false;
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
        assessCompletedInputs();
        status.setText("Inputs changed — run deterministic analysis again.");
        refreshControls();
    }

    void attachParameters(com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        this.source = source;
        parameters = new SocialSecurityParameterBinding(source,
                com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_DETERMINISTIC, values -> {
            var s = (com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration.Search) values;
            if (!"individual-nine-v1".equals(s.profile())) throw new IllegalArgumentException("Unsupported search profile");
            acceptedProfile = s; deathYear.setText(s.primaryDeathYear() == null ? "" : s.primaryDeathYear().toString());
        }, () -> { acceptedProfile = null; deathYear.clear(); }, () -> { assessCompletedInputs(); refreshControls(); if (persistence != null) persistence.reassess(); }, () -> true);
        ((VBox) navigation.choices().getFirst().workspace()).getChildren().add(9, parameters.controls);
        parameters.restore(); ssView.attachParameters(source); weightedView.attachParameters(source);
        persistence = new WeightedResultPersistence(source, com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_DETERMINISTIC,
                () -> new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService.Inputs(null, null, null, null, null,
                        null, null, List.of(), List.of(), deathYear.getText().isBlank() ? null : Integer.valueOf(deathYear.getText().trim())), saved -> {
            var restored = ((com.daviddunn.retirementplanner.app.socialsecurity.results.IndividualDeterministicSnapshot) saved.payload()).restore();
            var c = saved.context();
            var report = new IndividualReportContext(c.household(), c.primary(), c.startedAt(), c.assumptions().stream()
                    .map(s -> new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section(s.title(), s.lines())).toList());
            return () -> { reportContext = report; acceptedInputFingerprint = saved.inputFingerprint(); render(restored); };
        }, () -> { if (persistence != null) current = persistence.current(); refreshControls(); },
                () -> { completed = null; reportContext = null; table.getItems().clear(); details.clear(); });
        ((VBox) navigation.choices().getFirst().workspace()).getChildren().add(9, persistence.controls);
    }
    void closeParameters() {
        if (persistence != null) persistence.close();
        if (ssView.persistence != null) ssView.persistence.close();
        if (parameters != null) parameters.close();
        if (ssView.parameters != null) ssView.parameters.close();
        if (weightedView.parameters != null) weightedView.parameters.close();
    }
    private static String inputFingerprint(RetirementPlan plan, Integer year) {
        return new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService().fingerprint(
                com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_DETERMINISTIC, plan,
                new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService.Inputs(null, null, null, null, null,
                        null, null, List.of(), List.of(), year));
    }
    private void assessCompletedInputs() {
        if (acceptedInputFingerprint == null) return;
        try { current = acceptedInputFingerprint.equals(inputFingerprint(plan, deathYear.getText().isBlank() ? null : Integer.valueOf(deathYear.getText().trim()))); }
        catch (RuntimeException invalid) { current = false; }
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
                    List.of(), List.of(), List.of(), acceptedProfile == null ? base.rankingMeasure()
                            : IntegratedStrategyRankingMeasure.valueOf(acceptedProfile.rankingMeasure()),
                    acceptedProfile == null ? 9 : acceptedProfile.retainedDetails(), death);
            var parameterRun = parameters == null ? null : parameters.begin(SocialSecurityParameterMapping.search(request));
            String capturedFingerprint = parameters == null ? null : inputFingerprint(frozen, request.primaryDeathYear().map(java.time.Year::getValue).orElse(null));
            var admission = source == null ? null : source.prepareAnalyzerAssociation(com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_DETERMINISTIC, capturedFingerprint);
            var capturedPayload = new java.util.concurrent.atomic.AtomicReference<com.daviddunn.retirementplanner.app.socialsecurity.results.IndividualDeterministicSnapshot>();
            var calculationWork = calculation.apply(request);
            current = false;
            if (persistence != null) persistence.beginReplacement();
            boolean started = jobs.start(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE,
                    (progress, cancellation) -> {
                        var result = calculationWork.run(progress, cancellation); cancellation.throwIfCancellationRequested();
                        if (com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSuccessPolicy.accepted(result.result()))
                            capturedPayload.set(com.daviddunn.retirementplanner.app.socialsecurity.results.IndividualDeterministicSnapshot.capture(result));
                        return result;
                    }, result -> {
                        if (!com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSuccessPolicy.accepted(result.result())) {
                            if (parameterRun != null) parameters.rollback(parameterRun);
                            status.setText("No successful candidates. Previous accepted result and parameters retained.");
                            return;
                        }
                        reportContext = captured; acceptedInputFingerprint = capturedFingerprint; render(result);
                        if (persistence != null) {
                            var kind = com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_DETERMINISTIC;
                            var context = new com.daviddunn.retirementplanner.app.socialsecurity.results.SnapshotContext(captured.household(), captured.primary(), null, captured.started(),
                                    captured.assumptions().stream().map(s -> new com.daviddunn.retirementplanner.app.socialsecurity.results.SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), Map.of());
                            var completion = new com.daviddunn.retirementplanner.app.socialsecurity.results.AcceptedAnalysisCompletion(source.getAnalysisSessionId(),
                                    source.canPersistAnalysisResults() ? source.getCurrentFile() : null, kind, admission == null ? UUID.randomUUID() : admission.ticket(), java.time.Instant.now(), capturedFingerprint,
                                    new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService().effectiveContextFingerprint(kind, frozen),
                                    com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisVersions.algorithms(kind), Map.of(),
                                    capturedPayload.get(), context);
                            persistence.accept(completion.associatedWith(parameterRun != null && parameterRun.edit() == parameters.editEpoch() ? admission : null));
                        }
                        if (parameterRun != null && com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSuccessPolicy.accepted(result)) parameters.accept(parameterRun);
                    }, failure -> status.setText("Analysis failed: " + failure.getMessage()),
                    () -> { if (parameterRun != null) parameters.rollback(parameterRun); });
            if (!started) { if (persistence != null) persistence.finishReplacement(); status.setText("Another analysis is running. Try again when it finishes."); }
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
        navigation.showActivity(busy ? jobs.mode() : null, jobs.status());
        if (busy) progress.show(jobs.progress()); else progress.hide();
        if (!jobs.status().isBlank()) status.setText(jobs.status());
        refreshControls();
    }

    private void refreshControls() {
        inheritedPlan.setText(AnalyzerConfigurationLayout.individualPlan(plan));
        boolean busy = jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE;
        if (!busy && persistence != null) javafx.application.Platform.runLater(persistence::finishReplacement);
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
