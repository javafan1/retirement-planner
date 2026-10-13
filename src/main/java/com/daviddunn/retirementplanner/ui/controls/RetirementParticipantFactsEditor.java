package com.daviddunn.retirementplanner.ui.controls;

import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts.SeparationStatus;
import com.daviddunn.retirementplanner.domain.model.RetirementParticipantFacts.RetirementConditionsStatus;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

/** Optional arrangement-level facts; never asks the user to decide state-tax qualification. */
public final class RetirementParticipantFactsEditor extends TitledPane {
    private final ComboBox<SeparationStatus> separation = new ComboBox<>();
    private final DatePicker separationDate = new DatePicker();
    private final ComboBox<RetirementConditionsStatus> conditions = new ComboBox<>();
    private final DatePicker conditionsDate = new DatePicker();

    public RetirementParticipantFactsEditor(RetirementParticipantFacts facts) {
        setText("Participant facts (optional)");
        setExpanded(false);
        setId("retirement-participant-facts");
        separation.setId("participant-separation");
        separationDate.setId("participant-separation-date");
        conditions.setId("participant-conditions");
        conditionsDate.setId("participant-conditions-date");
        separation.getItems().setAll(SeparationStatus.values());
        conditions.getItems().setAll(RetirementConditionsStatus.values());
        separation.setConverter(labels("Unknown", "Not separated", "Separated"));
        conditions.setConverter(labels("Unknown", "Not met", "Met"));
        separation.setValue(facts.separationStatus());
        separationDate.setValue(facts.separationDate());
        conditions.setValue(facts.retirementConditionsStatus());
        conditionsDate.setValue(facts.retirementConditionsMetDate());
        separationDate.setDisable(separation.getValue() != SeparationStatus.SEPARATED);
        conditionsDate.setDisable(conditions.getValue() != RetirementConditionsStatus.MET);
        separation.valueProperty().addListener((observable, oldValue, value) -> {
            separationDate.setDisable(value != SeparationStatus.SEPARATED);
            if (separationDate.isDisabled()) separationDate.setValue(null);
        });
        conditions.valueProperty().addListener((observable, oldValue, value) -> {
            conditionsDate.setDisable(value != RetirementConditionsStatus.MET);
            if (conditionsDate.isDisabled()) conditionsDate.setValue(null);
        });
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new javafx.geometry.Insets(10));
        grid.addRow(0, new Label("Separated from employer:"), separation);
        grid.addRow(1, new Label("Separation date (optional):"), separationDate);
        grid.addRow(2, new Label("Plan retirement conditions:"), conditions);
        grid.addRow(3, new Label("Conditions met date (optional):"), conditionsDate);
        InputHelp.install(separation, "Whether this participant has separated from the employer sponsoring this arrangement. Unknown is allowed. This fact alone does not establish state-tax exemption.");
        InputHelp.install(separationDate, "Actual or planned separation date. A future event does not apply to earlier projection dates. Without a date, affirmative timing remains unknown.");
        InputHelp.install(conditions, "Whether this participant has met the arrangement's retirement conditions. Do not infer this from age, Social Security, pension commencement or RMDs. Unknown is allowed.");
        InputHelp.install(conditionsDate, "Actual or planned date retirement conditions were met. Without a date, affirmative timing remains unknown. This is not a tax qualification election.");
        InputHelp.linkGridLabels(grid);
        setContent(grid);
    }

    private static <E extends Enum<E>> StringConverter<E> labels(String... labels) {
        return new StringConverter<>() {
            @Override public String toString(E value) { return value == null ? "" : labels[value.ordinal()]; }
            @Override public E fromString(String value) { throw new UnsupportedOperationException("Select a listed status."); }
        };
    }

    public RetirementParticipantFacts value() {
        if (separation.getValue() == null || conditions.getValue() == null) {
            throw new IllegalArgumentException("Choose Unknown when participant facts are unavailable.");
        }
        return new RetirementParticipantFacts(separation.getValue(), separationDate.getValue(),
                conditions.getValue(), conditionsDate.getValue());
    }
}
