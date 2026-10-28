package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.util.*;

/** One set of existing controls, independent accepted mode settings, and guarded rollback. */
final class MonteCarloParameterControls implements AutoCloseable {
    record Run(AnalysisParameterCoordinator.Binding binding, AnalysisKind kind, long sequence, long edit,
            AnalysisParameterConfiguration.MonteCarlo parameters,
            com.daviddunn.retirementplanner.app.projection.AnalyzerAdmission association) { }
    private record Values(int count, String inflation, List<String> fields) { }
    private final ApplicationController controller;
    private final AnalysisParameterCoordinator coordinator;
    private final boolean paired;
    private final ComboBox<MonteCarloMode> mode;
    private final ComboBox<Integer> count;
    private final ComboBox<String> inflation;
    // return, volatility, seed, inflation mean/volatility/floor, primary/spouse, survivor A/B
    private final List<TextField> fields;
    private final Runnable share;
    private final Runnable refresh;
    private final Runnable detach;
    final Label status = new Label();
    final Button retry = new Button("Retry Parameters Save");
    final VBox notice = new VBox(3, status, retry);
    private RetirementPlan owner;
    private Values defaults;
    private boolean applying;
    private boolean closed;
    private long edit;
    private UUID appliedRun;

    MonteCarloParameterControls(ApplicationController controller, boolean paired, ComboBox<MonteCarloMode> mode,
            ComboBox<Integer> count, ComboBox<String> inflation, List<TextField> fields, Runnable share, Runnable refresh) {
        this.controller = controller; this.coordinator = controller.getAnalysisParameters(); this.paired = paired;
        this.mode = mode; this.count = count; this.inflation = inflation; this.fields = fields;
        this.share = share; this.refresh = refresh;
        String prefix = paired ? "mcc" : "mc";
        status.setId(prefix + "-parameters-status"); status.setWrapText(true);
        status.accessibleTextProperty().bind(status.textProperty());
        retry.setId(prefix + "-retry-parameters-save");
        retry.setAccessibleText("Retry saving accepted analysis parameters without running a simulation");
        retry.setOnAction(event -> { coordinator.retry(owner, kind()); updateStatus(); });
        detach = coordinator.addListener(Platform::runLater, () -> { if (!closed) updateStatus(); });
        owner = controller.getCurrentPlan(); defaults = read();
        restore();
    }
    boolean applying() { return applying; }
    void edited() { if (!applying) edit++; }
    AnalysisKind kind() {
        return AnalysisKind.valueOf((controller.getCurrentPlan().getHousehold().hasSpouse() ? "COUPLE" : "INDIVIDUAL")
                + "_MONTE_CARLO_" + (paired ? "PAIRED_" : "")
                + (mode.getValue() == MonteCarloMode.FIXED_LIFESPAN ? "FIXED" : "LONGEVITY"));
    }
    void select() { edit++; restore(); }
    Runnable afterPlanChange() {
        long expected = edit; var expectedOwner = owner;
        return () -> {
            if (closed || edit != expected || owner != expectedOwner || owner != controller.getCurrentPlan()) return;
            UUID accepted = coordinator.lastAccepted(owner, kind()).map(AnalysisParameterConfiguration::acceptedRunId).orElse(null);
            if (!Objects.equals(accepted, appliedRun)) { restore(); refresh.run(); }
        };
    }
    void planChanged() {
        owner = controller.getCurrentPlan(); edit++;
        var plan = owner; var p = plan.getPlanningAssumptions(); var longevity = controller.getLongevitySessionSettings();
        var baseline = plan.getBaseline();
        defaults = new Values(5000, "Deterministic", List.of(p.getExpectedAnnualInvestmentReturn().movePointRight(2).toPlainString(),
                "12.00", "417", p.getGeneralInflationRate().movePointRight(2).toPlainString(), "1.75", "-2.00",
                longevity.primaryAdjustment().factor().toPlainString(), longevity.spouseAdjustment().factor().toPlainString(),
                age(p.getDeathScenarioAssumptions().getSurvivorClaimingAge()), baseline == null ? "" : age(baseline.getSnapshot()
                .getPlanningAssumptions().getDeathScenarioAssumptions().getSurvivorClaimingAge())));
        restore();
    }
    private static String age(Integer age) { return age == null ? "" : age.toString(); }
    private Values read() {
        return new Values(count.getValue(), inflation.getValue(), fields.stream().map(field -> field == null ? "" : field.getText()).toList());
    }
    private void apply(Values values) {
        count.setValue(values.count()); inflation.setValue(values.inflation());
        for (int i = 0; i < fields.size(); i++) if (fields.get(i) != null) fields.get(i).setText(values.fields().get(i));
    }
    private void restore() {
        applying = true;
        try {
            apply(defaults);
            appliedRun = null;
            coordinator.lastAccepted(owner, kind()).ifPresent(configuration -> {
                appliedRun = configuration.acceptedRunId();
                var p = (AnalysisParameterConfiguration.MonteCarlo) configuration.parameters();
                count.setValue(p.simulations()); fields.get(0).setText(percent(p.expectedReturnRate()));
                fields.get(1).setText(percent(p.returnVolatilityRate())); fields.get(2).setText(Long.toString(p.seed()));
                inflation.setValue("STOCHASTIC".equals(p.inflation().mode()) ? "Stochastic" : "Deterministic");
                if ("STOCHASTIC".equals(p.inflation().mode())) {
                    fields.get(3).setText(percent(p.inflation().meanRate())); fields.get(4).setText(percent(p.inflation().volatilityRate()));
                    fields.get(5).setText(percent(p.inflation().floorRate()));
                }
                if (p.primaryMortalityFactor() != null) fields.get(6).setText(p.primaryMortalityFactor().toPlainString());
                if (p.spouseMortalityFactor() != null) fields.get(7).setText(p.spouseMortalityFactor().toPlainString());
                if (p.currentPlanSurvivorClaimingAge() != null) fields.get(8).setText(p.currentPlanSurvivorClaimingAge().toString());
                if (p.baselineSurvivorClaimingAge() != null && fields.get(9) != null) fields.get(9).setText(p.baselineSurvivorClaimingAge().toString());
            });
        } finally { applying = false; }
        if (mode.getValue() == MonteCarloMode.LONGEVITY_ADJUSTED) share.run();
        updateStatus();
    }
    private static String percent(BigDecimal value) { return value.scaleByPowerOfTen(2).toString(); }
    Run begin(AnalysisParameterConfiguration.MonteCarlo parameters) {
        return begin(parameters, null);
    }
    Run begin(AnalysisParameterConfiguration.MonteCarlo parameters, String fingerprint) {
        return new Run(coordinator.bind(owner, controller.getCurrentFile()), kind(), coordinator.nextAcceptanceSequence(), edit, parameters,
                controller.prepareAnalyzerAssociation(kind(), fingerprint));
    }
    void accept(Run run, AcceptedAnalysisCompletion completion) {
        if (closed || owner != controller.getCurrentPlan() || run.binding().plan() != owner || run.kind() != kind()
                || completion.kind() != run.kind() || run.binding().planId() != null && !run.binding().planId().equals(completion.planId())) return;
        var configuration = new AnalysisParameterConfiguration(1, run.kind(), run.kind().shape(), completion.runId(), completion.completedAt(),
                run.parameters(), Map.of("parameterContract", "1"));
        coordinator.accept(new AcceptedAnalysisParameters(run.binding(), run.sequence(), configuration));
        controller.associateAnalyzer(run.edit() == edit ? run.association() : null, completion);
        updateStatus();
    }
    void rollback(Run run) {
        if (closed || run.edit() != edit || owner != controller.getCurrentPlan() || run.binding().plan() != owner
                || run.kind() != kind() || !run.binding().equals(coordinator.bind(owner, controller.getCurrentFile()))) return;
        restore(); refresh.run();
    }
    void updateStatus() {
        var state = coordinator.state(owner, kind()).orElse(null);
        status.setText(state == null ? coordinator.lastAccepted(owner, kind()).isPresent()
                ? owner.getPlanId() != null && controller.getCurrentFile() != null
                    ? "Parameters — SAVED: Last-successful settings restored from retirement plan."
                    : "Parameters — MEMORY ONLY: Save this retirement plan to enable automatic parameter writes."
                : "No accepted parameters saved for this analysis mode."
                : "Parameters — " + state.status().name().replace('_', ' ') + ": " + state.message());
        var association = controller.getAnalyzerAssociationNotice(kind());
        if (!association.isEmpty()) status.setText(status.getText() + "\n" + association);
        boolean failed = state != null && state.status() == AnalysisParameterCoordinator.Status.SAVE_FAILED;
        retry.setVisible(failed); retry.setManaged(failed);
    }
    @Override public void close() { closed = true; edit++; detach.run(); }
}
