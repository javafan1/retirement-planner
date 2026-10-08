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
    final TextArea details = new TextArea();
    final TableView<SinglePersonMortalityAnalysis.Entry> table = new TableView<>();
    private final SinglePersonMortalityAnalysis.Mode mode;
    private final SocialSecurityAnalyzerJobController jobs;
    private RetirementPlan plan;
    private SinglePersonMortalityAnalysis.Result completed;
    private boolean current;
    private IndividualReportContext reportContext;
    private final IndividualPdfExportAction pdf;

    boolean canExportPdf() { return current && completed != null && reportContext != null
            && jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE; }
    com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport preparePdfReport() {
        if (!canExportPdf()) throw new IllegalStateException("A current completed analysis is required.");
        var selected = table.getSelectionModel().getSelectedItem();
        return IndividualAnalyzerReportAdapter.mortality(reportContext, completed,
                selected == null ? completed.currentAge() : selected.strategy().primaryRetirementAge());
    }
    private final AnalysisProgressView progress = new AnalysisProgressView();

    SinglePersonMortalityView(RetirementPlan plan, SocialSecurityAnalyzerJobController jobs, SinglePersonMortalityAnalysis.Mode mode) {
        super(10);
        this.plan = plan;
        this.jobs = jobs;
        this.mode = mode;
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
        var inputs = new GridPane();
        inputs.setHgap(12);
        inputs.setVgap(8);
        factor.setPrefColumnCount(8);
        discount.setPrefColumnCount(8);
        input(inputs, 0, "Primary Longevity Factor", factor,
                "Mortality multiplier: 1.00 uses the baseline table. Below 1 lowers mortality and increases later survival; above 1 increases mortality. Analysis only; the saved plan is unchanged.");
        input(inputs, 1, "Mortality Conditioning Date", conditioning,
                "Conditions on Primary being alive at this date. Uses the existing next complete birthday interval convention. Independent of the valuation date.");
        input(inputs, 2, "PV Valuation Date", valuation,
                "Date whose purchasing power is used for present values. Does not move the projection start or mortality conditioning date.");
        input(inputs, 3, "Real Discount Rate (%)", discount,
                "Annual real discount percentage, after inflation. Used only to value analysis outcomes; does not change investment returns or the saved plan.");
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
        getChildren().addAll(heading, explanation, assumptions, inputs, new HBox(10, run, cancel, pdf.button()), status, progress, summary, table, details,
                label("Read-only analysis. Mortality uses the SSA period table with terminal residual at age 120. PDF export uses the completed frozen result."));
        refresh();
    }

    void invalidate(RetirementPlan changed) { plan = changed; edited(); }
    private void edited() {
        current = false;
        jobs.invalidate(SocialSecurityAnalyzerJobController.Change.ASSUMPTIONS);
        status.setText("Inputs changed - displayed results are stale. Run analysis again.");
        refresh();
    }
    void start() {
        try {
            var frozen = new RetirementPlanScenarioCopyService().copy(plan);
            var captured = IndividualReportContext.capture(frozen);
            var mortality = new SocialSecurityStrategyAnalysisRequestFactory().createIndividual(frozen,
                    SocialSecurityMortalityAdjustment.of(new BigDecimal(factor.getText().trim())), conditioning.getValue());
            var date = valuation.getValue();
            var rate = new BigDecimal(discount.getText().trim()).movePointLeft(2);
            current = false;
            boolean admitted = jobs.start(mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED
                            ? SocialSecurityAnalyzerJobController.Mode.WEIGHTED : SocialSecurityAnalyzerJobController.Mode.SOCIAL_SECURITY,
                    (p, c) -> new SinglePersonMortalityAnalysis().calculate(frozen, mortality, date, rate, mode, p, c),
                    result -> { reportContext = captured; render(result); }, failure -> status.setText("Analysis failed: " + failure.getMessage()));
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
        assumptions.setText("Mortality category from Person: " + plan.getHousehold().getPrimaryPerson().getMortalityCategory());
        if (busy) status.setText("Evaluating " + (mode == SinglePersonMortalityAnalysis.Mode.INTEGRATED ? "individual lifetime scenarios" : "own-benefit claiming strategies")
                + " - " + jobs.status());
        table.setAccessibleHelp(current ? "Completed frozen results. Row selection does not rerun analysis." : "Results are stale or unavailable.");
    }
    private void column(String name, Function<SinglePersonMortalityAnalysis.Entry, String> format) {
        var column = new TableColumn<SinglePersonMortalityAnalysis.Entry, String>(name);
        column.setCellValueFactory(v -> new ReadOnlyStringWrapper(format.apply(v.getValue())));
        table.getColumns().add(column);
    }
    private static void input(GridPane grid, int row, String name, Control control, String help) {
        var label = new Label(name);
        InputHelp.install(control, help);
        InputHelp.link(label, control);
        control.setAccessibleText(name);
        grid.addRow(row, label, control);
    }
    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); return label; }
    private static String money(BigDecimal value) {
        var format = NumberFormat.getCurrencyInstance(java.util.Locale.US); format.setMaximumFractionDigits(0); return format.format(value);
    }
}
