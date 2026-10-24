package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.function.Function;

/** Primary-only inputs and cached nine-row results; no live plan values used in completed details. */
final class SinglePersonMortalityView extends VBox {
    final TextField factor = new TextField("1.00");
    final TextField discount = new TextField("1.0");
    final DatePicker conditioning = new DatePicker();
    final DatePicker valuation = new DatePicker();
    final Button run = new Button("Run Nine Claiming Strategies");
    final Button cancel = new Button("Cancel");
    final Label status = new Label();
    final Label assumptions = new Label();
    final Label summary = new Label();
    final Label persistenceNotice = new Label();
    WeightedResultPersistence persistence;
    SocialSecurityParameterBinding parameters;
    record CompletionInput(RetirementPlan plan, IndividualReportContext context,
            IndividualLongevityScenarios mortality, java.time.LocalDate valuation, BigDecimal rate) { }
    java.util.function.Function<CompletionInput, java.util.function.Consumer<SinglePersonMortalityAnalysis.Result>>
            acceptedCompletion = input -> result -> { };
    java.util.function.Function<CompletionInput, SocialSecurityAnalyzerJobController.Work<SinglePersonMortalityAnalysis.Result>> calculation;
    boolean displays(SinglePersonMortalityAnalysis.Result result) { return completed == result; }
    final TextArea details = new TextArea();
    final TableView<SinglePersonMortalityAnalysis.Entry> table = new TableView<>();
    private final SinglePersonMortalityAnalysis.Mode mode;
    private final SocialSecurityAnalyzerJobController jobs;
    private RetirementPlan plan;
    private SinglePersonMortalityAnalysis.Result completed;
    private boolean current;
    private IndividualReportContext reportContext;
    private String acceptedInputFingerprint;
    private final IndividualPdfExportAction pdf;

    boolean canExportPdf() { return (persistence == null ? current : persistence.exportable()) && completed != null && reportContext != null
            && jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE; }
    com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed analysis is required.");
        var selected = table.getSelectionModel().getSelectedItem();
        var report = IndividualAnalyzerReportAdapter.mortality(reportContext, completed,
                selected == null ? completed.currentAge() : selected.strategy().primaryRetirementAge());
        return persistence != null && persistence.exportable() ? persistence.report(report) : report;
    }
    private final AnalysisProgressView progress = new AnalysisProgressView();

    SinglePersonMortalityView(RetirementPlan plan, SocialSecurityAnalyzerJobController jobs, SinglePersonMortalityAnalysis.Mode mode) {
        super(10);
        persistenceNotice.visibleProperty().bind(persistenceNotice.textProperty().isNotEmpty());
        persistenceNotice.managedProperty().bind(persistenceNotice.visibleProperty());
        persistenceNotice.setWrapText(true);
        this.plan = plan;
        this.jobs = jobs;
        this.mode = mode;
        calculation = input -> (p, c) -> new SinglePersonMortalityAnalysis().calculate(input.plan(), input.mortality(), input.valuation(), input.rate(), mode, p, c);
        pdf = new IndividualPdfExportAction("single-mortality-pdf", mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED
                ? "social-security-longevity-weighted.pdf" : "social-security-only.pdf", this::canExportPdf, this::preparePdfReport);
        setPadding(new Insets(14));
        conditioning.setValue(plan.getPlanningAssumptions().getProjectionStartDate());
        valuation.setValue(conditioning.getValue());
        var heading = label(mode == SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY
                ? "Social Security Only - Expected Present Value" : "Longevity-Weighted Integrated Retirement Plan");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        var explanation = label(mode == SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY
                ? "Nine primary claiming ages, ranked by mortality-weighted expected PV of own Social Security. Benefits begin at the conditioning month; no spouse or survivor benefit is modeled."
                : "Nine primary claiming ages, ranked by expected PV After-Tax Estate at the person's death. Each lifetime uses the full retirement projection, ending December 31 before the modeled January 1 death. Non-investable property is excluded.");
        var inputs = AnalyzerConfigurationLayout.flow();
        factor.setPrefColumnCount(8);
        discount.setPrefColumnCount(8);
        input(inputs, "Primary Longevity Factor", factor,
                "Mortality multiplier: 1.00 uses the baseline table. Below 1 lowers mortality and increases later survival; above 1 increases mortality. Analyzer input; financial plan assumptions are unchanged.");
        input(inputs, "Mortality Conditioning Date", conditioning,
                "Conditions on Primary being alive at this date. Uses the existing next complete birthday interval convention. Independent of the valuation date.");
        input(inputs, "PV Valuation Date", valuation,
                "Date whose purchasing power is used for present values. Does not move the projection start or mortality conditioning date.");
        input(inputs, "Real Discount Rate (%)", discount,
                "Annual real discount percentage, after inflation. Used only to value analysis outcomes; does not change plan investment returns or financial assumptions.");
        table.setId(mode == SinglePersonMortalityAnalysis.Mode.SOCIAL_SECURITY_ONLY ? "single-ss-weighted" : "single-integrated-weighted");
        table.setAccessibleText("Nine primary claiming strategies ranked by mortality-weighted expected present value");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(29);
        table.setPrefHeight(300);
        table.setMinHeight(300);
        column("Rank", e -> Integer.toString(completed.rank(e)));
        column("Claiming Age", e -> Integer.toString(e.strategy().primaryRetirementAge()));
        column("Status", e -> (e.strategy().primaryRetirementAge() == completed.currentAge() ? "Current " : "")
                + (completed.rank(e) == 1 ? "Maximum expected PV" : ""));
        column("Expected PV", e -> money(e.expectedPresentValue()));
        column("Difference from Current", e -> money(e.expectedPresentValue().subtract(completed.current().expectedPresentValue())));
        column("Difference from Maximum", e -> money(e.expectedPresentValue().subtract(completed.ranked().getFirst().expectedPresentValue())));
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(4);
        details.setAccessibleText("Selected mortality-weighted strategy details");
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) details.clear();
            else details.setText("Claiming age " + b.strategy().primaryRetirementAge() + " - Expected PV " + money(b.expectedPresentValue())
                    + "\nExpected nominal " + (mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? "After-Tax Estate " : "own Social Security ")
                    + money(b.expectedNominal()) + (b.expectedInvestableAssets() == null ? "" : " - Expected Investable Assets " + money(b.expectedInvestableAssets()))
                    + b.minimumNominalEstate().map(v -> "\nScenario estate range " + money(v) + " to " + money(b.maximumNominalEstate().orElseThrow())).orElse("")
                    + "\n" + completed.mortality().scenarios().size() + " individual death scenarios; no spouse or survivor state.");
        });
        run.setOnAction(e -> start());
        cancel.setOnAction(e -> jobs.cancel());
        InputHelp.install(run, "Evaluate exactly nine primary claiming ages using the individual mortality distribution. No strategy is applied to the plan.");
        factor.textProperty().addListener((o, a, b) -> edited());
        discount.textProperty().addListener((o, a, b) -> edited());
        conditioning.valueProperty().addListener((o, a, b) -> edited());
        valuation.valueProperty().addListener((o, a, b) -> edited());
        status.setWrapText(true);
        status.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(heading, explanation,
                AnalyzerConfigurationLayout.heading("From Retirement Plan"), assumptions,
                AnalyzerConfigurationLayout.heading("Inputs for Next Run"),
                AnalyzerConfigurationLayout.note("Required temporary inputs for this analysis only; the other individual analysis retains its own settings."),
                inputs, AnalyzerConfigurationLayout.heading("Run / Cancel / Progress"),
                AnalyzerConfigurationLayout.flow(run, cancel), status, progress,
                AnalyzerConfigurationLayout.heading("Completed Results"), persistenceNotice, pdf.button(),
                AnalyzerConfigurationLayout.note("Completed Result Assumptions below are frozen for the displayed run."), summary, table, details,
                label("Read-only analysis. Mortality uses the SSA period table with terminal residual at age 120. PDF export uses the completed frozen result."));
        refresh();
    }

    void attachParameters(com.daviddunn.retirementplanner.ui.controller.ApplicationController source) {
        var kind = mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED
                ? com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_WEIGHTED
                : com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
        parameters = new SocialSecurityParameterBinding(source, kind, values -> {
            var m = (com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration.Mortality) values;
            String profile = mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? SocialSecurityParameterMapping.INDIVIDUAL_WEIGHTED : SocialSecurityParameterMapping.INDIVIDUAL_SS;
            if (!profile.equals(m.optimizerProfile())) throw new IllegalArgumentException("Unsupported optimization profile");
            factor.setText(m.primaryFactor().toString()); discount.setText(SocialSecurityParameterMapping.percent(m.realDiscountRate()));
            SocialSecurityParameterMapping.date(conditioning, m.conditioningDate()); SocialSecurityParameterMapping.date(valuation, m.valuationDate());
        }, () -> {
            factor.setText("1.00"); discount.setText("1.0");
            SocialSecurityParameterMapping.date(conditioning, plan.getPlanningAssumptions().getProjectionStartDate()); SocialSecurityParameterMapping.date(valuation, conditioning.getValue());
        }, () -> { assessCompletedInputs(); refresh(); if (persistence != null) persistence.reassess(); }, () -> true);
        getChildren().add(getChildren().indexOf(summary), parameters.controls);
        parameters.restore();
        conditioning.getEditor().textProperty().addListener((o, a, b) -> { if (!parameters.applying()) parameters.edited(); });
        valuation.getEditor().textProperty().addListener((o, a, b) -> { if (!parameters.applying()) parameters.edited(); });
    }
    private void assessCompletedInputs() {
        if (acceptedInputFingerprint == null) return;
        try {
            current = acceptedInputFingerprint.equals(inputFingerprint(plan, new BigDecimal(factor.getText().trim()),
                    WeightedResultPersistence.date(conditioning), WeightedResultPersistence.date(valuation), new BigDecimal(discount.getText().trim()).movePointLeft(2)));
        } catch (RuntimeException invalid) { current = false; }
    }
    private String inputFingerprint(RetirementPlan plan, BigDecimal factor, java.time.LocalDate conditioning, java.time.LocalDate valuation, BigDecimal rate) {
        var kind = mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED
                ? com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_WEIGHTED
                : com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY;
        return new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService().fingerprint(kind, plan,
                new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisFingerprintService.Inputs(factor, null, conditioning, valuation, rate,
                        null, null, java.util.List.of(), java.util.List.of(), null));
    }
    void invalidate(RetirementPlan changed) {
        boolean different = plan != changed; plan = changed;
        if (different) acceptedInputFingerprint = null;
        if (different && parameters != null) parameters.planChanged();
        edited();
    }
    private void edited() {
        if (parameters != null && parameters.applying()) return;
        if (parameters != null) parameters.edited();
        current = false;
        assessCompletedInputs();
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.ASSUMPTIONS);
        status.setText("Inputs changed - displayed results are stale. Run analysis again.");
        refresh();
        if (persistence != null) persistence.reassess();
    }
    void restore(SinglePersonMortalityAnalysis.Result result, IndividualReportContext context) {
        reportContext = context;
        render(result);
    }
    void clearSavedResult() {
        completed = null;
        reportContext = null;
        current = false;
        table.getItems().clear();
        summary.setText("");
        details.clear();
        status.setText("No completed result loaded for this plan.");
        refresh();
    }
    void refreshSavedState() {
        refresh();
        if (persistence != null && persistence.exportable() && jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE
                && status.getText().startsWith("Inputs changed")) {
            status.setText(persistence.current() ? "Completed results match current inputs."
                    : "Previous completed results remain available. Run analysis to replace them.");
        }
    }
    void start() {
        try {
            var frozen = new RetirementPlanScenarioCopyService().copy(plan);
            var captured = IndividualReportContext.capture(frozen);
            var mortality = new SocialSecurityStrategyAnalysisRequestFactory().createIndividual(frozen,
                    SocialSecurityMortalityAdjustment.of(new BigDecimal(factor.getText().trim())), conditioning.getValue());
            var date = valuation.getValue();
            var rate = new BigDecimal(discount.getText().trim()).movePointLeft(2);
            var accepted = acceptedCompletion.apply(new CompletionInput(frozen, captured, mortality, date, rate));
            var parameterRun = parameters == null ? null : parameters.begin(SocialSecurityParameterMapping.individual(
                    new CompletionInput(frozen, captured, mortality, date, rate), mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED));
            String capturedFingerprint = parameters == null ? null : inputFingerprint(frozen,
                    mortality.individual().request().mortalityAdjustment().factor(), mortality.individual().request().mortalityBaseDate(), date, rate);
            current = false;
            boolean admitted = jobs.start(mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED
                            ? SocialSecurityAnalyzerJobController.Mode.WEIGHTED : SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                    calculation.apply(new CompletionInput(frozen, captured, mortality, date, rate)),
                    result -> {
                        // This callback is reached only after the original generation/revision/cancellation gates.
                        try { accepted.accept(result); }
                        catch (RuntimeException persistenceFailure) { persistenceNotice.setText("Analysis completed, but result saving could not start. The previous saved result was retained."); }
                        reportContext = captured; acceptedInputFingerprint = capturedFingerprint; render(result);
                        if (parameterRun != null && com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisSuccessPolicy.accepted(result)) parameters.accept(parameterRun);
                    }, failure -> status.setText("Analysis failed: " + failure.getMessage()),
                    () -> { if (parameterRun != null) parameters.rollback(parameterRun); });
            if (!admitted) status.setText("Another analysis is running.");
        } catch (RuntimeException invalid) { current = false; status.setText("Check inputs: " + invalid.getMessage()); }
        refresh();
    }
    void render(SinglePersonMortalityAnalysis.Result result) {
        completed = result;
        current = true;
        summary.setText(result.primaryName() + " - Current claiming age " + result.currentAge() + " - "
                + result.mortality().individual().request().mortalityCategory() + " - Factor "
                + result.mortality().individual().request().mortalityAdjustment().factor()
                + "\nConditioned " + result.mortality().individual().request().mortalityBaseDate() + " - Valued "
                + result.valuationDate() + " - Real discount " + result.discountRate().movePointRight(2).toPlainString() + "%");
        table.getItems().setAll(result.ranked());
        table.getSelectionModel().selectFirst();
        status.setText("Completed nine strategies - " + result.projectionCount() + " projection executions.");
        refresh();
    }
    void refresh() {
        boolean busy = jobs.state() != SocialSecurityAnalyzerJobController.State.IDLE;
        if (busy) progress.show(jobs.progress()); else progress.hide();
        run.setDisable(busy);
        cancel.setDisable(!busy);
        pdf.refresh();
        factor.setDisable(busy); discount.setDisable(busy); conditioning.setDisable(busy); valuation.setDisable(busy);
        assumptions.setText(AnalyzerConfigurationLayout.individualPlan(plan));
        assumptions.setWrapText(true);
        assumptions.setMinHeight(Region.USE_PREF_SIZE);
        if (busy) status.setText("Evaluating " + (mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? "individual lifetime scenarios" : "own-benefit claiming strategies")
                + " - " + jobs.status());
        table.setAccessibleHelp(persistence != null && persistence.exportable()
                ? (persistence.current() ? "Current completed frozen results." : "Previous completed results; inspect original assumptions.")
                        + " Row selection does not rerun analysis."
                : current ? "Completed frozen results. Row selection does not rerun analysis." : "Results are stale or unavailable.");
    }
    private void column(String name, Function<SinglePersonMortalityAnalysis.Entry, String> format) {
        var column = new TableColumn<SinglePersonMortalityAnalysis.Entry, String>(name);
        column.setCellValueFactory(v -> new ReadOnlyStringWrapper(format.apply(v.getValue())));
        table.getColumns().add(column);
    }
    private static void input(FlowPane inputs, String name, Control control, String help) {
        var label = new Label(name);
        InputHelp.install(control, help);
        InputHelp.link(label, control);
        control.setAccessibleText(name);
        inputs.getChildren().add(AnalyzerConfigurationLayout.field(label, control));
    }
    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); return label; }
    private static String money(BigDecimal value) {
        var format = NumberFormat.getCurrencyInstance(java.util.Locale.US); format.setMaximumFractionDigits(0); return format.format(value);
    }
}
