package com.daviddunn.retirementplanner.ui.controls;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothRmdSchedulingPolicy;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class PendingRothSchedulingView extends GridPane {
    private final Label pending = new Label();
    private final CheckBox versioned = new CheckBox("Use start and end year scheduling");
    private final TextField endYear = new TextField();
    private final ComboBox<String> policy = new ComboBox<>();

    public PendingRothSchedulingView() {
        setId("pending-roth-scheduling");
        setHgap(8);
        setVgap(4);
        pending.setWrapText(true);
        endYear.setEditable(false);

        InputHelp.install(endYear, "Inclusive final conversion year. A one-time conversion uses its start year.");
        endYear.setEditable(true);
        endYear.disableProperty().bind(policy.disableProperty());
        policy.getItems().setAll(Arrays.stream(RothRmdSchedulingPolicy.values()).map(RothRmdSchedulingPolicy::label).toList());
        InputHelp.install(policy, Arrays.stream(RothRmdSchedulingPolicy.values())
                .map(value -> value.label() + ". " + value.description()).collect(Collectors.joining("\n")));
        add(pending, 0, 0, 2, 1);
        InputHelp.install(versioned, "Apply to adopt the versioned schedule. Cancel restores the saved scheduling choice.");
        add(versioned, 0, 1, 2, 1);
        addRow(2, new Label("End Year"), endYear);
        addRow(3, new Label("When RMDs Begin"), policy);
        policy.disableProperty().bind(versioned.disableProperty().or(versioned.selectedProperty().not()));
        InputHelp.linkGridLabels(this);
    }

    public void load(RetirementPlan plan) {
        var saved = plan == null ? null : plan.getRothConversionRequest();
        var draft = saved == null && plan != null ? plan.getNewPlanRothSchedulingDraft() : saved;
        var scheduling = draft == null ? null : draft.getScheduling();
        refreshAdmission(plan);
        versioned.setSelected(scheduling != null && (!versioned.isDisabled() || saved != null));
        endYear.setText(scheduling == null ? (saved == null ? "" : Integer.toString(saved.getStartYear()))
                : Integer.toString(scheduling.conversionEndYear()));
        policy.setValue(scheduling == null ? RothRmdSchedulingPolicy.CONTINUE_THROUGH_END_YEAR.label()
                : scheduling.rmdSchedulingPolicy().label());
    }

    public void refreshAdmission(RetirementPlan plan) {
        versioned.setDisable(!ProjectionEngine.supportsVersionedRothScheduling(plan));
        pending.setText(versioned.isDisabled() ? "Versioned scheduling requires a supported Florida Basic normal projection." : "");
        pending.setManaged(versioned.isDisabled());
    }

    public boolean selected() { return versioned.isSelected(); }
    public java.util.List<Object> draftFields() {
        return java.util.Arrays.asList(selected(), endYear.getText(), policy.getValue());
    }

    public void onChange(Runnable listener) {
        versioned.selectedProperty().addListener((o, a, b) -> listener.run());
        endYear.textProperty().addListener((o, a, b) -> listener.run());
        policy.valueProperty().addListener((o, a, b) -> listener.run());
    }

    public RothConversionRequest.Scheduling read() {
        if (!selected()) return null;
        if (versioned.isDisabled()) throw new RothConversionRequest.SchedulingUnavailable();
        int end = Integer.parseInt(endYear.getText().trim());
        var selectedPolicy = Arrays.stream(RothRmdSchedulingPolicy.values())
                .filter(value -> value.label().equals(policy.getValue())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an RMD scheduling policy."));
        return new RothConversionRequest.Scheduling(1, end, selectedPolicy);
    }
}
