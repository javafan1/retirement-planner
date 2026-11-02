package com.daviddunn.retirementplanner.ui.controls;

import com.daviddunn.retirementplanner.domain.roth.EmployerRothConversionAvailability;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.beans.binding.Bindings;
import java.util.ArrayList;
import java.util.List;
import java.time.Year;

/** Detached account-editor inputs; never mutates an account. */
public final class EmployerRothAvailabilityEditor extends VBox {
    private final ComboBox<EmployerRothConversionAvailability.Status> status = new ComboBox<>();
    private final TextField year = new TextField();
    private final CheckBox replaceLegacy = new CheckBox("Set rollover/conversion availability explicitly");
    private final boolean legacy;
    private final Label error = new Label();
    private final Label yearLabel = new Label("First eligible calendar year (optional)");
    private final Label statusLabel = new Label("Availability status:");
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();
    private Runnable preferredHeightChanged = () -> { };

    public EmployerRothAvailabilityEditor(EmployerRothConversionAvailability original, boolean legacy) {
        super(6);
        this.legacy = legacy;
        status.getItems().setAll(EmployerRothConversionAvailability.Status.values());
        status.setId("employer-roth-availability");
        year.setId("employer-roth-first-year");
        error.setId("employer-roth-year-error");
        status.setPromptText("Select availability");
        status.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(EmployerRothConversionAvailability.Status value) {
                return value == null ? "" : value == EmployerRothConversionAvailability.Status.AVAILABLE
                        ? "Available for rollover or conversion" : "Not yet available";
            }
            @Override public EmployerRothConversionAvailability.Status fromString(String value) { throw new UnsupportedOperationException(); }
        });
        status.setMaxWidth(Double.MAX_VALUE);
        year.setPromptText("YYYY (optional)");
        if (original != null) {
            status.setValue(original.status());
            year.setText(original.firstEligibleYear() == null ? "" : original.firstEligibleYear().toString());
        }
        var heading = new Label("Employer Roth conversion availability");
        var help = new Label(legacy
                ? "Unknown / Legacy: existing modeling assumes availability. This is not legal verification."
                : "Choose a planning assumption under the employer plan's rules; this is not legal verification.");
        help.setWrapText(true); help.setMaxWidth(380);
        replaceLegacy.setVisible(legacy); replaceLegacy.setManaged(legacy);
        InputHelp.install(status, "Availability is your planning assumption. It is not inferred from age, retirement or separation. Not yet available with no year means unavailable throughout the modeled horizon.");
        InputHelp.install(replaceLegacy, "Choose this only when you want to replace the saved Unknown / Legacy modeling assumption. Leaving it unchecked preserves the existing account without adding availability data.");
        InputHelp.install(year, "Enter a four-digit calendar year. Availability begins January 1 of that year. Leave blank to keep the account unavailable throughout the modeled horizon.");
        status.setAccessibleText("Employer rollover or conversion availability");
        year.setAccessibleText("First eligible calendar year");
        year.setAccessibleHelp("Optional four-digit year from 0001 through 9999. Eligibility begins January 1. A blank value means unavailable throughout the modeled horizon.");
        error.setWrapText(true);
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        yearLabel.setLabelFor(year);
        statusLabel.setLabelFor(status);
        getChildren().addAll(heading, help, replaceLegacy, statusLabel, status, yearLabel, year, error);
        var statusRequired = Bindings.createBooleanBinding(
                () -> isVisible() && isManaged() && (!legacy || replaceLegacy.isSelected()),
                visibleProperty(), managedProperty(), replaceLegacy.selectedProperty());
        requiredFieldAttachments.add(RequiredFieldSupport.attach(statusLabel, status, statusRequired));
        status.valueProperty().addListener((o, a, b) -> update());
        replaceLegacy.selectedProperty().addListener((o, a, b) -> update());
        update();
    }

    private void update() {
        status.setDisable(legacy && !replaceLegacy.isSelected());
        boolean needsYear = status.getValue() == EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE;
        yearLabel.setVisible(needsYear); yearLabel.setManaged(needsYear);
        year.setVisible(needsYear); year.setManaged(needsYear); year.setDisable(status.isDisabled());
        preferredHeightChanged.run();
    }

    public void setOnPreferredHeightChanged(Runnable callback) {
        preferredHeightChanged = callback == null ? () -> { } : callback;
    }

    public EmployerRothConversionAvailability value() {
        if (legacy && !replaceLegacy.isSelected()) return null;
        if (status.getValue() == null) throw new IllegalArgumentException("Select employer rollover/conversion availability.");
        Integer first = null;
        if (status.getValue() == EmployerRothConversionAvailability.Status.NOT_YET_AVAILABLE && !year.getText().isBlank()) {
            String entered = year.getText().trim();
            if (!entered.matches("\\d{4}")) {
                throw new IllegalArgumentException("First eligible year must contain four digits (YYYY), or be blank.");
            }
            try {
                first = Integer.valueOf(entered);
                if (first < 1 || first > 9999) throw new IllegalArgumentException();
                Year.of(first);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("First eligible year must be between 0001 and 9999.");
            }
        }
        return new EmployerRothConversionAvailability(status.getValue(), first);
    }

    public boolean validateInput() {
        try { value(); error.setText(""); return true; }
        catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
            if (status.getValue() == null) status.requestFocus(); else year.requestFocus();
            return false;
        }
    }

    /** Releases indicator listeners when the containing account dialog closes. */
    public void close() {
        requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
        requiredFieldAttachments.clear();
    }
}
