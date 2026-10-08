package com.daviddunn.retirementplanner.ui.wizard;

import javafx.scene.Node;

/** An implemented page validates its controls before writing to the isolated draft. */
public interface NewPlanWizardStep {

    String title();

    Node content();

    /** Reports actionable feedback in the page and returns false when navigation is blocked. */
    boolean validateAndApply();
}
