package com.daviddunn.retirementplanner.ui.controls;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.text.Text;

import java.util.Objects;

/**
 * Adds a visual, accessible required marker to an existing label/control pair.
 * This class renders caller-owned requiredness; it does not validate or infer it.
 */
public final class RequiredFieldSupport {
    private static final Object ATTACHMENT_KEY = new Object();

    private RequiredFieldSupport() { }

    /** Attaches an always-required marker. The returned attachment should be closed with its form. */
    public static Attachment attach(Label label, Control control) {
        Objects.requireNonNull(label, "label");
        Object current = label.getProperties().get(ATTACHMENT_KEY);
        if (current instanceof Attachment attachment) {
            if (attachment.control == control && attachment.constantRequired) return attachment;
            throw new IllegalStateException("A required-field attachment already exists for this label.");
        }
        return attachInternal(label, control, new SimpleBooleanProperty(true), true);
    }

    /**
     * Attaches a marker driven by the form's authoritative required-state property.
     * The observable is read-only from this helper's perspective.
     */
    public static Attachment attach(Label label, Control control, ObservableBooleanValue required) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(required, "required");
        Object current = label.getProperties().get(ATTACHMENT_KEY);
        if (current instanceof Attachment attachment) {
            if (attachment.control == control && attachment.required == required) return attachment;
            throw new IllegalStateException("A required-field attachment already exists for this label.");
        }
        return attachInternal(label, control, required, false);
    }

    /** Creates a compact standalone legend. It is non-interactive and theme styled. */
    public static Label createLegend() {
        Label legend = new Label("* Required field");
        legend.getStyleClass().add("required-field-legend");
        legend.setAccessibleText("Required field");
        legend.setFocusTraversable(false);
        return legend;
    }

    private static Attachment attachInternal(Label label, Control control,
                                             ObservableBooleanValue required, boolean constant) {
        Objects.requireNonNull(control, "control");
        if (label.getGraphic() != null) {
            throw new IllegalArgumentException("Required-field labels with an existing graphic are not supported; compose the label and marker explicitly.");
        }
        Node associated = label.getLabelFor();
        if (associated != null && associated != control) {
            throw new IllegalArgumentException("The label is already associated with a different control.");
        }
        Attachment attachment = new Attachment(label, control, required, constant, associated,
                label.getGraphic(), label.getContentDisplay(), label.getGraphicTextGap(), control.getAccessibleHelp());
        label.getProperties().put(ATTACHMENT_KEY, attachment);
        if (associated == null) label.setLabelFor(control);
        label.setContentDisplay(ContentDisplay.RIGHT);
        label.setGraphicTextGap(Math.max(3, attachment.originalGraphicTextGap));
        label.setGraphic(attachment.indicator);
        attachment.refresh();
        required.addListener(attachment.requiredListener);
        label.visibleProperty().addListener(attachment.labelVisibleListener);
        control.visibleProperty().addListener(attachment.controlVisibleListener);
        label.managedProperty().addListener(attachment.labelManagedListener);
        control.managedProperty().addListener(attachment.controlManagedListener);
        control.accessibleHelpProperty().addListener(attachment.accessibleHelpListener);
        return attachment;
    }

    /** Explicit listener and presentation lifecycle for one label/control pair. */
    public static final class Attachment implements AutoCloseable {
        private final Label label;
        private final Control control;
        private final ObservableBooleanValue required;
        private final boolean constantRequired;
        private final Node originalLabelFor;
        private final Node originalGraphic;
        private final ContentDisplay originalContentDisplay;
        private final double originalGraphicTextGap;
        private String baseAccessibleHelp;
        private String lastAppliedHelp;
        private boolean writingAccessibleHelp;
        private boolean closed;
        private final Text indicator = new Text("*");

        private final ChangeListener<Boolean> requiredListener = (observable, oldValue, newValue) -> refresh();
        private final ChangeListener<Boolean> labelVisibleListener = (observable, oldValue, newValue) -> refresh();
        private final ChangeListener<Boolean> controlVisibleListener = (observable, oldValue, newValue) -> refresh();
        private final ChangeListener<Boolean> labelManagedListener = (observable, oldValue, newValue) -> refresh();
        private final ChangeListener<Boolean> controlManagedListener = (observable, oldValue, newValue) -> refresh();
        private final ChangeListener<String> accessibleHelpListener = (observable, oldValue, newValue) -> {
            if (writingAccessibleHelp || closed) return;
            baseAccessibleHelp = stripRequiredSuffix(newValue);
            applyAccessibleHelp();
        };

        private Attachment(Label label, Control control, ObservableBooleanValue required,
                           boolean constantRequired, Node originalLabelFor, Node originalGraphic,
                           ContentDisplay originalContentDisplay, double originalGraphicTextGap,
                           String originalAccessibleHelp) {
            this.label = label;
            this.control = control;
            this.required = required;
            this.constantRequired = constantRequired;
            this.originalLabelFor = originalLabelFor;
            this.originalGraphic = originalGraphic;
            this.originalContentDisplay = originalContentDisplay;
            this.originalGraphicTextGap = originalGraphicTextGap;
            this.baseAccessibleHelp = originalAccessibleHelp;
            indicator.getStyleClass().add("required-field-indicator");
            indicator.setFocusTraversable(false);
        }

        public boolean isRequired() { return required.get(); }
        public Text indicatorNode() { return indicator; }

        private void refresh() {
            if (closed) return;
            boolean show = required.get() && label.isVisible() && control.isVisible()
                    && label.isManaged() && control.isManaged();
            indicator.setVisible(show);
            indicator.setManaged(show);
            applyAccessibleHelp();
        }

        private void applyAccessibleHelp() {
            if (closed) return;
            String value = baseAccessibleHelp;
            if (required.get() && !alreadySaysRequired(value)) value = value == null || value.isBlank()
                    ? "Required." : value.stripTrailing() + " Required.";
            writingAccessibleHelp = true;
            try {
                control.setAccessibleHelp(value);
                lastAppliedHelp = value;
            } finally {
                writingAccessibleHelp = false;
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            required.removeListener(requiredListener);
            label.visibleProperty().removeListener(labelVisibleListener);
            control.visibleProperty().removeListener(controlVisibleListener);
            label.managedProperty().removeListener(labelManagedListener);
            control.managedProperty().removeListener(controlManagedListener);
            control.accessibleHelpProperty().removeListener(accessibleHelpListener);
            if (label.getProperties().get(ATTACHMENT_KEY) == this) label.getProperties().remove(ATTACHMENT_KEY);
            if (label.getGraphic() == indicator) label.setGraphic(originalGraphic);
            label.setContentDisplay(originalContentDisplay);
            label.setGraphicTextGap(originalGraphicTextGap);
            if (label.getLabelFor() == control && originalLabelFor == null) label.setLabelFor(null);
            if (Objects.equals(control.getAccessibleHelp(), lastAppliedHelp)) control.setAccessibleHelp(baseAccessibleHelp);
        }

        private static String stripRequiredSuffix(String value) {
            if (value == null) return null;
            String trimmed = value.stripTrailing();
            if (trimmed.equals("Required.")) return "";
            if (trimmed.endsWith(". Required.")) return trimmed.substring(0, trimmed.length() - " Required.".length());
            return value;
        }

        private static boolean alreadySaysRequired(String value) {
            if (value == null) return false;
            String normalized = value.stripTrailing().toLowerCase(java.util.Locale.ROOT);
            if (normalized.endsWith("not required") || normalized.endsWith("not required.")) return false;
            return normalized.equals("required") || normalized.endsWith(" required.")
                    || normalized.endsWith(" required") || normalized.endsWith(" required field.");
        }
    }
}
