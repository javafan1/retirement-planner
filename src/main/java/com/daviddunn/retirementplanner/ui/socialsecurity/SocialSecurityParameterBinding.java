package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.analysis.parameters.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisKind;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.time.Instant;
import java.util.*;
import java.util.function.*;

/** Small UI binding; accepted values belong to the application coordinator, never this view. */
final class SocialSecurityParameterBinding implements AutoCloseable {
    record Run(AnalysisParameterCoordinator.Binding binding, long sequence, long edit, UUID id,
            AnalysisParameterConfiguration.Parameters parameters, Map<String, String> provenance) { }
    final VBox controls;
    final Label status = new Label();
    final Button retry = new Button("Retry Parameters Save");
    private final ApplicationController source;
    private final AnalysisKind kind;
    private final Consumer<AnalysisParameterConfiguration.Parameters> apply;
    private final Runnable defaults;
    private final Runnable refresh;
    private final BooleanSupplier visibleOwner;
    private final Runnable detach;
    private RetirementPlan owner;
    private long edit;
    private boolean closed;
    private boolean applying;
    private boolean unsupportedProfile;

    SocialSecurityParameterBinding(ApplicationController source, AnalysisKind kind,
            Consumer<AnalysisParameterConfiguration.Parameters> apply, Runnable defaults,
            Runnable refresh, BooleanSupplier visibleOwner) {
        this.source = source; this.kind = kind; this.apply = apply; this.defaults = defaults;
        this.refresh = refresh; this.visibleOwner = visibleOwner; owner = source.getCurrentPlan();
        status.setWrapText(true); status.accessibleTextProperty().bind(status.textProperty());
        status.setId(kind.name() + "-parameters-status");
        retry.setId(kind.name() + "-retry-parameters-save");
        retry.setAccessibleText("Retry saving accepted parameters without running analysis");
        retry.setOnAction(event -> { source.getAnalysisParameters().retry(owner, kind); update(); });
        controls = new VBox(3, status, retry);
        detach = source.getAnalysisParameters().addListener(Platform::runLater, () -> { if (!closed) update(); });
        update();
    }
    boolean applying() { return applying; }
    void edited() { if (!applying) edit++; }
    void restore() {
        edit++;
        applying = true;
        unsupportedProfile = false;
        try {
            var accepted = source.getAnalysisParameters().lastAccepted(owner, kind);
            if (accepted.isPresent()) apply.accept(accepted.orElseThrow().parameters()); else defaults.run();
        } catch (IllegalArgumentException unsupported) {
            unsupportedProfile = true;
            defaults.run();
        } finally { applying = false; }
        refresh.run(); update();
    }
    void planChanged() {
        owner = source.getCurrentPlan(); edit++;
        if ((kind.shape() == AnalysisKind.Shape.COUPLE) != owner.getHousehold().hasSpouse()) { update(); return; }
        if (visibleOwner.getAsBoolean()) restore();
        long expected = edit; var capturedOwner = owner;
        // Open notifies source observers before final file binding; do not overwrite later edits.
        Platform.runLater(() -> {
            if (!closed && visibleOwner.getAsBoolean() && owner == capturedOwner && owner == source.getCurrentPlan() && edit == expected) restore();
        });
    }
    Run begin(AnalysisParameterConfiguration.Parameters parameters) { return begin(parameters, Map.of()); }
    Run begin(AnalysisParameterConfiguration.Parameters parameters, Map<String, String> provenance) {
        return new Run(source.getAnalysisParameters().bind(owner, source.getCurrentFile()),
                source.getAnalysisParameters().nextAcceptanceSequence(), edit, UUID.randomUUID(), parameters, Map.copyOf(provenance));
    }
    void accept(Run run) {
        if (closed || owner != source.getCurrentPlan() || run.binding().plan() != owner) return;
        var configuration = new AnalysisParameterConfiguration(1, kind, kind.shape(), run.id(), Instant.now(), run.parameters(), run.provenance());
        source.getAnalysisParameters().accept(new AcceptedAnalysisParameters(run.binding(), run.sequence(), configuration));
        update();
    }
    void rollback(Run run) {
        if (closed || !visibleOwner.getAsBoolean() || edit != run.edit() || owner != source.getCurrentPlan()
                || owner != run.binding().plan() || !run.binding().equals(source.getAnalysisParameters().bind(owner, source.getCurrentFile()))) return;
        restore();
    }
    private void update() {
        var state = source.getAnalysisParameters().state(owner, kind).orElse(null);
        boolean accepted = source.getAnalysisParameters().lastAccepted(owner, kind).isPresent();
        status.setText(state != null ? "Parameters — " + state.status().name().replace('_', ' ') + ": " + state.message()
                : accepted ? owner.getPlanId() != null && source.getCurrentFile() != null
                ? "Parameters — SAVED: Last-successful settings restored from retirement plan."
                : "Parameters — MEMORY ONLY: Save the plan explicitly to persist accepted settings."
                : "No accepted parameters saved for this analysis.");
        if (unsupportedProfile) status.setText("Saved parameter profile is unavailable in this version. Default next-run settings are shown; the saved configuration was preserved.");
        boolean failed = state != null && state.status() == AnalysisParameterCoordinator.Status.SAVE_FAILED;
        retry.setVisible(failed); retry.setManaged(failed);
    }
    @Override public void close() { closed = true; edit++; detach.run(); }
}
